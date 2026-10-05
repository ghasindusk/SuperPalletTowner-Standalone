package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AffinityUiUsabilityTest {
    @Test
    void candidateClickAddsRemovesAndExplainsFullSlots() {
        var selected = List.of("rock", "steel", "fire");
        assertEquals("remove", AffinityUiLogic.candidateClick(selected, -1, "steel").packetAction());
        assertEquals("replace:1", AffinityUiLogic.candidateClick(selected, 1, "water").packetAction());
        assertEquals(AffinityUiLogic.Kind.FULL,
                AffinityUiLogic.candidateClick(selected, -1, "water").kind());
        assertEquals("select", AffinityUiLogic.candidateClick(List.of("rock"), -1, "water").packetAction());
        assertEquals("steel", AffinityUiLogic.slotRemove(selected, 1).type());
        assertEquals(2, AffinityUiLogic.digitSlot(AffinityUiLogic.KEY_KP_1 + 2));
    }

    @Test
    void linkSuggestionSwitchesPairByRemovingBeforeAdding() {
        assertEquals(List.of(new LinkSuggestions.Request("link_remove", "rock"),
                        new LinkSuggestions.Request("link_add", "fire")),
                LinkSuggestions.plan(List.of("rock", "steel"), List.of("steel", "fire")));
        assertTrue(LinkSuggestions.plan(List.of("rock", "steel"), List.of("rock", "steel")).isEmpty());
    }

    @Test
    void hudPlacementStaysOnScreenAtBothReviewSizes() {
        for (int[] size : new int[][] {{427, 240}, {640, 360}}) {
            for (int scale : new int[] {50, 100, 200}) {
                for (HudLayout.Anchor anchor : HudLayout.Anchor.values()) {
                    var p = HudLayout.place(size[0], size[1], HudLayout.Shape.LINK, 3,
                            scale, anchor, 1000, -1000);
                    assertNotNull(p);
                    assertTrue(p.x() >= 0 && p.y() >= 0);
                    assertTrue(p.x() + p.width() <= size[0]);
                    assertTrue(p.y() + p.height() <= size[1]);
                }
            }
        }
        assertNull(HudLayout.place(427, 240, HudLayout.Shape.SINGLES, 0,
                100, HudLayout.Anchor.HOTBAR, 0, 0));
    }

    @Test
    void customHudCenterSurvivesShapeAndScreenSizeChanges() {
        int fractionX = HudLayout.positionFraction(180, 427);
        int fractionY = HudLayout.positionFraction(104, 240);
        for (int[] size : new int[][] {{427, 240}, {640, 360}}) {
            for (HudLayout.Shape shape : HudLayout.Shape.values()) {
                for (int count : new int[] {1, 2, 3}) {
                    var placement = HudLayout.place(size[0], size[1], shape, count, 50,
                            HudLayout.Anchor.CUSTOM, 0, 0, fractionX, fractionY);
                    int centreX = placement.x() + placement.width() / 2;
                    int centreY = placement.y() + placement.height() / 2;
                    assertEquals(Math.round(size[0] * fractionX / (float) HudLayout.POSITION_UNIT),
                            centreX, 1);
                    assertEquals(Math.round(size[1] * fractionY / (float) HudLayout.POSITION_UNIT),
                            centreY, 1);
                }
            }
        }
    }
}
