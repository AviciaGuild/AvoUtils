package info.avicia.avoutils.core.gui;
 
import net.minecraft.client.Minecraft;
 
/**
 * Interface for modal overlays that can be resized/initialized dynamically.
 */
public interface ModalOverlay {
    void initModal(Minecraft client, int width, int height);

    default void onCloseModal() {}
}
