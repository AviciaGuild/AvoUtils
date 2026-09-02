package info.avicia.avoutils.features.anniparty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnniMemberDataTest {

    @Test
    void getDisplayNameFallsBackToQuestionMark() {
        AnniMemberData member = new AnniMemberData();
        assertEquals("?", member.getDisplayName());
    }

    @Test
    void getDisplayNameReturnsName() {
        AnniMemberData member = new AnniMemberData();
        member.name = "Steve";
        assertEquals("Steve", member.getDisplayName());
    }
}
