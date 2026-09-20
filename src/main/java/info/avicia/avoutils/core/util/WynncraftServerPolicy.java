package info.avicia.avoutils.core.util;

import info.avicia.avoutils.AvoUtilsMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Enforces server policies: gates features to Wynncraft and blocks networking on beta.
 */
public final class WynncraftServerPolicy {

    public static final String NOT_ON_WYNCRAFT_MESSAGE = "AvoUtils is only available on Wynncraft.";
    public static final String BETA_NETWORKING_BLOCKED_MESSAGE = "AvoUtils network features are only available on the main Wynncraft server.";

    private static Scope lastScope = Scope.BLOCKED;
    private static final List<Consumer<Scope>> scopeChangeListeners = new CopyOnWriteArrayList<>();
    private static volatile Supplier<Scope> scopeOverride = null;

    private WynncraftServerPolicy() {}

    public static void initialize() {
        // Disconnect listener: immediately resets state when leaving a server/world
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            updateScope(Scope.BLOCKED, null);
        });

        // Client tick check for server transitions
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Scope current = currentScope();
            String currentHost = currentNormalizedHost();
            if (current != lastScope) {
                updateScope(current, currentHost);
            }
        });
    }

    public static void addScopeChangeListener(Consumer<Scope> listener) {
        scopeChangeListeners.add(listener);
    }

    public static void removeScopeChangeListener(Consumer<Scope> listener) {
        scopeChangeListeners.remove(listener);
    }

    public static void setScopeOverride(Supplier<Scope> override) {
        scopeOverride = override;
    }

    public static Scope getLastScope() {
        return lastScope;
    }

    private static void updateScope(Scope newScope, String host) {
        Scope oldScope = lastScope;
        lastScope = newScope;
        if (oldScope != newScope) {
            AvoUtilsMod.LOGGER.info("[AvoUtils] Wynncraft server scope changed: {} -> {} (host={})", oldScope, newScope, host);
            for (Consumer<Scope> listener : scopeChangeListeners) {
                try {
                    listener.accept(newScope);
                } catch (Exception e) {
                    AvoUtilsMod.LOGGER.error("[AvoUtils] Error in scope change listener", e);
                }
            }
        }
    }

    public static Scope currentScope() {
        if (scopeOverride != null) {
            return scopeOverride.get();
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return Scope.BLOCKED;
        }
        Scope scope = classifyCurrentServer(
                currentServerAddress(),
                mc.isInSingleplayer() || mc.isIntegratedServerRunning(),
                mc.isInSingleplayer(),
                mc.getNetworkHandler() != null
        );
        if (scope == Scope.UNKNOWN && lastScope != Scope.BLOCKED) {
            return lastScope;
        }
        return scope;
    }

    public static boolean isOnWynncraft() {
        Scope scope = currentScope();
        return scope == Scope.MAIN || scope == Scope.BETA;
    }

    public static boolean isNetworkingAllowed() {
        return currentScope() == Scope.MAIN;
    }

    public static String currentNormalizedHost() {
        return normalizeHost(currentServerAddress());
    }

    public static Scope classifyAddress(String serverAddress) {
        String normalizedHost = normalizeHost(serverAddress);
        if (normalizedHost == null) {
            return Scope.UNKNOWN;
        }
        if (isHostOrSubdomain(normalizedHost, "beta.wynncraft.com")
                || isHostOrSubdomain(normalizedHost, "beta.wynncraft.net")
                || isHostOrSubdomain(normalizedHost, "beta.wynncraft.org")) {
            return Scope.BETA;
        }
        if (isHostOrSubdomain(normalizedHost, "wynncraft.com")
                || isHostOrSubdomain(normalizedHost, "wynncraft.net")
                || isHostOrSubdomain(normalizedHost, "wynncraft.org")) {
            return Scope.MAIN;
        }
        return Scope.BLOCKED;
    }

    public static boolean isHostOrSubdomain(String host, String domain) {
        if (host == null || domain == null) return false;
        return domain.equalsIgnoreCase(host) || host.toLowerCase(Locale.ROOT).endsWith("." + domain.toLowerCase(Locale.ROOT));
    }

    public static Scope classifyCurrentServer(
            String serverAddress, boolean localServer, boolean singleplayerServer, boolean multiplayerConnectionPresent) {
        if (localServer || singleplayerServer) {
            return Scope.BLOCKED;
        }

        Scope addressScope = classifyAddress(serverAddress);
        if (addressScope != Scope.UNKNOWN) {
            return addressScope;
        }

        // Server transfers can briefly clear the current host before the next Wynncraft host is known.
        if (multiplayerConnectionPresent) {
            return Scope.UNKNOWN;
        }

        return Scope.BLOCKED;
    }

    public static String normalizeHost(String serverAddress) {
        if (serverAddress == null) {
            return null;
        }

        String normalized = serverAddress.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator >= 0) {
            normalized = normalized.substring(schemeSeparator + 3);
        }

        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }

        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.startsWith("[")) {
            return null;
        }

        int colonIndex = normalized.indexOf(':');
        if (colonIndex >= 0) {
            normalized = normalized.substring(0, colonIndex);
        }

        return normalized.isEmpty() ? null : normalized;
    }

    private static String currentServerAddress() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return null;
        try {
            ServerInfo serverData = mc.getCurrentServerEntry();
            return serverData != null ? serverData.address : null;
        } catch (Exception e) {
            return null;
        }
    }

    public enum Scope {
        MAIN,
        BETA,
        UNKNOWN,
        BLOCKED
    }
}

