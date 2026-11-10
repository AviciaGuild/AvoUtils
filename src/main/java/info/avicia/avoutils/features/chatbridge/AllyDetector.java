package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.chat.DiscordMarkdown;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Detects guild alliance messages (requests, rejections, withdrawals, formations,
 * and revocations) from system chat and formats them for Discord relay.
 */
final class AllyDetector {

    private static final String HOME_GUILD = "Avicia";

    private static final Pattern LEADING_TAG_PATTERN = Pattern.compile(
            "^(?:\\[(?:guild|info)]\\s*)+", Pattern.CASE_INSENSITIVE);

    private static final Pattern INCOMING_REQUEST_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+from\\s+(?<guild>.+?)\\s+is requesting to be allied[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OUTGOING_REQUEST_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+sent\\s+(?<guild>.+?)\\s+a request to be allied[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern THEIR_REJECT_PATTERN = Pattern.compile(
            "^(?<guild>.+?)\\s+[Rr]ejected the alliance request[.!]?$");

    private static final Pattern OUR_REJECT_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+rejected\\s+(?<guild>.+?)(?:'s)?\\s+alliance request[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern THEIR_WITHDRAW_PATTERN = Pattern.compile(
            "^(?<guild>.+?)\\s+[Ww]ithdrew the alliance request[.!]?$");

    private static final Pattern OUR_WITHDRAW_PATTERN = Pattern.compile(
            "^(?<actor>.+?)\\s+withdrew\\s+(?<guild>.+?)(?:'s)?\\s+alliance request[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FORMED_PATTERN = Pattern.compile(
            "^(?<subject>.+?)\\s+formed an alliance with\\s+(?<object>.+?)[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REVOKED_PATTERN = Pattern.compile(
            "^(?<subject>.+?)\\s+revoked the alliance with\\s+(?<object>.+?)[.!]?$",
            Pattern.CASE_INSENSITIVE);

    private AllyDetector() {
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
        if (!PlayerUtil.containsIgnoreCase(cleaned, "alli")) {
            return null;
        }

        String text = LEADING_TAG_PATTERN.matcher(cleaned).replaceFirst("").trim();
        if (text.isEmpty()) {
            return null;
        }

        Matcher m = INCOMING_REQUEST_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = resolveActorEscaped(message, m.group("actor"), localUsername);
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            String formatted = "**" + actor + "** from **" + guild + "** is requesting to be allied";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Incoming request from {}", guild);
            return new Result(formatted);
        }

        m = OUTGOING_REQUEST_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = resolveActorEscaped(message, m.group("actor"), localUsername);
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            String formatted = "**" + actor + "** sent **" + guild + "** a request to be allied";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Outgoing request to {}", guild);
            return new Result(formatted);
        }

        m = THEIR_REJECT_PATTERN.matcher(text);
        if (m.matches()) {
            String guild = cleanGuildName(m.group("guild"));
            if (guild == null) return null;
            String formatted = "**" + guild + "** rejected the alliance request";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] {} rejected alliance request", guild);
            return new Result(formatted);
        }

        m = OUR_REJECT_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = resolveActorEscaped(message, m.group("actor"), localUsername);
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            String formatted = "**" + actor + "** rejected **" + guild + "**'s alliance request";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Rejected {} alliance request", guild);
            return new Result(formatted);
        }

        m = THEIR_WITHDRAW_PATTERN.matcher(text);
        if (m.matches()) {
            String guild = cleanGuildName(m.group("guild"));
            if (guild == null) return null;
            String formatted = "**" + guild + "** withdrew the alliance request";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] {} withdrew alliance request", guild);
            return new Result(formatted);
        }

        m = OUR_WITHDRAW_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = resolveActorEscaped(message, m.group("actor"), localUsername);
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            String formatted = "**" + actor + "** withdrew **" + guild + "**'s alliance request";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Withdrew {} alliance request", guild);
            return new Result(formatted);
        }

        m = FORMED_PATTERN.matcher(text);
        if (m.matches()) {
            String subject = cleanGuildName(m.group("subject"));
            String object = cleanGuildName(m.group("object"));
            if (subject == null || object == null) return null;
            if (HOME_GUILD.equalsIgnoreCase(subject) && !HOME_GUILD.equalsIgnoreCase(object)) {
                String formatted = "**" + HOME_GUILD + "** formed an alliance with **" + object + "**";
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Alliance formed with {}", object);
                return new Result(formatted);
            }
            if (HOME_GUILD.equalsIgnoreCase(object) && !HOME_GUILD.equalsIgnoreCase(subject)) {
                String formatted = "**" + subject + "** formed an alliance with **" + HOME_GUILD + "**";
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Alliance formed with {}", subject);
                return new Result(formatted);
            }
            return null;
        }

        m = REVOKED_PATTERN.matcher(text);
        if (m.matches()) {
            String subject = m.group("subject").trim();
            String object = cleanGuildName(m.group("object"));
            if (subject.isEmpty() || object == null) return null;

            if (HOME_GUILD.equalsIgnoreCase(object)) {
                String partner = cleanGuildName(subject);
                if (partner == null) return null;
                String formatted = "**" + partner + "** revoked the alliance with **" + HOME_GUILD + "**";
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] {} revoked alliance with {}", partner, HOME_GUILD);
                return new Result(formatted);
            }
            if (HOME_GUILD.equalsIgnoreCase(subject)) {
                String formatted = "**" + HOME_GUILD + "** revoked the alliance with **" + object + "**";
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Revoked alliance with {}", object);
                return new Result(formatted);
            }

            String actor = resolveActorEscaped(message, subject, localUsername);
            if (actor == null) return null;
            String formatted = "**" + actor + "** revoked the alliance with **" + object + "**";
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Revoked alliance with {}", object);
            return new Result(formatted);
        }

        return null;
    }

    private static String resolveActorEscaped(Component message, String rawActor, String localUsername) {
        if (rawActor == null) {
            return null;
        }
        String trimmed = rawActor.trim();
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

    private static String cleanGuildName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return null;
        }
        return trimmed;
    }

    record Result(String formattedMessage) {}
}
