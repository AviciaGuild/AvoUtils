package info.avicia.avoutils.core.util;

import net.minecraft.client.Minecraft;

/** Runs code on the Minecraft client thread. */
public final class ClientThread {
    private ClientThread() {
    }

    /** Queues {@code action} on the client thread; does nothing without a client. */
    public static void run(Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(action);
        }
    }
}
