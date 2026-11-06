package org.smartregister.chw.mothermentor.util;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.vijay.jsonwizard.activities.JsonFormActivity;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded adapter for the HEI banner and existing native return-test controls. */
public final class MotherMentorChildEidHeiUi {
    private Map<String, String> previous;
    private boolean refreshing;

    public void refresh(JsonFormActivity activity) {
        if (refreshing || activity.getmJSONObject() == null) return;
        JSONObject form = activity.getmJSONObject();
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        if (!"3".equals(values.get(DBConstants.KEY.CHILD_EID_FOLLOW_UP_SCHEMA))
                || !values.containsKey(DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT)
                || Objects.equals(previous, values)) return;
        View root = activity.getWindow().getDecorView();
        // Do not cache an early callback before the normal factory mounts native controls.
        if (controls(root, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS).isEmpty()) return;
        refreshing = true;
        try {
            boolean newReturn = MotherMentorChildEidReturnVisit.applies(values);
            if (previous != null && changed(values, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS)) {
                MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, "");
                if (!newReturn) clearReferralAndReturn(form);
            } else if (!newReturn && previous != null && changed(values,
                    DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)) {
                clearReferralAndReturn(form);
            } else if (previous != null && changed(values, DBConstants.KEY.CHILD_EID_TEST_TYPE)) {
                if (newReturn) MotherMentorChildEidReturnVisit.clearSample(form);
                else for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS.subList(
                        1, MotherMentorChildEidHeiUtils.RETURN_FIELDS.size())) {
                    MotherMentorMotherChildUtils.put(form, key, "");
                }
            }
            validateDates(activity, form, root);
            prepare(form, activity);
            Map<String, String> prepared = MotherMentorMotherChildUtils.values(form);
            for (Map.Entry<String, String> entry : prepared.entrySet()) {
                if (!Objects.equals(entry.getValue(), values.get(entry.getKey()))) {
                    syncWidget(root, entry.getKey(), entry.getValue());
                }
            }
            List<Stage> allowed = due(form);
            for (View control : controls(root, DBConstants.KEY.CHILD_EID_TEST_TYPE)) {
                if (!(control instanceof RadioButton)) continue;
                RadioButton button = (RadioButton) control;
                Stage stage = Stage.fromCode(String.valueOf(button.getTag(
                        com.vijay.jsonwizard.R.id.childKey)));
                View container = button;
                while (container.getParent() instanceof View
                        && !(container.getParent() instanceof RadioGroup)) {
                    container = (View) container.getParent();
                }
                container.setVisibility(allowed.contains(stage) ? View.VISIBLE : View.GONE);
                button.setEnabled(allowed.contains(stage));
            }
            previous = new LinkedHashMap<>(prepared);
        } finally {
            refreshing = false;
        }
    }

    private boolean changed(Map<String, String> values, String key) {
        return !Objects.equals(previous.get(key), values.get(key));
    }

    private static void clearReferralAndReturn(JSONObject form) {
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT, "");
        for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS) {
            MotherMentorMotherChildUtils.put(form, key, "");
        }
    }

    public static void prepare(JSONObject form, Context context) {
        MotherMentorChildEidHeiUtils.clearInactiveAnswers(form);
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        LocalDate dob = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.CHILD_DOB));
        LocalDate day = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_ASSESSMENT_DATE));
        if (day == null) day = LocalDate.now();
        Map<Stage, TestRecord> tests = MotherMentorMotherChildUtils.parseTests(
                values.get(DBConstants.KEY.MAMA_HEI_TEST_HISTORY));
        boolean first = "none".equals(values.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE));
        List<Stage> sampling = MotherMentorChildEidHeiUtils.eligible(values, dob, tests, day);
        List<Stage> review = MotherMentorChildEidHeiUtils.reviewable(values, dob, tests, day);
        JSONArray codes = new JSONArray();
        for (Stage stage : sampling) codes.put(stage.getCode());
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_HEI_DUE_TESTS, codes.toString());
        JSONArray reviewCodes = new JSONArray();
        for (Stage stage : review) reviewCodes.put(stage.getCode());
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_EID_HEI_REVIEW_TESTS,
                reviewCodes.toString());
        if (first) {
            Stage stage = sampling.isEmpty() ? null : sampling.get(0);
            LocalDate date = MotherMentorHeiSchedule.dueDate(stage, dob,
                    MotherMentorChildEidHeiUtils.cessation(values));
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_RUFAA_TEST,
                    stage == null ? "" : stage.getMetadataCode());
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_RUFAA_DUE,
                    date == null ? "" : date.toString());
            if (sampling.isEmpty()) MotherMentorMotherChildUtils.put(form,
                    DBConstants.KEY.MTOTO_REFERRAL_AGREEMENT, "");
        } else if (MotherMentorChildEidReturnVisit.applies(values)) {
            prepareReturn(form, context, values, dob, day, tests, sampling);
        } else {
            Stage selected = Stage.fromCode(values.get(DBConstants.KEY.CHILD_EID_TEST_TYPE));
            if (!review.contains(selected)) {
                clearReferralAndReturn(form);
                MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_EID_TEST_TYPE,
                        review.size() == 1 ? review.get(0).getCode() : "");
            } else if (selected != null) {
                MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_EID_TEST_TYPE,
                        selected.getCode());
            }
        }
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_CHILD_AGE_NOTE,
                age(context, dob, day));
        String banner;
        if (dob == null) banner = context == null ? "" : context.getString(
                R.string.mothermentor_child_eid_dob_required);
        else if (MotherMentorChildEidReturnVisit.applies(values)
                && !MotherMentorChildEidReturnVisit.REFERRED.equals(values.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE))
                && Stage.fromCode(MotherMentorMotherChildUtils.values(form).get(DBConstants.KEY.MTOTO_PENDING_TEST)) != null) {
            banner = label(context, Stage.fromCode(MotherMentorMotherChildUtils.values(form).get(DBConstants.KEY.MTOTO_PENDING_TEST)));
        } else if (!sampling.isEmpty()) banner = TextUtils.join(" / ", labels(context, sampling));
        else {
            Stage next = MotherMentorHeiSchedule.next(dob,
                    MotherMentorChildEidHeiUtils.cessation(values), tests, day);
            String none = context == null ? "none" : context.getString(
                    R.string.mothermentor_child_eid_hei_none);
            LocalDate nextDate = MotherMentorHeiSchedule.dueDate(next, dob,
                    MotherMentorChildEidHeiUtils.cessation(values));
            banner = next == null ? none : none + "\n" + (context == null
                    ? next.getCode() + " — " + nextDate
                    : context.getString(R.string.mothermentor_child_eid_hei_next,
                            label(context, next), nextDate.toString()));
        }
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MAMA_HEI_NEXT_TEST_NOTE, banner);
        for (String key : Arrays.asList(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA,
                DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE,
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE, DBConstants.KEY.MAMA_HEI_TEST_DATE)) {
            JSONObject field = MotherMentorMotherChildUtils.field(form, key);
            if (field == null) continue;
            try {
                LocalDate minimum = minimumDate(key, dob, values);
                if (minimum != null) field.put("min_date", minimum.toString("dd-MM-yyyy"));
                field.put("max_date", day.toString("dd-MM-yyyy"));
            } catch (Exception ignored) { }
        }
    }

    private static LocalDate minimumDate(String key, LocalDate dob, Map<String, String> values) {
        if (DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE.equals(key)) {
            return dob == null ? null : dob.plusMonths(18);
        }
        if (DBConstants.KEY.MAMA_HEI_TEST_DATE.equals(key)) {
            return MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE));
        }
        return dob;
    }

    private static void prepareReturn(JSONObject form, Context context, Map<String, String> values,
            LocalDate dob, LocalDate day, Map<Stage, TestRecord> tests, List<Stage> sampling) {
        String state = values.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE);
        if (MotherMentorChildEidReturnVisit.REFERRED.equals(state)) {
            Stage selected = Stage.fromCode(values.get(DBConstants.KEY.CHILD_EID_TEST_TYPE));
            if ("yes".equals(values.get(DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY))
                    && "yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE)) && !sampling.contains(selected)) {
                MotherMentorChildEidReturnVisit.clearSample(form);
                MotherMentorMotherChildUtils.put(form, DBConstants.KEY.CHILD_EID_TEST_TYPE,
                        sampling.size() == 1 ? sampling.get(0).getCode() : "");
            }
        } else {
            Stage stage = Stage.fromCode(values.get(DBConstants.KEY.MTOTO_PENDING_TEST));
            if (stage == null) stage = Stage.fromCode(values.get(DBConstants.KEY.MTOTO_RUFAA_TEST));
            if (!MotherMentorChildEidReturnVisit.matchesPending(tests.get(stage), state)) {
                stage = null;
                for (Map.Entry<Stage, TestRecord> entry : tests.entrySet()) {
                    if (!MotherMentorChildEidReturnVisit.matchesPending(entry.getValue(), state)) continue;
                    if (stage != null) { stage = null; break; }
                    stage = entry.getKey();
                }
            }
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_PENDING_TEST,
                    stage == null ? "" : stage.getMetadataCode());
            MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_PENDING_TEST_DATE,
                    stage == null ? "" : tests.get(stage).getDate().toString());
            JSONObject question = MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MTOTO_RESULT_RECEIVED);
            if (context != null && stage != null && question != null) {
                try {
                    question.put("label", context.getString(R.string.mothermentor_eid_return_named_result,
                            label(context, stage)));
                } catch (org.json.JSONException ignored) { }
            }
        }
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_RETURN_RESULT_NOTE,
                returnNote(context, MotherMentorMotherChildUtils.values(form), dob, day, tests));
    }

    public static String returnNote(Context context, Map<String, String> values, LocalDate dob,
            LocalDate day, Map<Stage, TestRecord> tests) {
        if (context == null) return "";
        MotherMentorChildEidReturnVisit.Result result = MotherMentorChildEidReturnVisit.evaluate(
                values, dob, day, tests, values.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE));
        if (result == null) return "";
        switch (result.outcome) {
            case TESTED_NEGATIVE: return context.getString(R.string.mothermentor_eid_return_negative);
            case CONFIRMATORY_NEGATIVE: return context.getString(R.string.mothermentor_eid_return_confirm_negative);
            case GRADUATED: return context.getString(R.string.mothermentor_eid_return_graduation);
            case CONFIRMED_POSITIVE: return context.getString(R.string.mothermentor_eid_return_confirm_positive);
            default:
                if (MotherMentorChildEidReturnVisit.AWAITING_RESULT.equals(result.nextState)) {
                    return context.getString(R.string.mothermentor_eid_return_wait_result);
                }
                if (MotherMentorChildEidReturnVisit.AWAITING_CONFIRM.equals(result.nextState)) {
                    return context.getString(R.string.mothermentor_eid_return_wait_confirm);
                }
                return context.getString("no".equals(values.get(DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY))
                        ? R.string.mothermentor_eid_return_nonattendance : R.string.mothermentor_eid_return_attendance);
        }
    }

    private static List<Stage> due(JSONObject form) {
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        List<Stage> result = new ArrayList<>();
        try {
            String key = MotherMentorChildEidReturnVisit.applies(values)
                    ? DBConstants.KEY.MAMA_HEI_DUE_TESTS : DBConstants.KEY.CHILD_EID_HEI_REVIEW_TESTS;
            JSONArray stages = new JSONArray(values.get(key));
            for (int i = 0; i < stages.length(); i++) {
                Stage stage = Stage.fromCode(stages.optString(i));
                if (stage != null) result.add(stage);
            }
        } catch (Exception ignored) { }
        return result;
    }

    private static void validateDates(Context context, JSONObject form, View root) {
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        LocalDate dob = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.CHILD_DOB));
        LocalDate day = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_ASSESSMENT_DATE));
        for (String key : Arrays.asList(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA,
                DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE,
                DBConstants.KEY.CHILD_EID_FINAL_HIV_TEST_DATE, DBConstants.KEY.MAMA_HEI_TEST_DATE)) {
            String value = values.get(key);
            LocalDate date = MotherMentorHeiSchedule.parseDate(value);
            if (value == null || value.isEmpty() || MotherMentorHeiSchedule.validDate(
                    date, minimumDate(key, dob, values), day)) continue;
            MotherMentorMotherChildUtils.put(form, key, "");
            syncWidget(root, key, "");
            for (View view : controls(root, key)) if (view instanceof EditText) {
                ((EditText) view).setError(context.getString(R.string.mothermentor_child_eid_hei_invalid_date));
            }
        }
    }

    public static List<String> labels(Context context, List<Stage> stages) {
        List<String> labels = new ArrayList<>();
        for (Stage stage : stages) labels.add(label(context, stage));
        return labels;
    }

    public static String label(Context context, Stage stage) {
        if (stage == null) return "";
        if (context == null) return stage.getCode();
        switch (stage) {
            case SIX_WEEKS: return context.getString(R.string.mothermentor_child_eid_hei_stage_six_weeks);
            case NINE_MONTHS: return context.getString(R.string.mothermentor_child_eid_hei_stage_nine_months);
            case AFTER_WEANING: return context.getString(R.string.mothermentor_child_eid_hei_stage_three_months_after_weaning);
            case EIGHTEEN_MONTHS: return context.getString(R.string.mothermentor_child_eid_hei_stage_eighteen_months);
            default: return stage.getCode();
        }
    }

    public static String age(Context context, LocalDate dob, LocalDate day) {
        if (dob == null || day == null || dob.isAfter(day)) return "";
        int days = Days.daysBetween(dob, day).getDays();
        if (context == null) return Integer.toString(days);
        if (days == 0) return context.getString(R.string.mothermentor_mother_child_newborn);
        if (days < 7) return context.getString(R.string.mothermentor_mother_child_days, days);
        if (days / 7 < 9) return context.getString(R.string.mothermentor_mother_child_weeks, days / 7);
        return context.getString(R.string.mothermentor_mother_child_months,
                Months.monthsBetween(dob, day).getMonths());
    }

    private static void syncWidget(View root, String key, String value) {
        for (View view : controls(root, key)) {
            if (view instanceof EditText && !value.contentEquals(((EditText) view).getText())) {
                ((EditText) view).setText(value);
            } else if (view instanceof RadioGroup) {
                RadioGroup group = (RadioGroup) view;
                if (value.isEmpty()) group.clearCheck();
                else for (View option : all(group)) if (option instanceof RadioButton
                        && value.equals(String.valueOf(option.getTag(com.vijay.jsonwizard.R.id.childKey)))) {
                    group.check(option.getId());
                }
            }
        }
    }

    private static List<View> controls(View root, String key) {
        List<View> result = new ArrayList<>();
        for (View view : all(root)) if (key.equals(view.getTag(com.vijay.jsonwizard.R.id.key))) result.add(view);
        return result;
    }

    private static List<View> all(View root) {
        List<View> result = new ArrayList<>();
        result.add(root);
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            result.addAll(all(((ViewGroup) root).getChildAt(i)));
        }
        return result;
    }
}
