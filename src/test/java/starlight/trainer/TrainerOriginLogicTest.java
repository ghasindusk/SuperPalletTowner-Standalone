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
}
