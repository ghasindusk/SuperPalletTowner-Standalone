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
        assertFalse(TrainerOriginLogic.isTrainer(null));
    }

    @Test
    void withNeoOriginsOnlyTheOriginCounts() {
        assertTrue(TrainerOriginLogic.isTrainer(true, PRIMARY_TRAINER, false));
        assertFalse(TrainerOriginLogic.isTrainer(true, PRIMARY_HUMAN, true));
        assertFalse(TrainerOriginLogic.isTrainer(true, null, true));
    }

    @Test
    void withoutNeoOriginsTheSettingDecides() {
        assertTrue(TrainerOriginLogic.isTrainer(false, null, true));
        assertFalse(TrainerOriginLogic.isTrainer(false, null, false));
    }
}
