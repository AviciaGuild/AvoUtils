package info.avicia.avoutils.features.rewards;

import info.avicia.avoutils.core.api.ApiException;
import info.avicia.avoutils.features.rewards.api.RewardsClient;
import info.avicia.avoutils.features.rewards.api.RewardsResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimRecorderTest {

    private record Claim(String uuid, RewardKind kind, long amount, String id,
                         CompletableFuture<RewardsResponse> reply) {}

    private final RewardsClient client = mock(RewardsClient.class);
    private final List<Claim> claims = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final ClaimRecorder recorder = new ClaimRecorder(client, Runnable::run, new ClaimRecorder.Listener() {
        @Override
        public void onRecorded(String uuid, RewardKind kind, long pending) {
            events.add("recorded " + uuid + " " + kind + " pending " + pending);
        }

        @Override
        public void onExcess(String uuid, RewardKind kind, String name, long excess) {
            events.add("excess " + name + " " + kind + " " + excess);
        }

        @Override
        public void onClaimFailed(String uuid, RewardKind kind, String name, long amount, String error) {
            events.add("failed " + name + " " + kind + " " + amount + ": " + error);
        }
    });

    ClaimRecorderTest() {
        when(client.claim(anyString(), any(), anyLong(), anyString())).thenAnswer(call -> {
            CompletableFuture<RewardsResponse> reply = new CompletableFuture<>();
            claims.add(new Claim(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3),
                    reply));
            return reply;
        });
        when(client.unlock(anyString(), any())).thenReturn(CompletableFuture.completedFuture(response(true, null)));
    }

    private static RewardsResponse response(boolean ok, Long pending) {
        RewardsResponse response = new RewardsResponse();
        response.ok = ok;
        response.pending = pending;
        return response;
    }

    private void lockWith(RewardsResponse reply) {
        when(client.lock("steve", RewardKind.TOME)).thenReturn(CompletableFuture.completedFuture(reply));
        recorder.lock("steve", RewardKind.TOME, "Steve", (response, error) -> events.add("lock replied"));
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) {
            recorder.tick();
        }
    }

    @Test
    void sendsOneClaimAtATimeAndPutsUnitsConfirmedMeanwhileInTheNext() {
        recorder.add("steve", RewardKind.TOME, "Steve", 1);
        recorder.add("steve", RewardKind.TOME, "Steve", 1);
        recorder.add("steve", RewardKind.TOME, "Steve", 1);
        assertEquals(1, claims.size());
        assertEquals(1, claims.get(0).amount());

        claims.get(0).reply().complete(response(true, 2L));

        assertEquals(2, claims.size());
        assertEquals(2, claims.get(1).amount());
        assertNotEquals(claims.get(0).id(), claims.get(1).id());
        assertEquals(List.of("recorded steve TOME pending 2"), events);
    }

    @Test
    void retriesWithTheSameClaimIdAndBacksOff() {
        recorder.add("steve", RewardKind.ASPECT, "Steve", 2);
        claims.get(0).reply().completeExceptionally(new IOException("timed out"));

        ticks(ClaimRecorder.FIRST_RETRY_TICKS - 1);
        assertEquals(1, claims.size());
        assertEquals(List.of("Not recorded yet, retrying: 2 Aspects for Steve"), recorder.backlog());
        ticks(1);
        assertEquals(2, claims.size());
        assertEquals(claims.get(0).id(), claims.get(1).id());
        assertTrue(recorder.backlog().isEmpty());

        claims.get(1).reply().completeExceptionally(new ApiException(502, "Bad gateway"));
        ticks(2 * ClaimRecorder.FIRST_RETRY_TICKS - 1);
        assertEquals(2, claims.size());
        ticks(1);
        assertEquals(3, claims.size());
        assertEquals(claims.get(0).id(), claims.get(2).id());
    }

    @Test
    void theBackoffStopsGrowingAt30Seconds() {
        recorder.add("steve", RewardKind.LE, "Steve", 4);
        for (int attempt = 0; attempt < 8; attempt++) {
            claims.getLast().reply().completeExceptionally(new IOException("offline"));
            ticks(ClaimRecorder.MAX_RETRY_TICKS);
        }
        int sent = claims.size();
        claims.getLast().reply().completeExceptionally(new IOException("offline"));

        ticks(ClaimRecorder.MAX_RETRY_TICKS - 1);
        assertEquals(sent, claims.size());
        ticks(1);
        assertEquals(sent + 1, claims.size());
    }

    @Test
    void doesNotRetryARefusal() {
        recorder.add("steve", RewardKind.LE, "Steve", 3);
        claims.get(0).reply().completeExceptionally(new ApiException(403, "Only guild management"));

        ticks(ClaimRecorder.MAX_RETRY_TICKS);

        assertEquals(1, claims.size());
        assertEquals(List.of("failed Steve LE 3: Only guild management"), events);
    }

    @Test
    void aConflictRecordsWhatIsPendingAndReportsTheRest() {
        recorder.add("steve", RewardKind.LE, "Steve", 3);
        recorder.add("steve", RewardKind.LE, "Steve", 1);

        claims.get(0).reply().complete(response(false, 2L));

        assertEquals(2, claims.size());
        assertEquals(2, claims.get(1).amount());
        assertNotEquals(claims.get(0).id(), claims.get(1).id());
        claims.get(1).reply().complete(response(true, 0L));
        assertEquals(List.of("excess Steve LE 2", "recorded steve LE pending 0"), events);
    }

    @Test
    void aConflictWithNothingPendingRecordsNothing() {
        recorder.add("steve", RewardKind.LE, "Steve", 2);

        claims.get(0).reply().complete(response(false, 0L));

        assertEquals(1, claims.size());
        assertEquals(List.of("excess Steve LE 2"), events);
    }

    @Test
    void aBotWithoutPartialClaimsFailsTheClaimInsteadOfLooping() {
        recorder.add("steve", RewardKind.LE, "Steve", 11);

        claims.get(0).reply().complete(response(false, 16L)); // the old bot wants exactly what's pending

        assertEquals(1, claims.size());
        assertEquals(List.of("failed Steve LE 11: the bot needs an update to record part of what's pending"), events);
    }

    @Test
    void retriesAfterA401() {
        recorder.add("steve", RewardKind.TOME, "Steve", 1);
        claims.get(0).reply().completeExceptionally(new ApiException(401, "Unauthorized."));

        ticks(ClaimRecorder.FIRST_RETRY_TICKS);

        assertEquals(2, claims.size());
        assertEquals(claims.get(0).id(), claims.get(1).id());
    }

    @Test
    void waitsForARetriedLastClaimBeforeUnlocking() {
        lockWith(response(true, 2L));
        recorder.add("steve", RewardKind.TOME, "Steve", 1);
        claims.get(0).reply().completeExceptionally(new IOException("timed out"));

        recorder.runFinished("steve", RewardKind.TOME);
        verify(client, never()).unlock(anyString(), any());
        ticks(ClaimRecorder.FIRST_RETRY_TICKS);
        claims.get(1).reply().complete(response(true, 1L));

        verify(client).unlock("steve", RewardKind.TOME);
    }

    @Test
    void unlocksOnceTheRunIsOverAndItsLastClaimIsRecorded() {
        lockWith(response(true, 2L));
        recorder.add("steve", RewardKind.TOME, "Steve", 1);

        recorder.runFinished("steve", RewardKind.TOME);
        verify(client, never()).unlock(anyString(), any());
        claims.get(0).reply().complete(response(true, 1L));

        verify(client).unlock("steve", RewardKind.TOME);
    }

    @Test
    void unlocksAtOnceAfterARunThatRecordedNothing() {
        lockWith(response(true, 2L));

        recorder.runFinished("steve", RewardKind.TOME);

        verify(client).unlock("steve", RewardKind.TOME);
        assertTrue(claims.isEmpty());
    }

    @Test
    void holdsNoLockAfterARefusalOrWithNothingPending() {
        RewardsResponse refused = response(false, null);
        refused.lockedBy = "ChiefA";
        lockWith(refused);
        recorder.runFinished("steve", RewardKind.TOME);
        lockWith(response(true, 0L));
        recorder.runFinished("steve", RewardKind.TOME);

        verify(client, never()).unlock(anyString(), any());
        assertEquals(List.of("lock replied", "lock replied"), events);
    }

    @Test
    void passesALockErrorOn() {
        when(client.lock("steve", RewardKind.TOME))
                .thenReturn(CompletableFuture.failedFuture(new ApiException(404, "Not found")));
        List<Throwable> errors = new ArrayList<>();

        recorder.lock("steve", RewardKind.TOME, "Steve", (response, error) -> {
            assertNull(response);
            errors.add(error);
        });

        assertEquals(404, ((ApiException) errors.getFirst()).status());
    }

    @Test
    void recordsAHandOutByHandWithoutALock() {
        recorder.add("steve", RewardKind.LE, "Steve", 11);
        claims.get(0).reply().complete(response(true, 5L));

        assertEquals(List.of("recorded steve LE pending 5"), events);
        verify(client, never()).unlock(anyString(), any());
    }
}
