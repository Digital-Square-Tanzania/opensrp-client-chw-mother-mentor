package org.smartregister.chw.mothermentor.util;

import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;

import java.util.Map;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

/** Shared interpretation of the two answers recorded after a positive HEI test. */
public final class MotherMentorHeiConfirmation {
    public enum Outcome { WAITING, NEGATIVE, POSITIVE }

    private MotherMentorHeiConfirmation() {}

    public static Outcome resolve(Stage stage, String done, String result) {
        if ("no".equals(done)) return Outcome.WAITING;
        if (!"yes".equals(done)) return null;
        if ("negative".equals(result)) return Outcome.NEGATIVE;
        if ("positive".equals(result)) return Outcome.POSITIVE;
        return "pending".equals(result) && stage != Stage.EIGHTEEN_MONTHS ? Outcome.WAITING : null;
    }

    public static Outcome fromRoutineVisit(Stage stage, Map<String, String> values) {
        if (!"positive".equals(values.get(MAMA_HEI_TEST_RESULT))) return null;
        if (values.containsKey(MAMA_HEI_CONFIRMATORY_TEST_DONE) || values.containsKey(MAMA_HEI_CONFIRMATORY_RESULT)) {
            return resolve(stage, values.get(MAMA_HEI_CONFIRMATORY_TEST_DONE), values.get(MAMA_HEI_CONFIRMATORY_RESULT));
        }
        // Preserve the interpretation of previously finalized visits during replay.
        String legacy = values.get(HEI_CHANYA_IMETHIBITISHWA);
        if ("yes".equals(legacy)) return Outcome.POSITIVE;
        return "no".equals(legacy) ? Outcome.WAITING : null;
    }
}
