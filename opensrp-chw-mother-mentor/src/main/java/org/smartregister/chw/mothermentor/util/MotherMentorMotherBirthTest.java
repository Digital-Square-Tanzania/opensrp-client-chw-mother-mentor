package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;
import static org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.hasReceivedResult;
import static org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.parseDate;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** The birth assessment is asked once and is independent of routine tests. */
public final class MotherMentorMotherBirthTest {
    static final List<String> ANSWERS = Arrays.asList(MAMA_BIRTH_TEST_DONE, MAMA_BIRTH_TEST_DATE,
            MAMA_BIRTH_TEST_RESULT, MAMA_BIRTH_TEST_RECEIVED_DATE, MAMA_BIRTH_TEST_CONFIRMED,
            MAMA_BIRTH_TEST_CONFIRMATION_DONE, MAMA_BIRTH_TEST_CONFIRMATION_RESULT);
    private static final List<String> CONTEXT = Arrays.asList(MAMA_BIRTH_TEST_WORKFLOW,
            MAMA_BIRTH_TEST_ANSWERED_DOB, MAMA_BIRTH_TEST_REQUIRED,
            "mama_birth_test_window_note", "mama_birth_test_pending_note");

    private MotherMentorMotherBirthTest() {}

    static boolean isField(String key) {
        return ANSWERS.contains(key) || CONTEXT.contains(key);
    }

    static List<String> dependentAnswers() {
        return ANSWERS.subList(1, ANSWERS.size());
    }

    public static boolean isRequired(Map<String, String> values, LocalDate assessment) {
        LocalDate dob = MotherMentorMotherChildUtils.birth(values);
        return isApplicableVisit(values)
                && dob != null && assessment != null && !assessment.isBefore(dob)
                && assessment.isBefore(dob.plusDays(56))
                && !dob.equals(parseDate(values.get(MAMA_BIRTH_TEST_ANSWERED_DOB)))
                && !MotherMentorMotherChildUtils.parseTests(values.get(MAMA_HEI_TEST_HISTORY))
                        .containsKey(Stage.AT_BIRTH);
    }

    private static boolean isApplicableVisit(Map<String, String> values) {
        // The preceding Mpya release stored these answers before the Marudio workflow existed.
        return MotherMentorMotherVisitUtils.hasFirstVisitAssessment(values)
                || ("true".equals(values.get(MAMA_BIRTH_TEST_WORKFLOW))
                        && MotherMentorMotherChildUtils.applies(values));
    }

    public static LocalDate latestSampleDate(LocalDate dob, LocalDate assessment) {
        LocalDate end = dob.plusDays(2);
        return assessment.isBefore(end) ? assessment : end;
    }

    public static boolean isComplete(Map<String, String> values, LocalDate assessment) {
        if (!isRequired(values, assessment) || "no".equals(values.get(MAMA_BIRTH_TEST_DONE))) return true;
        if (!"yes".equals(values.get(MAMA_BIRTH_TEST_DONE))) return false;
        LocalDate dob = MotherMentorMotherChildUtils.birth(values);
        LocalDate testDate = parseDate(values.get(MAMA_BIRTH_TEST_DATE));
        if (!MotherMentorHeiSchedule.validDate(testDate, dob, latestSampleDate(dob, assessment))) return false;
        String result = values.get(MAMA_BIRTH_TEST_RESULT);
        if ("pending".equals(result)) return true;
        if (!MotherMentorHeiSchedule.validDate(
                parseDate(values.get(MAMA_BIRTH_TEST_RECEIVED_DATE)), testDate, assessment)) return false;
        return "negative".equals(result) || ("positive".equals(result) && confirmationComplete(values));
    }

    private static boolean confirmationComplete(Map<String, String> values) {
        if (!"true".equals(values.get(MAMA_BIRTH_TEST_WORKFLOW))) {
            return Arrays.asList("yes", "no").contains(values.get(MAMA_BIRTH_TEST_CONFIRMED));
        }
        return MotherMentorHeiConfirmation.resolve(Stage.AT_BIRTH, values.get(MAMA_BIRTH_TEST_CONFIRMATION_DONE),
                values.get(MAMA_BIRTH_TEST_CONFIRMATION_RESULT)) != null;
    }

    /** A null confirmation result means that the confirmatory result still needs follow-up. */
    public static String confirmationResult(Map<String, String> values) {
        if (!"positive".equals(values.get(MAMA_BIRTH_TEST_RESULT))) return null;
        if (!"true".equals(values.get(MAMA_BIRTH_TEST_WORKFLOW))) {
            return "yes".equals(values.get(MAMA_BIRTH_TEST_CONFIRMED)) ? "positive" : null;
        }
        return "yes".equals(values.get(MAMA_BIRTH_TEST_CONFIRMATION_DONE))
                ? values.get(MAMA_BIRTH_TEST_CONFIRMATION_RESULT) : null;
    }

    static void normalize(JSONObject form, Map<String, String> values, LocalDate assessment) {
        if (!isRequired(values, assessment)) {
            for (String key : ANSWERS) MotherMentorMotherChildUtils.put(form, key, "");
        } else if (!"yes".equals(values.get(MAMA_BIRTH_TEST_DONE))) {
            for (String key : dependentAnswers()) {
                MotherMentorMotherChildUtils.put(form, key, "");
            }
        } else {
            String result = values.get(MAMA_BIRTH_TEST_RESULT);
            LocalDate dob = MotherMentorMotherChildUtils.birth(values);
            LocalDate sample = parseDate(values.get(MAMA_BIRTH_TEST_DATE));
            if (!MotherMentorHeiSchedule.validDate(sample, dob, latestSampleDate(dob, assessment))) {
                MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_DATE, "");
                MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_RECEIVED_DATE, "");
            }
            if (!"positive".equals(result)) {
                MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_CONFIRMATION_DONE, "");
                MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_CONFIRMED, "");
            }
            if (!"positive".equals(result) || !"yes".equals(values.get(MAMA_BIRTH_TEST_CONFIRMATION_DONE))) {
                MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_CONFIRMATION_RESULT, "");
            }
            if (!hasReceivedResult(result)) MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_RECEIVED_DATE, "");
        }
    }

    public static void prepare(JSONObject form, Map<String, String> values, LocalDate assessment) {
        boolean required = isRequired(values, assessment);
        MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_REQUIRED, Boolean.toString(required));
        if (!required) return;
        LocalDate dob = MotherMentorMotherChildUtils.birth(values);
        if (values.getOrDefault(MAMA_BIRTH_TEST_CONFIRMATION_DONE, "").isEmpty()
                && Arrays.asList("yes", "no").contains(values.get(MAMA_BIRTH_TEST_CONFIRMED))) {
            String confirmed = values.get(MAMA_BIRTH_TEST_CONFIRMED);
            MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_CONFIRMATION_DONE, confirmed);
            if ("yes".equals(confirmed)) {
                MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_CONFIRMATION_RESULT, "positive");
            }
            MotherMentorMotherChildUtils.put(form, MAMA_BIRTH_TEST_CONFIRMED, "");
        }
        setDateBounds(form, MAMA_BIRTH_TEST_DATE, dob, latestSampleDate(dob, assessment));
        LocalDate sample = parseDate(values.get(MAMA_BIRTH_TEST_DATE));
        setDateBounds(form, MAMA_BIRTH_TEST_RECEIVED_DATE,
                MotherMentorHeiSchedule.validDate(sample, dob, latestSampleDate(dob, assessment)) ? sample : dob,
                assessment);
    }

    private static void setDateBounds(JSONObject form, String key, LocalDate min, LocalDate max) {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        if (field == null) return;
        try {
            field.put("min_date", min.toString("dd-MM-yyyy"));
            field.put("max_date", max.toString("dd-MM-yyyy"));
        } catch (org.json.JSONException ignored) {
            // The fields use fixed JSON property names.
        }
    }
}
