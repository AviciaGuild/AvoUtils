package info.avicia.avoutils.features.rewards;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.ItemUtil;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.GuildScreen;
import info.avicia.avoutils.features.rewards.menu.GuildRewardMenu.MenuItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The open container screen, for {@link RewardDistributor}. Clicks go out as raw click packets, as Wynntils sends
 * them on Wynncraft's menus: vanilla's click call would first predict a key press as a swap on screen, moving the
 * head into the hotbar until the server corrects it, and the next press could miss the head.
 */
final class MinecraftGame implements RewardDistributor.Game {
    private int loggedContainerId = -1;
    private int loggedStateId = -1;

    private static AbstractContainerMenu menu() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen ? screen.getMenu() : null;
    }

    @Override
    public GuildScreen screen() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                ? GuildRewardMenu.screenOf(screen.getTitle().getString())
                : null;
    }

    @Override
    public int containerId() {
        AbstractContainerMenu menu = menu();
        return menu != null ? menu.containerId : -1;
    }

    @Override
    public int stateId() {
        AbstractContainerMenu menu = menu();
        return menu != null ? menu.getStateId() : -1;
    }

    /** The container's items, without the player's inventory below it. */
    @Override
    public List<MenuItem> items() {
        AbstractContainerMenu menu = menu();
        if (menu == null) return List.of();
        List<MenuItem> items = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory || !slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            ResolvableProfile profile = stack.get(DataComponents.PROFILE);
            items.add(new MenuItem(slot.index, stack.getHoverName().getString(), ItemUtil.loreLines(stack),
                    profile != null ? profile.partialProfile().id() : null,
                    profile != null ? profile.name().orElse(null) : null));
        }
        if (RewardsFeature.DEBUG && (menu.containerId != loggedContainerId || menu.getStateId() != loggedStateId)) {
            loggedContainerId = menu.containerId;
            loggedStateId = menu.getStateId();
            AvoUtilsMod.LOGGER.info("[AvoUtils] [Rewards] Menu {} (state {}):", loggedContainerId, loggedStateId);
            for (MenuItem item : items) {
                AvoUtilsMod.LOGGER.info("[AvoUtils] [Rewards]   {}", item);
            }
        }
        return items;
    }

    @Override
    public void click(int slot) {
        send(slot, 0, ClickType.PICKUP);
    }

    @Override
    public void pressKey(int slot, int key) {
        send(slot, key - 1, ClickType.SWAP);
    }

    private static void send(int slot, int button, ClickType type) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractContainerMenu menu = menu();
        if (menu == null || minecraft.player == null || minecraft.getConnection() == null
                || minecraft.player.containerMenu.containerId != menu.containerId) {
            return;
        }
        minecraft.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                (short) slot, (byte) button, type, Int2ObjectMaps.emptyMap(), HashedStack.EMPTY));
    }

    @Override
    public boolean isSelf(String username) {
        return PlayerUtil.isSelf(username);
    }
}
