package info.avicia.avoutils.core.gui;
 
import net.minecraft.client.MinecraftClient;
 
/**
 * Interface for modal overlays that can be resized/initialized dynamically.
 */
public interface ModalOverlay {
    void initModal(MinecraftClient client, int width, int height);
}
