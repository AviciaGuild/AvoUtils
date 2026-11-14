package info.avicia.avoutils.features.chatbridge;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.UsernameResolver;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * Detects guild alliance messages (requests, rejections, withdrawals, formations,
 * and revocations) from system chat and extracts structured fields for gateway relay.
 */
final class AllyDetector {

    private static final String HOME_GUILD = "Avicia";

    enum Action {
        INCOMING_REQUEST("incoming_request"),
        OUTGOING_REQUEST("outgoing_request"),
        THEIR_REJECT("their_reject"),
        OUR_REJECT("our_reject"),
        THEIR_WITHDRAW("their_withdraw"),
        OUR_WITHDRAW("our_withdraw"),
        OUR_FORM("our_form"),
        THEIR_FORM("their_form"),
        THEIR_REVOKE("their_revoke"),
        OUR_REVOKE("our_revoke");

        private final String wireName;

        Action(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }
    }

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
        if (cleaned == null || cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.contains(":") || cleaned.contains("->")) {
            return null;
        }
        if (!PlayerUtil.containsIgnoreCase(cleaned, "alli")) {
            return null;
        }

        String text = cleaned.trim();
        if (text.isEmpty()) {
            return null;
        }

        Matcher m = INCOMING_REQUEST_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = UsernameResolver.resolve(message, m.group("actor"));
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Incoming request from {}", guild);
            return new Result(Action.INCOMING_REQUEST, actor, guild);
        }

        m = OUTGOING_REQUEST_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = UsernameResolver.resolve(message, m.group("actor"));
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Outgoing request to {}", guild);
            return new Result(Action.OUTGOING_REQUEST, actor, guild);
        }

        m = THEIR_REJECT_PATTERN.matcher(text);
        if (m.matches()) {
            String guild = cleanGuildName(m.group("guild"));
            if (guild == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] {} rejected alliance request", guild);
            return new Result(Action.THEIR_REJECT, null, guild);
        }

        m = OUR_REJECT_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = UsernameResolver.resolve(message, m.group("actor"));
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Rejected {} alliance request", guild);
            return new Result(Action.OUR_REJECT, actor, guild);
        }

        m = THEIR_WITHDRAW_PATTERN.matcher(text);
        if (m.matches()) {
            String guild = cleanGuildName(m.group("guild"));
            if (guild == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] {} withdrew alliance request", guild);
            return new Result(Action.THEIR_WITHDRAW, null, guild);
        }

        m = OUR_WITHDRAW_PATTERN.matcher(text);
        if (m.matches()) {
            String actor = UsernameResolver.resolve(message, m.group("actor"));
            String guild = cleanGuildName(m.group("guild"));
            if (actor == null || guild == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Withdrew {} alliance request", guild);
            return new Result(Action.OUR_WITHDRAW, actor, guild);
        }

        m = FORMED_PATTERN.matcher(text);
        if (m.matches()) {
            String subject = cleanGuildName(m.group("subject"));
            String object = cleanGuildName(m.group("object"));
            if (subject == null || object == null) return null;
            if (HOME_GUILD.equalsIgnoreCase(subject) && !HOME_GUILD.equalsIgnoreCase(object)) {
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Alliance formed with {}", object);
                return new Result(Action.OUR_FORM, null, object);
            }
            if (HOME_GUILD.equalsIgnoreCase(object) && !HOME_GUILD.equalsIgnoreCase(subject)) {
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Alliance formed with {}", subject);
                return new Result(Action.THEIR_FORM, null, subject);
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
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] {} revoked alliance with {}", partner, HOME_GUILD);
                return new Result(Action.THEIR_REVOKE, null, partner);
            }
            if (HOME_GUILD.equalsIgnoreCase(subject)) {
                AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Revoked alliance with {}", object);
                return new Result(Action.OUR_REVOKE, null, object);
            }

            String actor = UsernameResolver.resolve(message, subject);
            if (actor == null) return null;
            AvoUtilsMod.LOGGER.info("[AvoUtils] [ChatBridge/Ally] Revoked alliance with {}", object);
            return new Result(Action.OUR_REVOKE, actor, object);
        }

        return null;
    }

    private static String cleanGuildName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return null;
        }
        return trimmed;
    }

    record Result(Action action, String actor, String guild) {
        String dedupKey() {
            return action.wireName() + "\u0000" + (actor == null ? "" : actor) + "\u0000" + guild;
        }
    }
}
