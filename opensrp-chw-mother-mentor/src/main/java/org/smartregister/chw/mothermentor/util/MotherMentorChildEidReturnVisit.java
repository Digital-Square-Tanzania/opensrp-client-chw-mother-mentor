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
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidDirectEnrollment.CONFIRMATION_REFERRED;

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
        MTOTO_PENDING_TEST, MTOTO_PENDING_TEST_DATE, MTOTO_RESULT_RECEIVED, MTOTO_RECEIVED_RESULT
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
            case REFERRED:
            case CONFIRMATION_REFERRED: return evaluateAttendance(values, dob, day, history, priorState);
            case AWAITING_RESULT:
            case AWAITING_CONFIRM: return evaluateReview(values, dob, day, history, priorState);
            default: return null;
        }
    }

    private static Result evaluateAttendance(Map<String, String> values, LocalDate dob, LocalDate day,
            Map<Stage, TestRecord> history, String priorState) {
        boolean confirmatorySample = CONFIRMATION_REFERRED.equals(priorState);
        String attendance = value(values, CHILD_EID_ARRIVED_AT_FACILITY);
        if ("no".equals(attendance)) {
            return validReason(values, MTOTO_SABABU_KUTOFIKA, MTOTO_SABABU_KUTOFIKA_OTHER, NON_ATTENDANCE)
                    ? keep(priorState) : null;
        }
        LocalDate arrival = date(values, CHILD_EID_FACILITY_ARRIVAL_DATE);
        if (!"yes".equals(attendance) || !MotherMentorHeiSchedule.validDate(arrival, dob, day)) return null;
        String tested = value(values, MAMA_HEI_TEST_DONE);
        if ("no".equals(tested)) {
            return validReason(values, MTOTO_SABABU_KUTOPIMWA, MTOTO_SABABU_KUTOPIMWA_OTHER, NOT_TESTED)
                    ? keep(priorState) : null;
        }
        Stage stage = Stage.fromCode(value(values, confirmatorySample ? MTOTO_RUFAA_TEST : CHILD_EID_TEST_TYPE));
        LocalDate testDate = date(values, MAMA_HEI_TEST_DATE);
        if (!"yes".equals(tested)
                || stage == null
                || !confirmatorySample && !MotherMentorChildEidHeiUtils.eligible(values, dob, history, day).contains(stage)
                || !MotherMentorHeiSchedule.validDate(testDate, arrival, day)) return null;
        if (confirmatorySample) {
            return resolveConfirmatorySample(stage, testDate, value(values, MAMA_HEI_TEST_RESULT));
        }
        if (!validSavedReceivedDate(values, testDate, day)) return null;
        return resolveInitialResult(stage, testDate, value(values, MAMA_HEI_TEST_RESULT), values, true);
    }

    private static Result resolveConfirmatorySample(Stage stage, LocalDate date, String result) {
        switch (result) {
            case "negative": return new Result("none", stage, date, result, Outcome.CONFIRMATORY_NEGATIVE);
            case "positive": return new Result("none", stage, date, result, Outcome.CONFIRMED_POSITIVE);
            case "pending": return stage == Stage.EIGHTEEN_MONTHS ? null
                    : new Result(AWAITING_CONFIRM, stage, date, "positive", Outcome.CONTINUE);
            default: return null;
        }
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
        if (confirming && !value(values, MTOTO_CONFIRMATORY_TEST_DONE).isEmpty()) {
            return resolveConfirmation(stage, date, values);
        }
        String received = value(values, confirming ? MTOTO_CONFIRMATION_RECEIVED : MTOTO_RESULT_RECEIVED);
        if ("no".equals(received)) return keep(priorState);
        if (!"yes".equals(received)) return null;
        if (confirming) {
            return confirmed(stage, date, value(values, MTOTO_CONFIRMATORY_RESULT));
        }
        if (!validSavedReceivedDate(values, date, day)) return null;
        return resolveInitialResult(stage, date, value(values, MTOTO_RECEIVED_RESULT), values, false);
    }

    public static boolean matchesPending(TestRecord record, String priorState) {
        if (record == null || record.getDate() == null) return false;
        if (AWAITING_RESULT.equals(priorState)) return "pending".equals(record.getResult());
        return AWAITING_CONFIRM.equals(priorState) && "positive".equals(record.getResult()) && record.requiresFollowUp();
    }

    /** A linked result updates an existing sample without replaying the visit's tracing answers. */
    public static Result evaluateStandalone(Map<String, String> values, LocalDate day) {
        Stage stage = Stage.fromCode(value(values, MTOTO_PENDING_TEST));
        LocalDate date = date(values, MTOTO_PENDING_TEST_DATE);
        if (stage == null || date == null || day == null || date.isAfter(day)) return null;
        String state = value(values, MTOTO_RUFAA_STATE_BEFORE);
        if (AWAITING_CONFIRM.equals(state)) {
            return validSavedReceivedDate(values, date, day) ? resolveConfirmation(stage, date, values) : null;
        }
        if (!AWAITING_RESULT.equals(state) || stage == Stage.EIGHTEEN_MONTHS
                || !MotherMentorHeiSchedule.validDate(date(values, MTOTO_RESULT_RECEIVED_DATE), date, day)) return null;
        String result = value(values, MTOTO_RECEIVED_RESULT);
        if ("negative".equals(result)) return negative(stage, date);
        return "positive".equals(result) ? resolveConfirmation(stage, date, values) : null;
    }

    private static Result resolveConfirmation(Stage stage, LocalDate date, Map<String, String> values) {
        String result = value(values, MTOTO_CONFIRMATORY_RESULT);
        MotherMentorHeiConfirmation.Outcome outcome = MotherMentorHeiConfirmation.resolve(
                stage, value(values, MTOTO_CONFIRMATORY_TEST_DONE), result);
        if (outcome == null) return null;
        return outcome == MotherMentorHeiConfirmation.Outcome.WAITING ? awaitingConfirmation(stage, date)
                : confirmed(stage, date, result);
    }

    private static Result awaitingConfirmation(Stage stage, LocalDate date) {
        return new Result(AWAITING_CONFIRM, stage, date, "positive", Outcome.CONTINUE);
    }

    private static Result confirmed(Stage stage, LocalDate date, String result) {
        if ("negative".equals(result)) return new Result("none", stage, date, result, Outcome.CONFIRMATORY_NEGATIVE);
        if ("positive".equals(result)) return new Result("none", stage, date, result, Outcome.CONFIRMED_POSITIVE);
        return null;
    }

    /** The result-availability snapshot used by existing report readers. */
    public static String confirmationReceived(Map<String, String> values) {
        String done = value(values, MTOTO_CONFIRMATORY_TEST_DONE);
        if (done.isEmpty()) return "";
        return "yes".equals(done) && availableResult(value(values, MTOTO_CONFIRMATORY_RESULT)) ? "yes" : "no";
    }

    public static boolean requiresReceivedDate(Map<String, String> values) {
        if (!applies(values)) return false;
        String state = value(values, MTOTO_RUFAA_STATE_BEFORE);
        if (REFERRED.equals(state) && "yes".equals(value(values, CHILD_EID_ARRIVED_AT_FACILITY))
                && "yes".equals(value(values, MAMA_HEI_TEST_DONE))) {
            return availableResult(value(values, MAMA_HEI_TEST_RESULT));
        }
        return AWAITING_RESULT.equals(state) && "yes".equals(value(values, MTOTO_RESULT_RECEIVED))
                && availableResult(value(values, MTOTO_RECEIVED_RESULT));
    }

    private static boolean availableResult(String result) {
        return "negative".equals(result) || "positive".equals(result);
    }

    static LocalDate receivedDateMinimum(Map<String, String> values) {
        return date(values, REFERRED.equals(value(values, MTOTO_RUFAA_STATE_BEFORE))
                ? MAMA_HEI_TEST_DATE : MTOTO_PENDING_TEST_DATE);
    }

    public static boolean validReceivedDate(Map<String, String> values, LocalDate day) {
        LocalDate received = date(values, MTOTO_RESULT_RECEIVED_DATE);
        return MotherMentorHeiSchedule.validDate(received, receivedDateMinimum(values), day)
                && !received.isAfter(LocalDate.now());
    }

    private static boolean validSavedReceivedDate(Map<String, String> values, LocalDate testDate, LocalDate day) {
        // Earlier versions saved results without recording when the facility received them.
        return value(values, MTOTO_RESULT_RECEIVED_DATE).isEmpty()
                || MotherMentorHeiSchedule.validDate(date(values, MTOTO_RESULT_RECEIVED_DATE), testDate, day);
    }

    private static Result resolveInitialResult(Stage stage, LocalDate date, String result,
            Map<String, String> values, boolean allowPending) {
        switch (result) {
            case "pending": return allowPending && stage != Stage.EIGHTEEN_MONTHS
                    ? new Result(AWAITING_RESULT, stage, date, result, Outcome.CONTINUE) : null;
            case "negative": return negative(stage, date);
            case "positive":
                if (!value(values, MTOTO_CONFIRMATORY_TEST_DONE).isEmpty()) {
                    return resolveConfirmation(stage, date, values);
                }
                // Historical visits used one question, where "yes" meant confirmed positive.
                String reported = value(values, MTOTO_CONFIRMATION_RECEIVED);
                if ("yes".equals(reported)) return confirmed(stage, date, "positive");
                if ("no".equals(reported)) return awaitingConfirmation(stage, date);
                return null;
            default: return null;
        }
    }

    private static Result negative(Stage stage, LocalDate date) {
        return new Result("none", stage, date, "negative", stage == Stage.EIGHTEEN_MONTHS
                ? Outcome.GRADUATED : Outcome.TESTED_NEGATIVE);
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
        boolean confirmatorySample = CONFIRMATION_REFERRED.equals(state);
        boolean referred = REFERRED.equals(state) || confirmatorySample;
        boolean awaitingResult = AWAITING_RESULT.equals(state);
        boolean awaitingConfirm = AWAITING_CONFIRM.equals(state);
        boolean attended = referred && "yes".equals(value(values, CHILD_EID_ARRIVED_AT_FACILITY));
        boolean tested = attended && "yes".equals(value(values, MAMA_HEI_TEST_DONE));
        boolean hasResult = awaitingResult && "yes".equals(value(values, MTOTO_RESULT_RECEIVED));
        clear(form, LEGACY_FIELDS);
        if (!referred) clear(form, ATTENDANCE_FIELDS);
        if (!tested) clear(form, SAMPLE_FIELDS);
        if (confirmatorySample) clear(form, CHILD_EID_TEST_TYPE);
        if (!awaitingResult && !awaitingConfirm) clear(form, REVIEW_FIELDS);
        if (!awaitingResult) clear(form, MTOTO_RESULT_RECEIVED);
        if (!tested) clear(form, MAMA_HEI_TEST_RESULT);
        if (!hasResult) clear(form, MTOTO_RECEIVED_RESULT);
        if (!requiresReceivedDate(values)) clear(form, MTOTO_RESULT_RECEIVED_DATE);
        boolean positive = tested && !confirmatorySample && "positive".equals(value(values, MAMA_HEI_TEST_RESULT))
                || hasResult && "positive".equals(value(values, MTOTO_RECEIVED_RESULT));
        if (!awaitingConfirm && !positive) {
            clear(form, MTOTO_CONFIRMATORY_TEST_DONE, MTOTO_CONFIRMATORY_RESULT, MTOTO_CONFIRMATION_RECEIVED);
        } else if (!"yes".equals(value(values, MTOTO_CONFIRMATORY_TEST_DONE))) {
            clear(form, MTOTO_CONFIRMATORY_RESULT);
        }
        if (!attended) clear(form, CHILD_EID_FACILITY_ARRIVAL_DATE, MAMA_HEI_TEST_DONE);
        if (!attended || !"no".equals(value(values, MAMA_HEI_TEST_DONE))) clear(form, MTOTO_SABABU_KUTOPIMWA);
        if (!referred || !"no".equals(value(values, CHILD_EID_ARRIVED_AT_FACILITY))) clear(form, MTOTO_SABABU_KUTOFIKA);
        Map<String, String> active = MotherMentorMotherChildUtils.values(form);
        if (!OTHER.equals(value(active, MTOTO_SABABU_KUTOFIKA))) clear(form, MTOTO_SABABU_KUTOFIKA_OTHER);
        if (!OTHER.equals(value(active, MTOTO_SABABU_KUTOPIMWA))) clear(form, MTOTO_SABABU_KUTOPIMWA_OTHER);
    }

    public static void clearSample(JSONObject form) {
        clear(form, MAMA_HEI_TEST_DATE, MAMA_HEI_TEST_RESULT, MTOTO_RESULT_RECEIVED_DATE,
                MTOTO_CONFIRMATORY_TEST_DONE, MTOTO_CONFIRMATORY_RESULT, MTOTO_CONFIRMATION_RECEIVED);
    }

    public static void restoreLegacyConfirmation(JSONObject form) {
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        // A separately recorded outcome establishes that the test was done; the old Yes/No alone is ambiguous.
        if (value(values, MTOTO_CONFIRMATORY_TEST_DONE).isEmpty()
                && "yes".equals(value(values, MTOTO_CONFIRMATION_RECEIVED))
                && availableResult(value(values, MTOTO_CONFIRMATORY_RESULT))) {
            MotherMentorMotherChildUtils.put(form, MTOTO_CONFIRMATORY_TEST_DONE, "yes");
        }
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
