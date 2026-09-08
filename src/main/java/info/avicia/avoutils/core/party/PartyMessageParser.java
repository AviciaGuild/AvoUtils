package info.avicia.avoutils.core.party;

import info.avicia.avoutils.AvoUtilsMod;
import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.PlayerUtil;
import info.avicia.avoutils.core.util.UsernameResolver;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared parser for in-game party chat messages.
 */
public final class PartyMessageParser {

    public enum Event {
        PARTY_LIST,
        JOIN,
        KICK,
        LEAVE,
        NOT_IN_PARTY,
        KICKED_FROM_PARTY,
        LEFT_PARTY,
        DISBANDED
    }

    /**
     * A parsed party chat message. {@code partyListMembers} is populated for
     * {@link Event#PARTY_LIST}, and {@code player} is populated for
     * {@link Event#JOIN}, {@link Event#KICK}, and {@link Event#LEAVE}.
     */
    public record Result(Event event, List<String> partyListMembers, String player) {
        public Result(Event event) {
            this(event, List.of(), null);
        }

        public Result(Event event, List<String> partyListMembers) {
            this(event, partyListMembers, null);
        }

        public Result(Event event, String player) {
            this(event, List.of(), player);
        }
    }

    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern PARTY_MEMBERS_DELIMITER_PATTERN = Pattern.compile("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+");

    private static final Pattern PARTY_MEMBERS_PATTERN = Pattern.compile(
            "^Party members:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_JOIN_PATTERN = Pattern.compile(
            "^([^:\n]+?) has joined (?:your party, say hello!|the party[.!]*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_KICK_PATTERN = Pattern.compile(
            "^([^:\n]+?) has been kicked from the party!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_LEAVE_PATTERN = Pattern.compile(
            "^([^:\n]+?) has left the party[.!]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_IN_PARTY_PATTERN = Pattern.compile(
            "^You must be in a party.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern KICKED_FROM_PARTY_PATTERN = Pattern.compile(
            "^You have been kicked from your party!?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEFT_PARTY_PATTERN = Pattern.compile(
            "^You have left (?:your current|the) party[.!]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DISBANDED_PATTERN = Pattern.compile(
            "^Your party has been disbanded[.!]*$", Pattern.CASE_INSENSITIVE);

    private PartyMessageParser() {
    }

    /**
     * Parse a chat message into a party event, or return null if the message is not a recognized
     * party event.
     */
    public static Result parse(String text) {
        return parse(text, null);
    }

    /**
     * Parse a chat message with optional Text component context for resolving player nicknames via hover text.
     */
    public static Result parse(String text, Text message) {
        if (text == null) {
            return null;
        }
        if (!PlayerUtil.containsIgnoreCase(text, "party")) {
            return null;
        }

        String trimmed = PacketTextNormalizer.normalizeForParsing(text);
        if (trimmed.isEmpty()) {
            return null;
        }

        Matcher membersMatcher = PARTY_MEMBERS_PATTERN.matcher(trimmed);
        if (membersMatcher.matches()) {
            List<String> members = parsePartyList(membersMatcher.group(1));
            AvoUtilsMod.LOGGER.info("[AvoUtils] Parsed /party list ({} members): {}", members.size(), members);
            return new Result(Event.PARTY_LIST, members);
        }

        if (LEFT_PARTY_PATTERN.matcher(trimmed).matches()) {
            return new Result(Event.LEFT_PARTY);
        }
        if (KICKED_FROM_PARTY_PATTERN.matcher(trimmed).matches()) {
            return new Result(Event.KICKED_FROM_PARTY);
        }
        if (DISBANDED_PATTERN.matcher(trimmed).matches()) {
            return new Result(Event.DISBANDED);
        }
        if (NOT_IN_PARTY_PATTERN.matcher(trimmed).matches()) {
            return new Result(Event.NOT_IN_PARTY);
        }

        Matcher kickMatcher = PARTY_KICK_PATTERN.matcher(trimmed);
        if (kickMatcher.matches()) {
            String rawPlayer = kickMatcher.group(1).trim();
            String resolved = message != null ? UsernameResolver.resolve(message, rawPlayer) : null;
            return new Result(Event.KICK, resolved != null ? resolved : rawPlayer);
        }
        Matcher joinMatcher = PARTY_JOIN_PATTERN.matcher(trimmed);
        if (joinMatcher.matches()) {
            String rawPlayer = joinMatcher.group(1).trim();
            String resolved = message != null ? UsernameResolver.resolve(message, rawPlayer) : null;
            return new Result(Event.JOIN, resolved != null ? resolved : rawPlayer);
        }
        Matcher leaveMatcher = PARTY_LEAVE_PATTERN.matcher(trimmed);
        if (leaveMatcher.matches()) {
            String rawPlayer = leaveMatcher.group(1).trim();
            String resolved = message != null ? UsernameResolver.resolve(message, rawPlayer) : null;
            return new Result(Event.LEAVE, resolved != null ? resolved : rawPlayer);
        }

        return null;
    }

    private static List<String> parsePartyList(String membersTail) {
        if (membersTail == null || membersTail.isBlank()) {
            return List.of();
        }

        String[] tokens = PARTY_MEMBERS_DELIMITER_PATTERN.split(membersTail.trim());
        List<String> members = new ArrayList<>();
        for (String token : tokens) {
            String name = token == null ? "" : token.trim();
            if (MC_USERNAME_PATTERN.matcher(name).matches()) {
                members.add(name);
            }
        }
        return members;
    }
}
