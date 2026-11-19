package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;
import static org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.parseDate;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The first-visit birth test is recorded independently of any routine test due that day. */
public final class MotherMentorMotherBirthTest {
    static final List<String> ANSWERS = Arrays.asList(MAMA_BIRTH_TEST_DONE, MAMA_BIRTH_TEST_DATE,
            MAMA_BIRTH_TEST_RESULT, MAMA_BIRTH_TEST_RECEIVED_DATE, MAMA_BIRTH_TEST_CONFIRMED);

    private MotherMentorMotherBirthTest() {}

    public static boolean isRequired(Map<String, String> values, LocalDate assessment) {
        LocalDate dob = MotherMentorMotherChildUtils.birth(values);
        return MotherMentorMotherVisitUtils.hasFirstVisitAssessment(values)
                && dob != null && assessment != null && !assessment.isBefore(dob)
                && assessment.isBefore(dob.plusDays(56))
                && !MotherMentorMotherChildUtils.parseTests(values.get(MAMA_HEI_TEST_HISTORY))
                        .containsKey(Stage.AT_BIRTH);
    }

    public static LocalDate latestSampleDate(LocalDate dob, LocalDate assessment) {
        LocalDate end = dob.plusDays(2);
        return assessment.isBefore(end) ? assessment : end;
    }

    static boolean isComplete(Map<String, String> values, LocalDate assessment) {
        if (!isRequired(values, assessment) || "no".equals(values.get(MAMA_BIRTH_TEST_DONE))) return true;
        if (!"yes".equals(values.get(MAMA_BIRTH_TEST_DONE))) return false;
        LocalDate dob = MotherMentorMotherChildUtils.birth(values);
        LocalDate testDate = parseDate(values.get(MAMA_BIRTH_TEST_DATE));
        if (!MotherMentorHeiSchedule.validDate(testDate, dob, latestSampleDate(dob, assessment))) return false;
        String result = values.get(MAMA_BIRTH_TEST_RESULT);
        if ("pending".equals(result)) return true;
        if (!MotherMentorHeiSchedule.validDate(
                parseDate(values.get(MAMA_BIRTH_TEST_RECEIVED_DATE)), testDate, assessment)) return false;
        return "negative".equals(result) || ("positive".equals(result)
                && Arrays.asList("yes", "no").contains(values.get(MAMA_BIRTH_TEST_CONFIRMED)));
    }

    static void normalize(JSONObject form, Map<String, String> values, LocalDate assessment) {
        if (!isRequired(values, assessment)) {
            for (String key : ANSWERS) MotherMentorMotherChildUtils.put(form, key, "");
        } else if (!"yes".equals(values.get(MAMA_BIRTH_TEST_DONE))) {
            for (String key : ANSWERS.subList(1, ANSWERS.size())) {
                MotherMentorMotherChildUtils.put(form, key, "");
            }
        } else {
            String result = values.get(MAMA_BIRTH_TEST_RESULT);
            if (!"positive".equals(result)) MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_CONFIRMED, "");
            if ("pending".equals(result)) MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_RECEIVED_DATE, "");
        }
    }
}
