package info.avicia.avoutils.features.rewards;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.api.ApiException;
import info.avicia.avoutils.features.rewards.api.RewardsClient;
import info.avicia.avoutils.features.rewards.api.RewardsResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/**
 * Records hand-outs in the bot: takes the hand-out lock before a run, claims units as they're confirmed or recorded
 * by hand, and unlocks after the run. Per player and reward, one claim is in flight at a time and units that come
 * meanwhile go into the next one. Failed claims are retried with the same claim id, with backoff, for as long as the
 * game runs, also after a disconnect. Used on the client thread; HTTP replies come back through {@code callbacks}.
 */
public final class ClaimRecorder {
    static final int FIRST_RETRY_TICKS = 20;
    static final int MAX_RETRY_TICKS = 600;

    public interface Listener {
        /** A claim was recorded; {@code pending} is what the player is still owed of {@code kind}. */
        void onRecorded(String uuid, RewardKind kind, long pending);

        /** More was handed out than the bot had pending: what's pending is claimed anew, {@code excess} isn't. */
        void onExcess(String uuid, RewardKind kind, String name, long excess);

        /** A claim the bot refused for good, e.g. after losing management rights. */
        void onClaimFailed(String uuid, RewardKind kind, String name, long amount, String error);
    }

    private record Key(String uuid, RewardKind kind) {}

    private record Claim(String id, long amount) {}

    private static final class Account {
        final String name;
        long unsent;
        Claim inFlight;
        boolean sending;
        long retryAt = -1;
        int retryDelay = FIRST_RETRY_TICKS;
        boolean locked;
        boolean runActive;

        Account(String name) {
            this.name = name;
        }

        boolean idle() {
            return !locked && !runActive && !sending && inFlight == null && unsent == 0;
        }
    }

    private final RewardsClient client;
    private final Executor callbacks;
    private final Listener listener;
    private final Map<Key, Account> accounts = new LinkedHashMap<>();
    private long now;

    public ClaimRecorder(RewardsClient client, Executor callbacks, Listener listener) {
        this.client = client;
        this.callbacks = callbacks;
        this.listener = listener;
    }

    /**
     * Takes the lock before a run. {@code done} gets the bot's reply (a 409 has {@code lockedBy}) or the error. A run
     * may start only if the reply is ok with something pending; {@link #runFinished} must follow it.
     */
    public void lock(String uuid, RewardKind kind, String name, BiConsumer<RewardsResponse, Throwable> done) {
        client.lock(uuid, kind).whenComplete((response, error) -> callbacks.execute(() -> {
            if (error == null && response.ok && response.pending != null && response.pending > 0) {
                Account account = account(new Key(uuid, kind), name);
                account.locked = true;
                account.runActive = true;
            }
            done.accept(response, unwrap(error));
        }));
    }

    /** Claims {@code amount} handed out, confirmed by a run or recorded by hand. */
    public void add(String uuid, RewardKind kind, String name, long amount) {
        Account account = account(new Key(uuid, kind), name);
        account.unsent += amount;
        sendNext(new Key(uuid, kind), account);
    }

    /** The run is over: unlock once its last claim is recorded. */
    public void runFinished(String uuid, RewardKind kind) {
        Key key = new Key(uuid, kind);
        Account account = accounts.get(key);
        if (account != null) {
            account.runActive = false;
            sendNext(key, account);
        }
    }

    public void tick() {
        now++;
        for (Map.Entry<Key, Account> entry : new ArrayList<>(accounts.entrySet())) {
            Account account = entry.getValue();
            if (account.retryAt >= 0 && now >= account.retryAt) {
                account.retryAt = -1;
                sendNext(entry.getKey(), account);
            }
        }
    }

    /** Claims waiting to be retried, for the status line; empty while the bot answers. */
    public List<String> backlog() {
        List<String> lines = new ArrayList<>();
        accounts.forEach((key, account) -> {
            long amount = account.unsent + (account.inFlight != null ? account.inFlight.amount() : 0);
            if (account.retryAt >= 0 && amount > 0) {
                lines.add("Not recorded yet, retrying: " + key.kind().label(amount) + " for " + account.name);
            }
        });
        return lines;
    }

    private Account account(Key key, String name) {
        return accounts.computeIfAbsent(key, k -> new Account(name));
    }

    private void sendNext(Key key, Account account) {
        if (account.sending || account.retryAt >= 0) return;
        if (account.inFlight == null && account.unsent > 0) {
            account.inFlight = new Claim(UUID.randomUUID().toString(), account.unsent);
            account.unsent = 0;
        }
        if (account.inFlight != null) {
            send(key, account, account.inFlight);
            return;
        }
        if (account.locked && !account.runActive) {
            account.locked = false;
            client.unlock(key.uuid(), key.kind()).whenComplete((response, error) -> {
                if (error != null) {
                    AvoUtilsMod.LOGGER.debug("[AvoUtils] [Rewards] Unlock failed; the lock expires anyway", error);
                }
            });
        }
        if (account.idle()) {
            accounts.remove(key);
        }
    }

    private void send(Key key, Account account, Claim claim) {
        account.sending = true;
        client.claim(key.uuid(), key.kind(), claim.amount(), claim.id())
                .whenComplete((response, error) -> callbacks.execute(
                        () -> onClaimReply(key, account, claim, response, unwrap(error))));
    }

    private void onClaimReply(Key key, Account account, Claim claim, RewardsResponse response, Throwable error) {
        account.sending = false;
        if (error != null) {
            if (isRetryable(error)) {
                account.retryAt = now + account.retryDelay;
                account.retryDelay = Math.min(MAX_RETRY_TICKS, account.retryDelay * 2);
                AvoUtilsMod.LOGGER.warn("[AvoUtils] [Rewards] Claim of {} for {} failed, retrying: {}",
                        key.kind().label(claim.amount()), account.name, error.getMessage());
                return;
            }
            account.inFlight = null;
            listener.onClaimFailed(key.uuid(), key.kind(), account.name, claim.amount(), error.getMessage());
        } else if (response.ok) {
            account.inFlight = null;
            listener.onRecorded(key.uuid(), key.kind(), response.pending != null ? response.pending : 0);
        } else {
            long pending = response.pending != null ? response.pending : 0;
            account.inFlight = null;
            if (pending >= claim.amount()) {
                // Only a bot without partial claims refuses a claim that isn't more than is pending
                listener.onClaimFailed(key.uuid(), key.kind(), account.name, claim.amount(),
                        "the bot needs an update to record part of what's pending");
            } else {
                // More was handed out than is pending: claim what's pending, as a new claim, and report the rest
                long handedOut = claim.amount() + account.unsent;
                account.unsent = 0;
                if (pending > 0) {
                    account.inFlight = new Claim(UUID.randomUUID().toString(), pending);
                }
                listener.onExcess(key.uuid(), key.kind(), account.name, handedOut - pending);
            }
        }
        account.retryDelay = FIRST_RETRY_TICKS;
        sendNext(key, account);
    }

    /** Connection problems, timeouts and server errors are retried; other refusals won't change on a retry. */
    private static boolean isRetryable(Throwable error) {
        return !(error instanceof ApiException api) || api.status() >= 500 || api.status() == 401;
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
