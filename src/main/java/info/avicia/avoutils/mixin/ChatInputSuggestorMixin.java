package info.avicia.avoutils.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.features.emojis.EmojiFeature;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides emoji autocomplete suggestions when typing shortcodes in chat.
 */
@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow
    TextFieldWidget textField;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    boolean completingSuggestions;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At("TAIL"))
    private void avoutils$onRefresh(CallbackInfo ci) {
        if (this.completingSuggestions) {
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

        if (this.textField == null) {
            return;
        }

        String text = this.textField.getText();
        int cursor = this.textField.getCursor();
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
                    builder.suggest(match, Text.literal(pua));
                } else {
                    builder.suggest(match);
                }
            }
            this.pendingSuggestions = builder.buildFuture();
            this.show(false);
        }
    }
}

