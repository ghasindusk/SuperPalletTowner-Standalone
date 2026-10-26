package starlight.trainer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerOriginLogicTest {
    private static final String PRIMARY_TRAINER = "starlight:super_pallet_towner";
    private static final String PRIMARY_HUMAN = "neoorigins:human";

    @Test
    void directPrimaryTrainerOriginIsTrainer() {
        assertTrue(TrainerOriginLogic.isTrainer(PRIMARY_TRAINER));
    }

    @Test
    void unrelatedOrMissingPrimaryOriginIsNotTrainer() {
        assertFalse(TrainerOriginLogic.isTrainer(PRIMARY_HUMAN));
        assertFalse(TrainerOriginLogic.isTrainer((String) null));
    }

    @Test
    void withNeoOriginsOnlyTheOriginCounts() {
        assertTrue(TrainerOriginLogic.isTrainer(true, PRIMARY_TRAINER, false));
        assertFalse(TrainerOriginLogic.isTrainer(true, PRIMARY_HUMAN, true));
        assertFalse(TrainerOriginLogic.isTrainer(true, (String) null, true));
    }

    @Test
    void withoutNeoOriginsTheSettingDecides() {
        assertTrue(TrainerOriginLogic.isTrainer(false, (String) null, true));
        assertFalse(TrainerOriginLogic.isTrainer(false, (String) null, false));
    }

    @Test
    void trainerOnAnySubOriginLayerIsTrainer() {
        assertTrue(TrainerOriginLogic.isTrainer(java.util.List.of("starlight_origins:some_origin", PRIMARY_TRAINER)));
        assertTrue(TrainerOriginLogic.isTrainer(true, java.util.List.of(PRIMARY_HUMAN, PRIMARY_TRAINER), false));
    }

    @Test
    void noTrainerLayerIsNotTrainer() {
        assertFalse(TrainerOriginLogic.isTrainer(java.util.List.of(PRIMARY_HUMAN, "neoorigins:class_cook")));
        assertFalse(TrainerOriginLogic.isTrainer(java.util.List.of()));
        assertTrue(TrainerOriginLogic.isTrainer(false, java.util.List.of(), true));
    }
}
