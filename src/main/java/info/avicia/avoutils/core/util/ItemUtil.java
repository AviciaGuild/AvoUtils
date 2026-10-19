package info.avicia.avoutils.core.util;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Reads item data shown in menus. */
public final class ItemUtil {
    private ItemUtil() {
    }

    /** The item's lore as plain text lines; empty if it has none. */
    public static List<String> loreLines(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
    }
}
