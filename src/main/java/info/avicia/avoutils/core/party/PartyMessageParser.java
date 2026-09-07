package info.avicia.avoutils.core.party;

import info.avicia.avoutils.core.util.PacketTextNormalizer;
import info.avicia.avoutils.core.util.PlayerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
     * A parsed party chat message. {@code partyListMembers} is only populated for
     * {@link Event#PARTY_LIST}.
     */
    public record Result(Event event, List<String> partyListMembers) {
        public Result(Event event) {
            this(event, List.of());
        }
    }

    private static final Pattern PARTY_JOIN_PATTERN = Pattern.compile(
            "(?:\\[.+?\\] )?(.+?) has joined your party, say hello!", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_KICK_PATTERN = Pattern.compile(
            "(?:\\[.+?\\] )?(.+?) has been kicked from the party!", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_LEAVE_PATTERN = Pattern.compile(
            "(?:\\[.+?\\] )?(.+?) has left the party!", Pattern.CASE_INSENSITIVE);
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern PARTY_MEMBERS_DELIMITER_PATTERN = Pattern.compile("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+");

    private static final String[] EVENT_KEYWORDS = {
            "party members:",
            "has joined your party",
            "has been kicked from the party",
            "has left the party",
            "you must be in a party",
            "you have been kicked from your party",
            "you have left your current party",
            "your party has been disbanded"
    };

    private PartyMessageParser() {
    }

    /**
     * Parse a chat message into a party event, or return null if the message is not a recognized
     * party event.
     */
    public static Result parse(String text) {
        if (text == null) {
            return null;
        }
        if (!PlayerUtil.containsIgnoreCase(text, "party")) {
            return null;
        }

        String trimmed = PacketTextNormalizer.stripColorCodes(text);
        String lowerTrimmed = trimmed.toLowerCase(Locale.ROOT);

        String matchedKeyword = null;
        int keywordIndex = -1;
        for (String keyword : EVENT_KEYWORDS) {
            int index = lowerTrimmed.indexOf(keyword);
            if (index != -1) {
                matchedKeyword = keyword;
                keywordIndex = index;
                break;
            }
        }
        if (keywordIndex == -1) {
            return null;
        }

        // Forgery prevention: if there is a colon before the keyword, it is a player message.
        int colonIndex = trimmed.indexOf(":");
        if (colonIndex != -1 && colonIndex < keywordIndex) {
            return null;
        }

        Event event = eventFor(matchedKeyword);
        if (event == null) {
            return null;
        }

        if (event == Event.NOT_IN_PARTY
                || event == Event.KICKED_FROM_PARTY
                || event == Event.LEFT_PARTY
                || event == Event.DISBANDED) {
            return new Result(event);
        }

        if (event == Event.PARTY_LIST) {
            return new Result(event, parsePartyList(trimmed, keywordIndex));
        }

        // Join/kick/leave keywords must additionally match their regexes to be treated as events.
        if ((event == Event.JOIN && PARTY_JOIN_PATTERN.matcher(trimmed).find())
                || (event == Event.KICK && PARTY_KICK_PATTERN.matcher(trimmed).find())
                || (event == Event.LEAVE && PARTY_LEAVE_PATTERN.matcher(trimmed).find())) {
            return new Result(event);
        }

        return null;
    }

    private static Event eventFor(String keyword) {
        return switch (keyword) {
            case "party members:" -> Event.PARTY_LIST;
            case "has joined your party" -> Event.JOIN;
            case "has been kicked from the party" -> Event.KICK;
            case "has left the party" -> Event.LEAVE;
            case "you must be in a party" -> Event.NOT_IN_PARTY;
            case "you have been kicked from your party" -> Event.KICKED_FROM_PARTY;
            case "you have left your current party" -> Event.LEFT_PARTY;
            case "your party has been disbanded" -> Event.DISBANDED;
            default -> null;
        };
    }

    private static List<String> parsePartyList(String trimmed, int keywordIndex) {
        String membersTail = trimmed.substring(keywordIndex + "party members:".length()).trim();
        if (membersTail.isEmpty()) {
            return List.of();
        }

        String[] tokens = PARTY_MEMBERS_DELIMITER_PATTERN.split(membersTail);
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
