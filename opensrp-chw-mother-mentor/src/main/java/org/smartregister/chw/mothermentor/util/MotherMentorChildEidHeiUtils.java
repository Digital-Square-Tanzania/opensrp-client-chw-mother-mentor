package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Child EID HEI assessment; referral and testing are separate visits. */
public final class MotherMentorChildEidHeiUtils {
    public static final List<String> RETURN_FIELDS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.CHILD_EID_TEST_TYPE,
            DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY,
            DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE,
            DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE,
            DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT,
            DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE));

    private MotherMentorChildEidHeiUtils() {}

    public static boolean isBreastfeedingComplete(Map<String, String> values,
            LocalDate dob, LocalDate day) {
        if (values == null || dob == null || day == null || dob.isAfter(day)
                || day.isAfter(LocalDate.now())) return false;
        String status = value(values, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS);
        return "breastfeeding".equals(status)
                ? value(values, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA).isEmpty()
                : "not_breastfeeding".equals(status)
                    && MotherMentorHeiSchedule.validDate(cessation(values), dob, day);
    }

    public static List<Stage> eligible(Map<String, String> values, LocalDate dob,
            Map<Stage, TestRecord> tests, LocalDate day) {
        List<Stage> stages = new ArrayList<>(MotherMentorHeiSchedule.eligibleForSampling(dob, cessation(values), tests, day));
        stages.removeAll(MotherMentorChildEidRepository.suppressedTests(value(values, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS)));
        return stages;
    }

    public static List<Stage> reviewable(Map<String, String> values, LocalDate dob,
            Map<Stage, TestRecord> tests, LocalDate day) {
        return MotherMentorHeiSchedule.due(dob, cessation(values), tests, day);
    }

    public static boolean isComplete(Map<String, String> values, LocalDate dob, LocalDate day,
            Map<Stage, TestRecord> tests, String previousReferralState) {
        if (!isBreastfeedingComplete(values, dob, day)
                || !isReferralState(previousReferralState)
                || !previousReferralState.equals(value(values,
                        DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE))) return false;
        if ("none".equals(previousReferralState)) {
            if (hasAny(values, RETURN_FIELDS)) return false;
            List<Stage> eligible = eligible(values, dob, tests, day);
            if (eligible.isEmpty()) {
                return value(values, DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT).isEmpty()
                        && value(values, DBConstants.KEY.MTOTO_RUFAA_TEST).isEmpty()
                        && value(values, DBConstants.KEY.MTOTO_RUFAA_DUE).isEmpty();
            }
            String agreement = value(values, DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT);
            Stage first = eligible.get(0);
            LocalDate due = MotherMentorHeiSchedule.dueDate(first, dob, cessation(values));
            return ("agreed".equals(agreement) || "refused".equals(agreement))
                    && first == Stage.fromCode(value(values, DBConstants.KEY.MTOTO_RUFAA_TEST))
                    && due != null && due.toString().equals(value(values, DBConstants.KEY.MTOTO_RUFAA_DUE));
        }
        if (!Arrays.asList("referred", "awaiting_result", "awaiting_confirm")
                .contains(previousReferralState)
                || !value(values, DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT).isEmpty()) return false;
        if (MotherMentorChildEidReturnVisit.applies(values)) {
            return MotherMentorChildEidReturnVisit.evaluate(values, dob, day, tests, previousReferralState) != null;
        }
        List<Stage> reviewable = reviewable(values, dob, tests, day);
        if (reviewable.isEmpty()) return !hasAny(values, RETURN_FIELDS);
        Stage selected = Stage.fromCode(value(values, DBConstants.KEY.CHILD_EID_TEST_TYPE));
        if (!reviewable.contains(selected)) return false;
        String arrived = value(values, DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY);
        if ("no".equals(arrived)) {
            return value(values, DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE).isEmpty()
                    && noFinalAnswers(values);
        }
        if (!"yes".equals(arrived) || !MotherMentorHeiSchedule.validDate(
                MotherMentorHeiSchedule.parseDate(value(values,
                        DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE)), dob, day)) return false;
        if (selected != Stage.EIGHTEEN_MONTHS) return noFinalAnswers(values);
        String done = value(values, DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE);
        if ("no".equals(done)) {
            return value(values, DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT).isEmpty()
                    && value(values, DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE).isEmpty();
        }
        LocalDate testDate = MotherMentorHeiSchedule.parseDate(value(values,
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE));
        return "yes".equals(done)
                && Arrays.asList("negative", "positive", "pending").contains(
                        value(values, DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT))
                && MotherMentorHeiSchedule.validDate(testDate, dob, day)
                && !testDate.isBefore(dob.plusMonths(18));
    }

    public static void clearInactiveAnswers(JSONObject form) {
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        if (!"not_breastfeeding".equals(value(values, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))) {
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, "");
        }
        if ("none".equals(value(values, DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE))) {
            for (String key : RETURN_FIELDS) MotherMentorMotherChildUtils.put(form, key, "");
            MotherMentorChildEidReturnVisit.clearInactive(form, values);
            return;
        }
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT, "");
        if (MotherMentorChildEidReturnVisit.applies(values)) {
            MotherMentorChildEidReturnVisit.clearInactive(form, values);
            return;
        }
        if (!"yes".equals(value(values, DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY))) {
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE, "");
        }
        if (!"yes".equals(value(values, DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY))
                || !"eighteen_months".equals(value(values, DBConstants.KEY.CHILD_EID_TEST_TYPE))) {
            for (String key : Arrays.asList(DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE,
                    DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT,
                    DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE)) {
                MotherMentorMotherChildUtils.put(form, key, "");
            }
        } else if (!"yes".equals(value(values, DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DONE))) {
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_EID_FINAL_HIV_RESULT, "");
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE, "");
        }
    }

    public static LocalDate cessation(Map<String, String> values) {
        return "not_breastfeeding".equals(value(values, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))
                ? MotherMentorHeiSchedule.parseDate(value(values,
                        DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)) : null;
    }

    public static boolean isReferralState(String state) {
        return Arrays.asList("none", "referred", "awaiting_result", "awaiting_confirm").contains(state);
    }

    private static boolean noFinalAnswers(Map<String, String> values) {
        return !hasAny(values, RETURN_FIELDS.subList(3, RETURN_FIELDS.size()));
    }

    private static boolean hasAny(Map<String, String> values, List<String> keys) {
        for (String key : keys) if (!value(values, key).isEmpty()) return true;
        return false;
    }

    private static String value(Map<String, String> values, String key) {
        return values == null || values.get(key) == null ? "" : values.get(key).trim();
    }
}
