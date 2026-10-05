package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffinityViewTest {
    @Test
    void standardTypesHaveTheirOwnSymbolAndDeferredTypesDoNot() {
        assertEquals(18, AffinityView.STANDARD_TYPES.size());
        for (String extra : List.of("sound", "light", "air")) {
            assertTrue(AffinityView.isConfigured(extra));
            assertFalse(AffinityView.isStandard(extra));
            assertEquals(-1, AffinityView.atlasIndex(extra), "uses its supplied custom icon");
        }
        for (String deferred : List.of("cosmic", "digital", "nuclear", "void", "mystic")) {
            assertFalse(AffinityView.isConfigured(deferred));
            assertFalse(AffinityView.isStandard(deferred), deferred);
            assertEquals(-1, AffinityView.atlasIndex(deferred), deferred);
        }
    }

    @Test
    void atlasCellsMatchCobblemonTextureXMultiplier() {
        // ElementalTypes.register(name, …, hue, textureXMultiplier, …) in Cobblemon 1.8.1.
        String[] cobblemonOrder = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison",
                "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};
        for (int i = 0; i < cobblemonOrder.length; i++) assertEquals(i, AffinityView.atlasIndex(cobblemonOrder[i]));
        assertEquals(1, AffinityView.atlasIndex("Fire"));
        assertEquals(AffinityView.ATLAS_CELLS, cobblemonOrder.length);
        assertEquals(18, AffinityView.TYPE_ICON, "half a 36px cell, Cobblemon TypeIcon size");
    }

    @Test
    void candidatesFollowTypeOrderThenUnknownByName() {
        assertEquals(List.of("fire", "water", "psychic", "dark", "fairy", "sound", "zzz"),
                AffinityView.displayOrder(List.of("zzz", "dark", "fairy", "water", "sound", "psychic", "fire")));
    }

    @Test
    void slotStateSeparatesActiveInactiveAndEmpty() {
        List<String> selected = List.of("fire", "water");
        Set<String> available = Set.of("fire", "grass");
        assertEquals(AffinityView.SlotState.ACTIVE, AffinityView.slotState(selected, available, 0));
        assertEquals(AffinityView.SlotState.INACTIVE, AffinityView.slotState(selected, available, 1));
        assertEquals(AffinityView.SlotState.EMPTY, AffinityView.slotState(selected, available, 2));
        assertEquals(AffinityView.SlotState.EMPTY, AffinityView.slotState(selected, available, -1));
    }

    @Test
    void hudShowsOnlyActiveTypesOnceInSlotOrder() {
        assertEquals(List.of("water", "fire"),
                AffinityView.hudTypes(List.of("water", "grass", "fire"), Set.of("fire", "water")));
        assertEquals(List.of(), AffinityView.hudTypes(List.of("water"), Set.of()));
        assertEquals(List.of("fire"), AffinityView.hudTypes(List.of("Fire", "fire"), Set.of("fire")));
        assertEquals(3, AffinityView.hudTypes(List.of("a", "b", "c", "d"), Set.of("a", "b", "c", "d")).size());
    }

    @Test
    void hudSitsRightOfHotbarClearOfOffhandAndAttackIndicator() {
        // 1920x1080 at GUI scale 2, 1280x720 / 1920x1080 at scale 2 / 3, 2240x1260 at scale 4.
        for (int[] size : new int[][] {{960, 540}, {640, 360}, {560, 315}}) {
            var placement = AffinityView.hudPlacement(size[0], size[1], 3);
            assertNotNull(placement);
            assertTrue(placement.besideHotbar(), size[0] + "x" + size[1]);
            int hotbarRight = size[0] / 2 + 91;
            // Offhand slot (right side) ends at +29, attack indicator at +6..+24.
            assertTrue(placement.x() >= hotbarRight + 30, "clear of offhand/attack indicator");
            int rowEnd = placement.nextX(2) + AffinityView.HUD_ICON;
            assertTrue(rowEnd <= size[0] - AffinityView.HUD_RIGHT_RESERVE, "clear of the bottom-right corner HUDs");
            assertTrue(placement.y() >= size[1] - 22 && placement.y() + AffinityView.HUD_ICON <= size[1],
                    "within the hotbar band, below chat");
            assertEquals(placement.y(), placement.nextY(2));
        }
    }

    @Test
    void narrowWindowUsesRightColumnBelowPotionIconsAndEmptyShowsNothing() {
        // 640x480 / 854x480 at scale 2, 1920x1080 at scale 4.
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {480, 270}}) {
            var placement = AffinityView.hudPlacement(size[0], size[1], 3);
            assertNotNull(placement);
            assertFalse(placement.besideHotbar(), size[0] + "x" + size[1]);
            assertEquals(size[0] - AffinityView.HUD_MARGIN - AffinityView.HUD_ICON, placement.x());
            assertEquals(placement.x(), placement.nextX(2));
            assertTrue(placement.y() >= 52, "below vanilla's two potion-icon rows");
            int columnEnd = placement.nextY(2) + AffinityView.HUD_ICON;
            // The minimap's coordinate line starts about 105 GUI px above the bottom in the pack.
            assertTrue(columnEnd <= size[1] - 110, "above the bottom-right minimap: " + columnEnd);
        }
        assertNull(AffinityView.hudPlacement(960, 540, 0));
    }

    @Test
    void linkedHudKeepsThreeSymbolsAndLineInsideTheSameReservedArea() {
        for (int[] size : new int[][] {{960, 540}, {640, 360}, {560, 315}, {320, 240}, {427, 240}}) {
            var p = AffinityView.linkHudPlacement(size[0], size[1]);
            assertTrue(p.leftX() >= 2);
            assertTrue(p.rightX() + AffinityView.HUD_ICON + 2 <= size[0]);
            assertEquals(p.leftX() + AffinityView.HUD_ICON + 12, p.rightX());
            assertTrue(p.topX() >= p.leftX() && p.topX() + AffinityView.HUD_ICON <= p.rightX() + AffinityView.HUD_ICON);
            assertEquals(p.bottomY() - AffinityView.HUD_ICON - 8, p.topY());
            if (p.besideHotbar()) {
                assertTrue(p.rightX() + AffinityView.HUD_ICON <= size[0] - AffinityView.HUD_RIGHT_RESERVE);
                assertTrue(p.bottomY() + AffinityView.HUD_ICON <= size[1]);
            } else {
                assertTrue(p.topY() >= AffinityView.HUD_COLUMN_TOP);
                assertTrue(p.bottomY() + AffinityView.HUD_ICON <= size[1] - 110);
            }
        }
    }

    @Test
    void candidateGridScrollsWhenTypesOverflow() {
        var grid = AffinityView.grid(240, 150, 14, 88, 18);
        assertEquals(2, grid.columns());
        assertEquals(10, grid.visibleRows());
        assertEquals(9, grid.totalRows());
        assertEquals(0, grid.maxScroll());

        var small = AffinityView.grid(140, 84, 14, 88, 18);
        assertEquals(1, small.columns());
        assertEquals(6, small.visibleRows());
        assertEquals(12, small.maxScroll());
        assertEquals(0, AffinityView.clampScroll(-3, small));
        assertEquals(12, AffinityView.clampScroll(99, small));
        assertEquals(5, AffinityView.clampScroll(5, small));
    }
}
