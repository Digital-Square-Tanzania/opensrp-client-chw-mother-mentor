package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

/** Validates and resolves one saved Child EID return visit against its preceding evidence. */
public final class MotherMentorChildEidReturnVisit {
    public static final String REFERRED = "referred";
    public static final String AWAITING_RESULT = "awaiting_result";
    public static final String AWAITING_CONFIRM = "awaiting_confirm";
    private static final List<String> NON_ATTENDANCE = Arrays.asList(
            "forgot", "travelled", "no_fare", "overcrowding", "stigma", OTHER);
    private static final List<String> NOT_TESTED = Arrays.asList("stockout", "return_later", OTHER);
    private static final String[] ATTENDANCE_FIELDS = {
        CHILD_EID_ARRIVED_AT_FACILITY, CHILD_EID_FACILITY_ARRIVAL_DATE, MTOTO_SABABU_KUTOFIKA,
        MTOTO_SABABU_KUTOFIKA_OTHER, MAMA_HEI_TEST_DONE, MTOTO_SABABU_KUTOPIMWA, MTOTO_SABABU_KUTOPIMWA_OTHER
    };
    private static final String[] SAMPLE_FIELDS = {CHILD_EID_TEST_TYPE, MAMA_HEI_TEST_DATE};
    private static final String[] REVIEW_FIELDS = {
        MTOTO_PENDING_TEST, MTOTO_PENDING_TEST_DATE, MTOTO_RESULT_RECEIVED, MTOTO_RECEIVED_RESULT, MTOTO_CONFIRMATORY_RESULT
    };
    private static final String[] LEGACY_FIELDS = {
        CHILD_EID_FINAL_HIV_TEST_DONE, CHILD_EID_FINAL_HIV_TEST_DATE, CHILD_EID_FINAL_HIV_RESULT
    };

    private MotherMentorChildEidReturnVisit() {}

    public enum Outcome { CONTINUE, TESTED_NEGATIVE, CONFIRMATORY_NEGATIVE, GRADUATED, CONFIRMED_POSITIVE }

    public static final class Result {
        public final String nextState;
        public final Stage stage;
        public final LocalDate testDate;
        public final String result;
        public final Outcome outcome;

        private Result(String nextState, Stage stage, LocalDate testDate, String result, Outcome outcome) {
            this.nextState = nextState;
            this.stage = stage;
            this.testDate = testDate;
            this.result = result;
            this.outcome = outcome;
        }
    }

    public static boolean applies(Map<String, String> values) {
        return "1".equals(value(values, MTOTO_RETURN_FLOW));
    }

    public static Result evaluate(Map<String, String> values, LocalDate dob, LocalDate day,
            Map<Stage, TestRecord> history, String priorState) {
        if (!applies(values) || !value(values, MTOTO_RUFAA_STATE_BEFORE).equals(priorState)
                || !MotherMentorChildEidHeiUtils.isBreastfeedingComplete(values, dob, day)) return null;
        switch (priorState) {
            case REFERRED: return evaluateAttendance(values, dob, day, history);
            case AWAITING_RESULT:
            case AWAITING_CONFIRM: return evaluateReview(values, dob, day, history, priorState);
            default: return null;
        }
    }

    private static Result evaluateAttendance(Map<String, String> values, LocalDate dob, LocalDate day,
            Map<Stage, TestRecord> history) {
        String attendance = value(values, CHILD_EID_ARRIVED_AT_FACILITY);
        if ("no".equals(attendance)) {
            return validReason(values, MTOTO_SABABU_KUTOFIKA, MTOTO_SABABU_KUTOFIKA_OTHER, NON_ATTENDANCE)
                    ? keep(REFERRED) : null;
        }
        LocalDate arrival = date(values, CHILD_EID_FACILITY_ARRIVAL_DATE);
        if (!"yes".equals(attendance) || !MotherMentorHeiSchedule.validDate(arrival, dob, day)) return null;
        String tested = value(values, MAMA_HEI_TEST_DONE);
        if ("no".equals(tested)) {
            return validReason(values, MTOTO_SABABU_KUTOPIMWA, MTOTO_SABABU_KUTOPIMWA_OTHER, NOT_TESTED)
                    ? keep(REFERRED) : null;
        }
        Stage stage = Stage.fromCode(value(values, CHILD_EID_TEST_TYPE));
        LocalDate testDate = date(values, MAMA_HEI_TEST_DATE);
        if (!"yes".equals(tested)
                || !MotherMentorChildEidHeiUtils.eligible(values, dob, history, day).contains(stage)
                || !MotherMentorHeiSchedule.validDate(testDate, arrival, day)) return null;
        return resolveResult(stage, testDate, value(values, MAMA_HEI_TEST_RESULT),
                value(values, MTOTO_CONFIRMATION_RECEIVED), true);
    }

    private static Result evaluateReview(Map<String, String> values, LocalDate dob, LocalDate day,
            Map<Stage, TestRecord> history, String priorState) {
        Stage stage = Stage.fromCode(value(values, MTOTO_PENDING_TEST));
        TestRecord original = history == null ? null : history.get(stage);
        LocalDate date = date(values, MTOTO_PENDING_TEST_DATE);
        if (!matchesPending(original, priorState)
                || !MotherMentorHeiSchedule.validDate(date, dob, day)
                || !date.equals(original.getDate())) return null;
        boolean confirming = AWAITING_CONFIRM.equals(priorState);
        String received = value(values, confirming ? MTOTO_CONFIRMATION_RECEIVED : MTOTO_RESULT_RECEIVED);
        if ("no".equals(received)) return keep(priorState);
        if (!"yes".equals(received)) return null;
        if (confirming) {
            String result = value(values, MTOTO_CONFIRMATORY_RESULT);
            if ("negative".equals(result)) return new Result("none", stage, date, result, Outcome.CONFIRMATORY_NEGATIVE);
            if ("positive".equals(result)) return new Result("none", stage, date, result, Outcome.CONFIRMED_POSITIVE);
            return null;
        }
        return resolveResult(stage, date, value(values, MTOTO_RECEIVED_RESULT),
                value(values, MTOTO_CONFIRMATION_RECEIVED), false);
    }

    public static boolean matchesPending(TestRecord record, String priorState) {
        if (record == null || record.getDate() == null) return false;
        if (AWAITING_RESULT.equals(priorState)) return "pending".equals(record.getResult());
        return AWAITING_CONFIRM.equals(priorState) && "positive".equals(record.getResult()) && !record.isConfirmed();
    }

    private static Result resolveResult(Stage stage, LocalDate date, String result,
            String confirmation, boolean allowPending) {
        switch (result) {
            case "pending": return allowPending
                    ? new Result(AWAITING_RESULT, stage, date, result, Outcome.CONTINUE) : null;
            case "negative": return new Result("none", stage, date, result, stage == Stage.EIGHTEEN_MONTHS
                    ? Outcome.GRADUATED : Outcome.TESTED_NEGATIVE);
            case "positive":
                if ("yes".equals(confirmation)) return new Result("none", stage, date, result, Outcome.CONFIRMED_POSITIVE);
                if ("no".equals(confirmation)) return new Result(AWAITING_CONFIRM, stage, date, result, Outcome.CONTINUE);
                return null;
            default: return null;
        }
    }

    private static Result keep(String state) {
        return new Result(state, null, null, "", Outcome.CONTINUE);
    }

    private static boolean validReason(Map<String, String> values, String field, String other, List<String> options) {
        String reason = value(values, field);
        return options.contains(reason) && (!OTHER.equals(reason) || !value(values, other).isEmpty());
    }

    public static void clearInactive(JSONObject form, Map<String, String> values) {
        String state = value(values, MTOTO_RUFAA_STATE_BEFORE);
        boolean referred = REFERRED.equals(state);
        boolean awaitingResult = AWAITING_RESULT.equals(state);
        boolean awaitingConfirm = AWAITING_CONFIRM.equals(state);
        boolean attended = referred && "yes".equals(value(values, CHILD_EID_ARRIVED_AT_FACILITY));
        boolean tested = attended && "yes".equals(value(values, MAMA_HEI_TEST_DONE));
        boolean hasResult = awaitingResult && "yes".equals(value(values, MTOTO_RESULT_RECEIVED));
        clear(form, LEGACY_FIELDS);
        if (!referred) clear(form, ATTENDANCE_FIELDS);
        if (!tested) clear(form, SAMPLE_FIELDS);
        if (!awaitingResult && !awaitingConfirm) clear(form, REVIEW_FIELDS);
        if (!awaitingResult) clear(form, MTOTO_RESULT_RECEIVED);
        if (!awaitingConfirm || !"yes".equals(value(values, MTOTO_CONFIRMATION_RECEIVED))) {
            clear(form, MTOTO_CONFIRMATORY_RESULT);
        }
        if (!tested) clear(form, MAMA_HEI_TEST_RESULT);
        if (!hasResult) clear(form, MTOTO_RECEIVED_RESULT);
        boolean positive = tested && "positive".equals(value(values, MAMA_HEI_TEST_RESULT))
                || hasResult && "positive".equals(value(values, MTOTO_RECEIVED_RESULT));
        if (!awaitingConfirm && !positive) {
            clear(form, MTOTO_CONFIRMATION_RECEIVED);
        }
        if (!attended) clear(form, CHILD_EID_FACILITY_ARRIVAL_DATE, MAMA_HEI_TEST_DONE);
        if (!attended || !"no".equals(value(values, MAMA_HEI_TEST_DONE))) clear(form, MTOTO_SABABU_KUTOPIMWA);
        if (!referred || !"no".equals(value(values, CHILD_EID_ARRIVED_AT_FACILITY))) clear(form, MTOTO_SABABU_KUTOFIKA);
        Map<String, String> active = MotherMentorMotherChildUtils.values(form);
        if (!OTHER.equals(value(active, MTOTO_SABABU_KUTOFIKA))) clear(form, MTOTO_SABABU_KUTOFIKA_OTHER);
        if (!OTHER.equals(value(active, MTOTO_SABABU_KUTOPIMWA))) clear(form, MTOTO_SABABU_KUTOPIMWA_OTHER);
    }

    public static void clearSample(JSONObject form) {
        clear(form, MAMA_HEI_TEST_DATE, MAMA_HEI_TEST_RESULT, MTOTO_CONFIRMATION_RECEIVED);
    }

    private static void clear(JSONObject form, String... keys) {
        for (String key : keys) MotherMentorMotherChildUtils.put(form, key, "");
    }

    private static LocalDate date(Map<String, String> values, String key) {
        return MotherMentorHeiSchedule.parseDate(value(values, key));
    }

    private static String value(Map<String, String> values, String key) {
        return values == null ? "" : Objects.toString(values.get(key), "").trim();
    }
}
