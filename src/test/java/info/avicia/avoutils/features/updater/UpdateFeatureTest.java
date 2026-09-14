package info.avicia.avoutils.features.updater;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateFeatureTest {

    private ModrinthUpdateChecker checker;
    private UpdateApplier applier;
    private UpdateFeature feature;

    @BeforeEach
    void setUp() {
        checker = mock(ModrinthUpdateChecker.class);
        applier = mock(UpdateApplier.class);
        feature = new UpdateFeature(checker, applier);
    }

    @Test
    void initialStateIsUnchecked() {
        assertEquals(UpdateFeature.UpdateState.UNCHECKED, feature.getState());
        assertNull(feature.getLastCheckResult());
        assertNull(feature.getErrorMessage());
    }

    @Test
    void checkForUpdateTransitionsToUpdateAvailableWhenUpdateFound() {
        UpdateCheckResult updateResult = new UpdateCheckResult(
                true, "1.0.0", "1.1.0",
                "https://example.com/mod.jar", "sha512",
                "avoutils-1.1.0.jar", 12345, "Changelog"
        );
        when(checker.checkForUpdate()).thenReturn(CompletableFuture.completedFuture(updateResult));

        UpdateCheckResult res = feature.checkForUpdate().join();

        assertEquals(updateResult, res);
        assertEquals(UpdateFeature.UpdateState.UPDATE_AVAILABLE, feature.getState());
        assertEquals(updateResult, feature.getLastCheckResult());
    }

    @Test
    void checkForUpdateTransitionsToUpToDateWhenNoUpdate() {
        UpdateCheckResult upToDateResult = UpdateCheckResult.upToDate("1.0.0");
        when(checker.checkForUpdate()).thenReturn(CompletableFuture.completedFuture(upToDateResult));

        UpdateCheckResult res = feature.checkForUpdate().join();

        assertEquals(upToDateResult, res);
        assertEquals(UpdateFeature.UpdateState.UP_TO_DATE, feature.getState());
    }

    @Test
    void checkForUpdateCachesResultUnlessForced() {
        UpdateCheckResult result1 = UpdateCheckResult.upToDate("1.0.0");
        when(checker.checkForUpdate()).thenReturn(CompletableFuture.completedFuture(result1));

        feature.checkForUpdate().join();
        feature.checkForUpdate().join();

        verify(checker, times(1)).checkForUpdate();

        // Forced recheck should call checker again
        feature.checkForUpdate(true).join();
        verify(checker, times(2)).checkForUpdate();
    }

    @Test
    void checkForUpdateHandlesFailureGracefully() {
        when(checker.checkForUpdate()).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("Modrinth is down"))
        );

        UpdateCheckResult res = feature.checkForUpdate().join();

        assertNull(res);
        assertEquals(UpdateFeature.UpdateState.ERROR, feature.getState());
        assertNotNull(feature.getErrorMessage());
        assertTrue(feature.getErrorMessage().contains("Modrinth is down"));
    }

    @Test
    void downloadFailsIfNoUpdateAvailable() {
        java.util.concurrent.CompletionException ex = assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> feature.downloadAndApplyUpdate().join()
        );
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void downloadReturnsEarlyIfAlreadyStaged() {
        UpdateCheckResult updateResult = new UpdateCheckResult(
                true, "1.0.0", "1.1.0",
                "https://example.com/mod.jar", "sha512",
                "avoutils-1.1.0.jar", 12345, "Changelog"
        );
        when(checker.checkForUpdate()).thenReturn(CompletableFuture.completedFuture(updateResult));
        feature.checkForUpdate().join();

        when(applier.isUpdateStaged()).thenReturn(true);

        assertDoesNotThrow(() -> feature.downloadAndApplyUpdate().join());
        verify(checker, never()).downloadUpdate(any());
    }

    @Test
    void checkForUpdateReusesInFlightCheck() {
        CompletableFuture<UpdateCheckResult> inFlight = new CompletableFuture<>();
        when(checker.checkForUpdate()).thenReturn(inFlight);

        CompletableFuture<UpdateCheckResult> future1 = feature.checkForUpdate();
        CompletableFuture<UpdateCheckResult> future2 = feature.checkForUpdate();

        assertSame(future1, future2);
        verify(checker, times(1)).checkForUpdate();

        UpdateCheckResult result = UpdateCheckResult.upToDate("1.0.0");
        inFlight.complete(result);

        assertEquals(result, future1.join());
    }

    @Test
    void handleCheckCommandTriggersCheck() {
        UpdateCheckResult updateResult = new UpdateCheckResult(
                true, "1.0.0", "1.1.0",
                "https://example.com/mod.jar", "sha512",
                "avoutils-1.1.0.jar", 12345, "Changelog"
        );
        when(checker.checkForUpdate()).thenReturn(CompletableFuture.completedFuture(updateResult));

        feature.handleCheckCommand(false);

        verify(checker, times(1)).checkForUpdate();
        assertEquals(UpdateFeature.UpdateState.UPDATE_AVAILABLE, feature.getState());
    }

    @Test
    void handleDownloadCommandWhenNoUpdateAvailableDoesNotAttemptDownload() {
        UpdateCheckResult upToDate = UpdateCheckResult.upToDate("1.0.0");
        when(checker.checkForUpdate()).thenReturn(CompletableFuture.completedFuture(upToDate));

        feature.handleDownloadCommand();

        verify(checker, times(1)).checkForUpdate();
        verify(checker, never()).downloadUpdate(any());
    }

    @Test
    void requestRestartWhenUpdateStagedCallsStopClient() {
        TestableUpdateFeature testFeature = new TestableUpdateFeature(checker, applier);
        when(applier.isUpdateStaged()).thenReturn(true);

        testFeature.requestRestart();

        assertTrue(testFeature.stopClientCalled);
    }

    @Test
    void requestRestartWhenNoUpdateStagedDoesNotCallStopClient() {
        TestableUpdateFeature testFeature = new TestableUpdateFeature(checker, applier);
        when(applier.isUpdateStaged()).thenReturn(false);

        testFeature.requestRestart();

        assertFalse(testFeature.stopClientCalled);
    }

    @Test
    void buildUpdateDownloadedMessageStylesTextWithGrayAndGreenAccent() {
        var msg = UpdateFeature.buildUpdateDownloadedMessage("1.2.0");
        String plain = msg.getString();

        assertTrue(plain.contains("Update v1.2.0 downloaded! "));

        // Verify click action on the pill sibling
        var restartPill = msg.getSiblings().stream()
                .filter(s -> s.getStyle().getClickEvent() != null)
                .findFirst();
        assertTrue(restartPill.isPresent());
        assertInstanceOf(net.minecraft.text.ClickEvent.RunCommand.class, restartPill.get().getStyle().getClickEvent());
        assertEquals("/avo update restart", ((net.minecraft.text.ClickEvent.RunCommand) restartPill.get().getStyle().getClickEvent()).command());
    }

    @Test
    void buildPendingReadyMessageStylesTextWithGrayAndGreenAccent() {
        var msg = UpdateFeature.buildPendingReadyMessage("1.2.0");
        String plain = msg.getString();

        assertTrue(plain.contains("A previously downloaded update (v1.2.0) is ready to install! "));

        var restartPill = msg.getSiblings().stream()
                .filter(s -> s.getStyle().getClickEvent() != null)
                .findFirst();
        assertTrue(restartPill.isPresent());
        assertInstanceOf(net.minecraft.text.ClickEvent.RunCommand.class, restartPill.get().getStyle().getClickEvent());
        assertEquals("/avo update restart", ((net.minecraft.text.ClickEvent.RunCommand) restartPill.get().getStyle().getClickEvent()).command());
    }

    @Test
    void buildUpdateAvailableMessageIncludesVersionsAndPill() {
        UpdateCheckResult result = new UpdateCheckResult(
                true, "1.0.0", "1.2.0",
                "https://example.com/mod.jar", "sha512",
                "avoutils-1.2.0.jar", 12345, "Changelog"
        );
        var msg = UpdateFeature.buildUpdateAvailableMessage(result);
        String plain = msg.getString();

        assertTrue(plain.contains("Update available: v1.0.0 → v1.2.0 "));

        var updatePill = msg.getSiblings().stream()
                .filter(s -> s.getStyle().getClickEvent() != null)
                .findFirst();
        assertTrue(updatePill.isPresent());
        assertInstanceOf(net.minecraft.text.ClickEvent.RunCommand.class, updatePill.get().getStyle().getClickEvent());
        assertEquals("/avo update download", ((net.minecraft.text.ClickEvent.RunCommand) updatePill.get().getStyle().getClickEvent()).command());
    }

    @Test
    void buildUpToDateMessageIncludesCurrentVersion() {
        var msg = UpdateFeature.buildUpToDateMessage("1.0.0");
        String plain = msg.getString();

        assertTrue(plain.contains("You are running the latest version (v1.0.0)."));
    }

    static class TestableUpdateFeature extends UpdateFeature {
        boolean stopClientCalled = false;

        TestableUpdateFeature(ModrinthUpdateChecker checker, UpdateApplier applier) {
            super(checker, applier);
        }

        @Override
        void stopClient() {
            stopClientCalled = true;
        }
    }
}

