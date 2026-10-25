package starlight.trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResonanceAutogenCatalogTest {
    @Test
    void completeV5CatalogWithStableKeys() {
        assertEquals(21, ResonanceAutogenCatalog.typeTraits().size());
        assertEquals(1540, ResonanceAutogenCatalog.entries().size());
        assertEquals(210, ResonanceAutogenCatalog.entries().stream()
                .filter(entry -> entry.types().size() == 2).count());
        assertEquals(1330, ResonanceAutogenCatalog.entries().stream()
                .filter(entry -> entry.types().size() == 3).count());
        assertEquals(24, ResonanceAutogenCatalog.entries().stream().filter(ResonanceAutogenCatalog.Entry::signature).count());
        assertEquals("fire_rock_steel", ResonanceAutogenCatalog.key(List.of("steel", "fire", "rock")));
        assertEquals("", ResonanceAutogenCatalog.key(List.of("fire", "fire")));
        assertEquals("", ResonanceAutogenCatalog.key(List.of("fire", "unknown")));
        assertNull(ResonanceAutogenCatalog.find(List.of("fire", "unknown")));
    }

    @Test
    void signaturesKeepExistingIdsAndStandardTraitsMerge() {
        var original = ResonanceAutogenCatalog.find(Set.of("normal", "fighting"));
        assertTrue(original.signature());
        assertEquals("normal_fighting", original.id(), "existing save-facing recipe id is retained");
        var standard = ResonanceAutogenCatalog.find(List.of("normal", "rock"));
        assertFalse(standard.signature());
        assertEquals("normal_rock", standard.id());
        assertEquals(List.of("versatility", "mining", "amplify"), standard.primaryTraits());
        assertTrue(ResonanceAutogenCatalog.entries().stream().allMatch(entry ->
                entry.primaryTraits().size() <= (entry.types().size() == 2 ? 3 : 5)));
        assertEquals(List.of("mobility", "flow", "evasion"),
                ResonanceAutogenCatalog.typeTraits().get("air"));
    }
}
