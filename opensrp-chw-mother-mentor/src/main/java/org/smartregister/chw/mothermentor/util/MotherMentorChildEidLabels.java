package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.smartregister.chw.mothermentor.R;

import java.util.Map;

/** Localized child-card and register text; stored clinical codes are never displayed as labels. */
public final class MotherMentorChildEidLabels {
    private MotherMentorChildEidLabels() {}

    public static String age(Context context, String dob, LocalDate day) {
        LocalDate birth = MotherMentorHeiSchedule.parseBirthDate(dob);
        if (birth == null || day == null || birth.isAfter(day)) return "";
        int days = Days.daysBetween(birth, day).getDays();
        if (days == 0) return context.getString(R.string.mothermentor_eid_newborn);
        if (days < 7) return context.getResources().getQuantityString(
                R.plurals.mothermentor_age_days, days, days);
        int weeks = days / 7;
        if (weeks < 9) return context.getResources().getQuantityString(
                R.plurals.mothermentor_eid_age_weeks, weeks, weeks);
        int months = Months.monthsBetween(birth, day).getMonths();
        return context.getResources().getQuantityString(
                R.plurals.mothermentor_age_months, months, months);
    }

    public static String test(Context context, String code) {
        MotherMentorHeiSchedule.Stage stage = MotherMentorHeiSchedule.Stage.fromCode(code);
        if (stage == null) return context.getString(R.string.mothermentor_eid_not_recorded);
        switch (stage) {
            case AT_BIRTH: return context.getString(R.string.mothermentor_eid_direct_at_birth);
            case SIX_WEEKS: return context.getString(R.string.mothermentor_eid_test_six_weeks);
            case NINE_MONTHS: return context.getString(R.string.mothermentor_eid_test_nine_months);
            case AFTER_WEANING: return context.getString(R.string.mothermentor_eid_test_after_weaning);
            case EIGHTEEN_MONTHS: return context.getString(R.string.mothermentor_eid_test_eighteen_months);
            default: return context.getString(R.string.mothermentor_eid_not_recorded);
        }
    }

    public static String pill(Context context, String state, boolean hasMissedTest) {
        if (MotherMentorChildEidDirectEnrollment.CONFIRMATION_NEEDED.equals(state)) {
            return context.getString(R.string.mothermentor_eid_direct_confirmation_needed);
        }
        if (MotherMentorChildEidDirectEnrollment.CONFIRMATION_REFERRED.equals(state)) {
            return context.getString(R.string.mothermentor_eid_direct_confirmation_referred);
        }
        if ("referred".equals(state)) return context.getString(R.string.mothermentor_eid_referred);
        if ("awaiting_result".equals(state)) return context.getString(R.string.mothermentor_eid_result_pending);
        if ("awaiting_confirm".equals(state)) return context.getString(R.string.mothermentor_eid_awaiting_confirmation);
        return context.getString(hasMissedTest ? R.string.mothermentor_eid_missed_test
                : R.string.mothermentor_eid_title);
    }

    public static String referral(Context context, String state, String test) {
        int label;
        if (MotherMentorChildEidResultOverdue.DISPLAY_STATE.equals(state)) {
            return context.getString(R.string.mothermentor_eid_result_overdue);
        }
        if ("none".equals(state)) return context.getString(R.string.mothermentor_eid_to_refer);
        if (MotherMentorChildEidDirectEnrollment.CONFIRMATION_NEEDED.equals(state)
                || MotherMentorChildEidDirectEnrollment.CONFIRMATION_REFERRED.equals(state)) {
            return pill(context, state, false);
        }
        if ("referred".equals(state)) label = R.string.mothermentor_eid_referred;
        else if ("awaiting_result".equals(state)) label = R.string.mothermentor_eid_result_pending;
        else if ("awaiting_confirm".equals(state)) return context.getString(R.string.mothermentor_eid_awaiting_confirmation);
        else return context.getString(R.string.mothermentor_eid_not_recorded);
        return MotherMentorHeiSchedule.Stage.fromCode(test) == null
                ? context.getString(label)
                : context.getString(R.string.mothermentor_eid_state_test,
                        context.getString(label), test(context, test));
    }

    public static String missed(Context context, String test, String due) {
        LocalDate date = MotherMentorHeiSchedule.parseDate(due);
        return date == null || MotherMentorHeiSchedule.Stage.fromCode(test) == null ? ""
                : context.getString(R.string.mothermentor_eid_missed_due,
                        test(context, test), MotherMentorMotherPregnancyUtils.displayDate(date));
    }

    public static String entryReason(Context context, String test, String due) {
        LocalDate date = MotherMentorHeiSchedule.parseDate(due);
        return date == null || MotherMentorHeiSchedule.Stage.fromCode(test) == null ? ""
                : context.getString(R.string.mothermentor_eid_entry_reason,
                        test(context, test), MotherMentorMotherPregnancyUtils.displayDate(date));
    }

    public static String entryReason(Context context, Map<String, String> values, String missed, String due) {
        if (MotherMentorChildEidDirectEnrollment.SOURCE.equals(values.get(DBConstants.KEY.MTOTO_ENTRY_SOURCE))) {
            String test = test(context, values.get(DBConstants.KEY.MTOTO_ENTRY_TEST));
            String result = values.get(DBConstants.KEY.MTOTO_ENTRY_RESULT);
            if ("positive".equals(result)) return context.getString(R.string.mothermentor_eid_direct_entry_positive, test);
            if ("pending".equals(result)) return context.getString(R.string.mothermentor_eid_direct_entry_pending, test);
        }
        return entryReason(context, missed, due);
    }

    public static String summary(Context context, String mother, String heiNumber, int visits) {
        StringBuilder text = new StringBuilder();
        if (mother != null && !mother.trim().isEmpty()) append(text,
                context.getString(R.string.mothermentor_eid_mother_name, mother.trim()));
        if (heiNumber != null && !heiNumber.trim().isEmpty()) append(text, heiNumber.trim());
        if (visits >= 0) append(text, context.getString(R.string.mothermentor_eid_visit_number, visits));
        return text.toString();
    }

    public static String cardSummary(Context context, String mother, String village, String heiNumber) {
        StringBuilder text = new StringBuilder();
        if (mother != null && !mother.trim().isEmpty()) append(text,
                context.getString(R.string.mothermentor_eid_mother_name, mother.trim()));
        if (village != null && !village.trim().isEmpty()) append(text, village.trim());
        if (heiNumber != null && !heiNumber.trim().isEmpty()) append(text, heiNumber.trim());
        return text.toString();
    }

    public static String registerSummary(Context context, Map<String, String> values, int visits) {
        StringBuilder summary = new StringBuilder();
        appendContact(context, values, summary);
        String hei = values.get(DBConstants.KEY.HEI_NUMBER);
        if (hei != null && !hei.trim().isEmpty()) append(summary, hei.trim());
        if (visits >= 0) append(summary, context.getString(R.string.mothermentor_eid_visit_number, visits));
        return summary.toString();
    }

    public static String cardSummary(Context context, Map<String, String> values) {
        StringBuilder summary = new StringBuilder();
        appendContact(context, values, summary);
        for (String key : new String[] {DBConstants.KEY.VILLAGE_TOWN, DBConstants.KEY.HEI_NUMBER}) {
            String text = values.get(key);
            if (text != null && !text.trim().isEmpty()) append(summary, text.trim());
        }
        return summary.toString();
    }

    private static void appendContact(Context context, Map<String, String> values, StringBuilder summary) {
        if (MotherMentorChildEidDirectEnrollment.SOURCE.equals(values.get(DBConstants.KEY.MTOTO_ENTRY_SOURCE))) {
            String caregiver = values.get(DBConstants.KEY.MTOTO_CAREGIVER_NAME);
            int relationship = MotherMentorChildEidDirectEnrollment.relationshipLabel(
                    values.get(DBConstants.KEY.MTOTO_CAREGIVER_RELATIONSHIP));
            if (caregiver != null && !caregiver.trim().isEmpty() && relationship != 0) {
                append(summary, context.getString(R.string.mothermentor_eid_direct_caregiver_display,
                        caregiver.trim(), context.getString(relationship)));
            }
        } else {
            String mother = values.get(DBConstants.KEY.MOTHER_NAME);
            if (mother != null && !mother.trim().isEmpty()) {
                append(summary, context.getString(R.string.mothermentor_eid_mother_name, mother.trim()));
            }
        }
    }

    public static String lastTest(Context context,
            Map<MotherMentorHeiSchedule.Stage, MotherMentorHeiSchedule.TestRecord> history) {
        MotherMentorHeiSchedule.Stage last = null;
        LocalDate observed = null;
        if (history == null) return "";
        for (Map.Entry<MotherMentorHeiSchedule.Stage, MotherMentorHeiSchedule.TestRecord> entry : history.entrySet()) {
            MotherMentorHeiSchedule.TestRecord record = entry.getValue();
            // Observation date orders an undated "last test" report; it is never displayed as a test date.
            LocalDate day = record.getDate() == null ? record.getServiceDate() : record.getDate();
            if (last == null || day != null && (observed == null || !day.isBefore(observed))) {
                last = entry.getKey();
                observed = day;
            }
        }
        if (last == null) return "";
        String result = history.get(last).getResult();
        int label = R.string.mothermentor_eid_not_recorded;
        if ("negative".equals(result)) label = R.string.mothermentor_eid_negative;
        else if ("positive".equals(result)) label = R.string.mothermentor_eid_positive;
        else if ("pending".equals(result)) label = R.string.mothermentor_eid_result_pending;
        return context.getString(R.string.mothermentor_eid_last_test,
                test(context, last.getCode()), context.getString(label));
    }

    private static void append(StringBuilder text, String value) {
        if (text.length() > 0) text.append(" · ");
        text.append(value);
    }

    public static boolean isSuccessfulClose(String status) {
        MotherMentorChildEidExit exit = MotherMentorChildEidExit.fromStatus(status);
        return exit != null && exit.isSuccess();
    }

    public static String closedReason(Context context, String status) {
        int label = R.string.mothermentor_eid_closed_message;
        if ("tested_negative".equals(status)) label = R.string.mothermentor_eid_exit_tested;
        else if ("confirmatory_negative".equals(status)) label = R.string.mothermentor_eid_exit_confirm_negative;
        else if ("nonattendance_3".equals(status)) label = R.string.mothermentor_eid_exit_nonattendance;
        else if ("deceased".equals(status) || "child_deceased".equals(status)) label = R.string.mothermentor_eid_child_deceased;
        else if ("moved_area".equals(status)) label = R.string.mothermentor_eid_moved_area;
        else if ("moved_facility".equals(status)) label = R.string.mothermentor_eid_moved_facility;
        else if ("not_found_other_3".equals(status)) label = R.string.mothermentor_eid_not_found_three;
        else if ("refused_3".equals(status)) label = R.string.mothermentor_eid_refused_three;
        else if ("graduated".equals(status)) label = R.string.mothermentor_eid_graduated;
        else if ("confirmed_positive".equals(status) || "child_confirmed_positive".equals(status)) label = R.string.mothermentor_eid_confirmed_positive;
        return context.getString(label);
    }
}
