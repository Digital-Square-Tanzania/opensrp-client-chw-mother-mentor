package org.smartregister.chw.mothermentor.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

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

/** Bounded native-wizard adapter: retains existing widgets/validators and filters due options. */
public final class MotherMentorMotherChildUi {
    private Map<String, String> previous;
    private boolean refreshing;

    public void refresh(JsonFormActivity activity) {
        if (refreshing || activity.getmJSONObject() == null) return;
        JSONObject form = activity.getmJSONObject();
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        if (!values.containsKey(DBConstants.KEY.MAMA_CHILD_STATUS)) return;
        View root = activity.getWindow().getDecorView();
        RadioGroup choices = radioGroup(root, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE);
        // Wait until the normal factory has registered the complete stage control.
        if (choices == null) return;
        refreshing = true;
        try {
            boolean firstAlive = previous != null
                    && String.valueOf(previous.get(DBConstants.KEY.MAMA_CHILD_STATUS)).isEmpty()
                    && "alive".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS));
            clearChangedBranches(form, root, values);
            if (firstAlive) {
                set(form, root, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS,
                        values.getOrDefault(DBConstants.KEY.MAMA_PREVIOUS_BREASTFEEDING_STATUS, ""));
                set(form, root, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA,
                        values.getOrDefault(DBConstants.KEY.MAMA_PREVIOUS_WEANING_DATE, ""));
            }
            values = MotherMentorMotherChildUtils.values(form);
            LocalDate assessment = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_HEI_ASSESSMENT_DATE));
            if (assessment == null) assessment = LocalDate.now();
            boolean path = MotherMentorMotherChildUtils.applies(values);
            set(form, root, DBConstants.KEY.MAMA_CHILD_PATH, Boolean.toString(path));
            LocalDate dob = MotherMentorMotherChildUtils.birth(values);
            if (path) validateDates(activity, form, root, dob, assessment);
            values = MotherMentorMotherChildUtils.values(form);
            List<Stage> due = path && "alive".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS))
                    ? MotherMentorMotherChildUtils.due(values, assessment) : new ArrayList<>();
            JSONArray codes = new JSONArray();
            for (Stage stage : due) codes.put(stage.getCode());
            set(form, root, DBConstants.KEY.MAMA_HEI_DUE_TESTS, codes.toString());
            Stage first = due.isEmpty() ? null : due.get(0);
            set(form, root, DBConstants.KEY.MAMA_HEI_DUE_TEST, first == null ? "" : first.getCode());
            LocalDate stop = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
            LocalDate dueDate = MotherMentorHeiSchedule.dueDate(first, dob, stop);
            set(form, root, DBConstants.KEY.MAMA_HEI_DUE_DATE, dueDate == null ? "" : dueDate.toString());
            set(form, root, DBConstants.KEY.MAMA_CHILD_AGE_NOTE, path ? age(activity, dob, assessment) : "");
            set(form, root, DBConstants.KEY.MAMA_HEI_NEXT_TEST_NOTE,
                    path ? next(activity, values, due, assessment) : "");
            if (firstAlive) {
                for (Map.Entry<Stage, TestRecord> entry : MotherMentorMotherChildUtils.parseTests(
                        values.get(DBConstants.KEY.MAMA_HEI_TEST_HISTORY)).entrySet()) {
                    if (!entry.getValue().requiresFollowUp() || !due.contains(entry.getKey())) continue;
                    set(form, root, DBConstants.KEY.MAMA_HEI_TEST_DONE, "yes");
                    set(form, root, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, entry.getKey().getCode());
                    set(form, root, DBConstants.KEY.MAMA_HEI_TEST_DATE,
                            entry.getValue().getDate() == null ? "" : entry.getValue().getDate().toString("dd-MM-yyyy"));
                    set(form, root, DBConstants.KEY.MAMA_HEI_TEST_RESULT, entry.getValue().getResult());
                    if ("positive".equals(entry.getValue().getResult())) set(form, root, DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA, "no");
                    break;
                }
                values = MotherMentorMotherChildUtils.values(form);
            }
            String selected = values.get(DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE);
            Stage choice = Stage.fromCode(selected);
            if (choice == null || !due.contains(choice)) {
                choices.clearCheck();
                set(form, root, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, "");
                choice = null;
            }
            for (View control : controls(root, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE)) {
                if (!(control instanceof RadioButton)) continue;
                RadioButton button = (RadioButton) control;
                Stage option = Stage.fromCode(String.valueOf(button.getTag(com.vijay.jsonwizard.R.id.childKey)));
                View container = button;
                while (container.getParent() instanceof View && container.getParent() != choices) {
                    container = (View) container.getParent();
                }
                container.setVisibility(due.contains(option) ? View.VISIBLE : View.GONE);
                button.setEnabled(due.contains(option));
            }
            String canonical = "yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))
                    ? (due.size() == 1 ? due.get(0).getCode() : choice == null ? "" : choice.getCode()) : "";
            set(form, root, DBConstants.KEY.MAMA_HEI_TEST_TYPE, canonical);
            updateQuestion(activity, form, root, due);
            previous = new LinkedHashMap<>(MotherMentorMotherChildUtils.values(form));
        } finally {
            refreshing = false;
        }
    }

    private void clearChangedBranches(JSONObject form, View root, Map<String, String> now) {
        if (previous == null) return;
        if (changed(now, DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA)
                || changed(now, DBConstants.KEY.FOLLOW_UP_STATUS)
                || changed(now, DBConstants.KEY.MAMA_PREGNANCY_OUTCOME)
                || changed(now, DBConstants.KEY.VISIT_PREGNANCY_STATUS)) {
            clear(form, root, MotherMentorMotherChildUtils.ANSWER_FIELDS);
        } else if (changed(now, DBConstants.KEY.MAMA_CHILD_STATUS)
                && previous.get(DBConstants.KEY.MAMA_CHILD_STATUS) != null
                && !previous.get(DBConstants.KEY.MAMA_CHILD_STATUS).isEmpty()) {
            clear(form, root, Arrays.asList(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI,
                    DBConstants.KEY.MAMA_BREASTFEEDING_STATUS, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
            clearTests(form, root, true);
        } else if (changed(now, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS)) {
            clear(form, root, Arrays.asList(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
            clearTests(form, root, true);
        } else if (changed(now, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)) {
            clearTests(form, root, true);
        } else if (changed(now, DBConstants.KEY.MAMA_HEI_TEST_DONE)
                || changed(now, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE)) {
            clearTests(form, root, false);
        } else if (changed(now, DBConstants.KEY.MAMA_HEI_TEST_RESULT)) {
            clear(form, root, Arrays.asList(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA));
        }
    }

    private boolean changed(Map<String, String> now, String key) {
        return !java.util.Objects.equals(previous.get(key), now.get(key));
    }

    private void clearTests(JSONObject form, View root, boolean includeDone) {
        if (includeDone) set(form, root, DBConstants.KEY.MAMA_HEI_TEST_DONE, "");
        clear(form, root, Arrays.asList(DBConstants.KEY.MAMA_HEI_TEST_TYPE,
                DBConstants.KEY.MAMA_HEI_TEST_DATE, DBConstants.KEY.MAMA_HEI_TEST_RESULT,
                DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA));
        if (includeDone) set(form, root, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, "");
    }

    private void clear(JSONObject form, View root, List<String> keys) {
        for (String key : keys) set(form, root, key, "");
    }

    private void validateDates(Context context, JSONObject form, View root, LocalDate dob, LocalDate assessment) {
        for (String key : Arrays.asList(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI,
                DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, DBConstants.KEY.MAMA_HEI_TEST_DATE)) {
            JSONObject field = MotherMentorMotherChildUtils.field(form, key);
            if (field == null) continue;
            try {
                field.put("min_date", dob.toString("dd-MM-yyyy"));
                field.put("max_date", assessment.toString("dd-MM-yyyy"));
            } catch (Exception ignored) { }
            String value = field.optString("value");
            if (!value.isEmpty() && !MotherMentorHeiSchedule.validDate(
                    MotherMentorHeiSchedule.parseDate(value), dob, assessment)) {
                set(form, root, key, "");
                for (View view : controls(root, key)) if (view instanceof EditText) {
                    ((EditText) view).setError(context.getString(R.string.mothermentor_mother_child_invalid_date));
                }
            }
        }
    }

    private static void set(JSONObject form, View root, String key, String value) {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        if (field == null || value.equals(field.optString("value"))) return;
        MotherMentorMotherChildUtils.put(form, key, value);
        for (View view : controls(root, key)) {
            if (view instanceof EditText && !value.contentEquals(((EditText) view).getText())) {
                ((EditText) view).setText(value);
            } else if (view instanceof RadioGroup) {
                RadioGroup group = (RadioGroup) view;
                if (value.isEmpty()) group.clearCheck();
                else for (View option : all(group)) {
                    if (option instanceof RadioButton && value.equals(String.valueOf(
                            option.getTag(com.vijay.jsonwizard.R.id.childKey)))) {
                        group.check(option.getId());
                    }
                }
            }
        }
    }

    private static void updateQuestion(Context context, JSONObject form, View root, List<Stage> due) {
        if (due.isEmpty()) return;
        List<String> labels = new ArrayList<>();
        for (Stage stage : due) labels.add(label(context, stage));
        JSONObject field = MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MAMA_HEI_TEST_DONE);
        if (field == null) return;
        String old = field.optString("label");
        String text = context.getString(R.string.mothermentor_mother_hei_question, android.text.TextUtils.join(" / ", labels));
        if (old.equals(text)) return;
        for (View view : all(root)) if (view instanceof TextView && !(view instanceof EditText)
                && !(view instanceof RadioButton) && ((TextView) view).getText().toString().startsWith(old)) {
            ((TextView) view).setText(text);
        }
        try { field.put("label", text); } catch (Exception ignored) { }
    }

    private static String age(Context context, LocalDate dob, LocalDate day) {
        if (dob == null || day.isBefore(dob)) return "";
        int days = Days.daysBetween(dob, day).getDays();
        if (days == 0) return context.getString(R.string.mothermentor_mother_child_newborn);
        if (days < 7) return context.getString(R.string.mothermentor_mother_child_days, days);
        if (days / 7 < 9) return context.getString(R.string.mothermentor_mother_child_weeks, days / 7);
        return context.getString(R.string.mothermentor_mother_child_months, Months.monthsBetween(dob, day).getMonths());
    }

    private static String next(Context context, Map<String, String> values, List<Stage> due, LocalDate day) {
        if (!due.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (Stage stage : due) labels.add(label(context, stage));
            return context.getString(R.string.mothermentor_mother_hei_due, android.text.TextUtils.join(" / ", labels));
        }
        LocalDate dob = MotherMentorMotherChildUtils.birth(values);
        if (dob == null) return "";
        Map<Stage, TestRecord> tests = MotherMentorMotherChildUtils.parseTests(values.get(DBConstants.KEY.MAMA_HEI_TEST_HISTORY));
        LocalDate stop = MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
        for (Stage stage : Stage.values()) {
            if (tests.containsKey(stage)) continue;
            if (stage == Stage.SIX_WEEKS && !day.isBefore(dob.plusMonths(9))) continue;
            if (stage == Stage.NINE_MONTHS && !day.isBefore(dob.plusMonths(18))) continue;
            if (stage == Stage.AFTER_WEANING && !day.isBefore(dob.plusMonths(18))) continue;
            LocalDate date = MotherMentorHeiSchedule.dueDate(stage, dob, stop);
            if (stage == Stage.AFTER_WEANING && date == null) return context.getString(R.string.mothermentor_mother_hei_wait_weaning);
            if (date != null) return context.getString(R.string.mothermentor_mother_hei_next,
                    label(context, stage), MotherMentorMotherPregnancyUtils.displayDate(date));
        }
        return context.getString(R.string.mothermentor_mother_hei_none);
    }

    public static String label(Context context, Stage stage) {
        int resource = context.getResources().getIdentifier("mothermentor_child_eid_test_" + stage.getCode(), "string", context.getPackageName());
        return resource == 0 ? stage.getCode() : context.getString(resource);
    }

    private static RadioGroup radioGroup(View root, String key) {
        for (View view : controls(root, key)) if (view instanceof RadioGroup) return (RadioGroup) view;
        return null;
    }

    private static List<View> controls(View root, String key) {
        List<View> result = new ArrayList<>();
        for (View view : all(root)) if (key.equals(view.getTag(com.vijay.jsonwizard.R.id.key))) result.add(view);
        return result;
    }

    private static List<View> all(View root) {
        List<View> result = new ArrayList<>();
        result.add(root);
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) result.addAll(all(((ViewGroup) root).getChildAt(i)));
        return result;
    }
}
