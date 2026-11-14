package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Detects guild member and rank events (promotions, demotions, joins, leaves,
 * kicks, invites, and uninvites) from system chat and extracts structured fields for gateway relay.
 */
final class GuildMemberDetector {

    private static final List<String> RANK_ORDER = List.of(
            "Recruit", "Recruiter", "Captain", "Strategist", "Chief", "Owner");

    enum Action {
        PROMOTE("promote"),
        DEMOTE("demote"),
        JOIN("join"),
        LEAVE("leave"),
        KICK("kick"),
        INVITE("invite"),
        UNINVITE("uninvite");

        private final String wireName;

        Action(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }
    }

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
            "^(?<actor>.+?)\\s+(?:has\\s+)?kicked\\s+(?<target>.+?)\\s+"
                    + "from\\s+(?:the|your)\\s+guild[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern KICK_PASSIVE_PATTERN = Pattern.compile(
            "^(?<target>.+?)\\s+(?:was|has\\s+been)\\s+kicked\\s+from\\s+(?:the|your)\\s+guild\\s+"
                    + "by\\s+(?<actor>.+?)[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVITE_ACTIVE_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+(?:has\\s+)?(?<verb>invited|uninvited)\\s+"
                    + "(?<target>.+?)\\s+(?:to\\s+(?:join\\s+)?|from\\s+)(?:(?:the|your)\\s+)?guild[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVITE_PASSIVE_PATTERN = Pattern.compile(
            "^(?<target>.+?)\\s+(?:was|has\\s+been)\\s+(?<verb>invited|uninvited)\\s+"
                    + "(?:to\\s+(?:join\\s+)?|from\\s+)(?:(?:the|your)\\s+)?guild\\s+by\\s+(?<actor>.+?)[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private GuildMemberDetector() {
    }

    static Result tryDetect(String cleaned, Component message) {
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.contains(":") || cleaned.contains("->")) {
            return null;
        }

        if (!PlayerUtil.containsIgnoreCase(cleaned, "guild")) {
            return null;
        }

        String text = cleaned.trim();
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
            String actor = UsernameResolver.resolve(message, m.group("actor"));
            String target = UsernameResolver.resolve(message, m.group("target"));
            if (actor == null || target == null) return null;

            Action action = newIdx > oldIdx ? Action.PROMOTE : Action.DEMOTE;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Rank {}: {} -> {}",
                    action.wireName(), oldRank, newRank);
            return new Result(action, actor, target, oldRank, newRank);
        }

        // ── Join ──────────────────────────────────────────────────────
        m = JOIN_PATTERN.matcher(text);
        if (m.matches()) {
            String target = UsernameResolver.resolve(message, m.group("target"));
            if (target == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member joined");
            return new Result(Action.JOIN, null, target, null, null);
        }

        // ── Leave ─────────────────────────────────────────────────────
        m = LEAVE_PATTERN.matcher(text);
        if (m.matches()) {
            String target = UsernameResolver.resolve(message, m.group("target"));
            if (target == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member left");
            return new Result(Action.LEAVE, null, target, null, null);
        }

        // ── Kick ──────────────────────────────────────────────────────
        m = KICK_ACTIVE_PATTERN.matcher(text);
        if (!m.matches()) {
            m = KICK_PASSIVE_PATTERN.matcher(text);
        }
        if (m.matches()) {
            String actor = UsernameResolver.resolve(message, m.group("actor"));
            String target = UsernameResolver.resolve(message, m.group("target"));
            if (actor == null || target == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member kicked");
            return new Result(Action.KICK, actor, target, null, null);
        }

        // ── Invite / Uninvite ─────────────────────────────────────────
        m = INVITE_ACTIVE_PATTERN.matcher(text);
        if (!m.matches()) {
            m = INVITE_PASSIVE_PATTERN.matcher(text);
        }
        if (m.matches()) {
            String actor = UsernameResolver.resolve(message, m.group("actor"));
            String target = UsernameResolver.resolve(message, m.group("target"));
            if (actor == null || target == null) return null;

            Action action = "invited".equalsIgnoreCase(m.group("verb")) ? Action.INVITE : Action.UNINVITE;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Member] Member {}", action.wireName());
            return new Result(action, actor, target, null, null);
        }

        return null;
    }

    private static String canonicalRank(String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    record Result(Action action, String actor, String target, String oldRank, String newRank) {
        String dedupKey() {
            return action.wireName()
                    + "\u0000" + (actor == null ? "" : actor)
                    + "\u0000" + target
                    + "\u0000" + (oldRank == null ? "" : oldRank)
                    + "\u0000" + (newRank == null ? "" : newRank);
        }
    }
}
