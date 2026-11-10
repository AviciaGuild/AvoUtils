package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.chat.DiscordMarkdown;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Detects guild member and rank events (promotions, demotions, joins, leaves,
 * kicks, invites, and uninvites) from system chat and formats them for Discord relay.
 */
final class GuildMemberDetector {

    private static final List<String> RANK_ORDER = List.of(
            "Recruit", "Recruiter", "Captain", "Strategist", "Chief", "Owner");

    private static final Pattern STARTS_WITH_GUILD_TAG = Pattern.compile(
            "^\\[guild]\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern RANK_CHANGE_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+has set\\s+(?<target>.+?)\\s+guild rank from\\s+"
                    + "(?<oldRank>Recruit|Recruiter|Captain|Strategist|Chief|Owner)\\s+to\\s+"
                    + "(?<newRank>Recruit|Recruiter|Captain|Strategist|Chief|Owner)[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JOIN_PATTERN = Pattern.compile(
            "^(?<target>.+?)\\s+has joined the guild(?:,\\s*say hello)?[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LEAVE_PATTERN = Pattern.compile(
            "^(?<target>.+?)\\s+has left the guild[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KICK_ACTIVE_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+(?:have\\s+|has\\s+)?(?:successfully\\s+)?kicked\\s+(?<target>.+?)\\s+"
                    + "from\\s+(?:the|your)\\s+guild[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KICK_PASSIVE_PATTERN = Pattern.compile(
            "^(?<target>.+?)\\s+(?:was|has\\s+been)\\s+kicked\\s+from\\s+(?:the|your)\\s+guild\\s+"
                    + "by\\s+(?<actor>.+?)[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVITE_ACTIVE_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+(?:have\\s+|has\\s+)?(?:successfully\\s+)?(?<verb>invited|uninvited)\\s+"
                    + "(?<target>.+?)\\s+(?:to\\s+(?:join\\s+)?|from\\s+)(?:(?:the|your)\\s+)?guild[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVITE_PASSIVE_PATTERN = Pattern.compile(
            "^(?<target>.+?)\\s+(?:was|has\\s+been)\\s+(?<verb>invited|uninvited)\\s+"
                    + "(?:to\\s+(?:join\\s+)?|from\\s+)(?:(?:the|your)\\s+)?guild\\s+by\\s+(?<actor>.+?)[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private GuildMemberDetector() {
    }

    static Result tryDetect(String cleaned, Component message) {
        return tryDetect(cleaned, message, PlayerUtil.selfName());
    }

    static Result tryDetect(String cleaned, Component message, String localUsername) {
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.contains(":") || cleaned.contains("->")) {
            return null;
        }

        if (!PlayerUtil.containsIgnoreCase(cleaned, "guild")) {
            return null;
        }

        String text = STARTS_WITH_GUILD_TAG.matcher(cleaned).replaceFirst("").trim();
        if (text.isEmpty()) {
            return null;
        }

        // ── Promotions & Demotions ────────────────────────────────────
        Matcher m = RANK_CHANGE_PATTERN.matcher(text);
        if (m.matches()) {
            String oldRank = canonicalRank(m.group("oldRank"));
            String newRank = canonicalRank(m.group("newRank"));
            int oldIdx = RANK_ORDER.indexOf(oldRank);
            int newIdx = RANK_ORDER.indexOf(newRank);
            if (oldIdx < 0 || newIdx < 0 || oldIdx == newIdx) {
                return null;
            }
            String actor = resolvePersonEscaped(message, m.group("actor"), localUsername);
            String target = resolvePersonEscaped(message, m.group("target"), null);
            if (actor == null || target == null) return null;

            String verb = newIdx > oldIdx ? "promoted" : "demoted";
            String formatted = "**" + actor + "** " + verb + " **" + target
                    + "** from **" + oldRank + "** to **" + newRank + "**";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Rank {}: {} -> {}", verb, oldRank, newRank);
            return new Result(formatted);
        }

        // ── Join ──────────────────────────────────────────────────────
        m = JOIN_PATTERN.matcher(text);
        if (m.matches()) {
            String target = resolvePersonEscaped(message, m.group("target"), null);
            if (target == null) return null;
            String formatted = "**" + target + "** has joined the guild!";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member joined");
            return new Result(formatted);
        }

        // ── Leave ─────────────────────────────────────────────────────
        m = LEAVE_PATTERN.matcher(text);
        if (m.matches()) {
            String target = resolvePersonEscaped(message, m.group("target"), null);
            if (target == null) return null;
            String formatted = "**" + target + "** has left the guild";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member left");
            return new Result(formatted);
        }

        // ── Kick ──────────────────────────────────────────────────────
        m = KICK_ACTIVE_PATTERN.matcher(text);
        if (!m.matches()) {
            m = KICK_PASSIVE_PATTERN.matcher(text);
        }
        if (m.matches()) {
            String actor = resolvePersonEscaped(message, m.group("actor"), localUsername);
            String target = resolvePersonEscaped(message, m.group("target"), null);
            if (actor == null || target == null) return null;
            String formatted = "**" + actor + "** kicked **" + target + "** from the guild";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member kicked");
            return new Result(formatted);
        }

        // ── Invite / Uninvite ─────────────────────────────────────────
        m = INVITE_ACTIVE_PATTERN.matcher(text);
        if (!m.matches()) {
            m = INVITE_PASSIVE_PATTERN.matcher(text);
        }
        if (m.matches()) {
            String actor = resolvePersonEscaped(message, m.group("actor"), localUsername);
            String target = resolvePersonEscaped(message, m.group("target"), null);
            if (actor == null || target == null) return null;

            boolean isInvite = "invited".equalsIgnoreCase(m.group("verb"));
            String formatted = isInvite
                    ? "**" + actor + "** invited **" + target + "** to the guild"
                    : "**" + actor + "** uninvited **" + target + "** from the guild";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member {}", isInvite ? "invited" : "uninvited");
            return new Result(formatted);
        }

        return null;
    }

    private static String resolvePersonEscaped(Component message, String rawName, String localUsername) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("you".equalsIgnoreCase(trimmed)) {
            if (!UsernameResolver.isValid(localUsername)) {
                return null;
            }
            return DiscordMarkdown.escapeUsername(localUsername.trim());
        }
        String resolved = UsernameResolver.resolve(message, trimmed);
        return resolved != null ? DiscordMarkdown.escapeUsername(resolved) : null;
    }

    private static String canonicalRank(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    record Result(String formattedMessage) {}
}
