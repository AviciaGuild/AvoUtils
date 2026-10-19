package info.avicia.avoutils.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.WynncraftServerPolicy;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Provides emoji autocomplete suggestions when typing shortcodes in chat.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow
    EditBox input;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    boolean keepSuggestions;

    @Shadow
    public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void avoutils$onRefresh(CallbackInfo ci) {
        if (this.keepSuggestions) {
            return;
        }

        if (!WynncraftServerPolicy.isOnWynncraft()) {
            return;
        }

        AvoUtilsMod mod = AvoUtilsMod.getInstance();
        if (mod == null) {
            return;
        }
        EmojiFeature feature = mod.getFeature(EmojiFeature.class);
        if (feature == null || !feature.isAutocompleteEnabled()) {
            return;
        }

        if (this.input == null) {
            return;
        }

        String text = this.input.getValue();
        int cursor = this.input.getCursorPosition();
        if (cursor < 0 || cursor > text.length()) {
            return;
        }

        String textBeforeCursor = text.substring(0, cursor);

        EmojiFeature.AutocompletePrefix prefixInfo = EmojiFeature.findAutocompletePrefix(textBeforeCursor);
        if (prefixInfo == null) {
            return;
        }

        // Never interfere while typing the command name itself
        if (text.startsWith("/") && prefixInfo.startIndex() == 0) {
            return;
        }

        List<String> matches = feature.getMatchingShortcodes(prefixInfo.prefix());
        if (!matches.isEmpty()) {
            SuggestionsBuilder builder = new SuggestionsBuilder(textBeforeCursor, prefixInfo.startIndex());
            for (String match : matches) {
                String pua = feature.getEmojiReplacement(match);
                if (pua != null) {
                    builder.suggest(match, Component.literal(pua));
                } else {
                    builder.suggest(match);
                }
            }
            this.pendingSuggestions = builder.buildFuture();
            this.showSuggestions(false);
        }
    }
}

