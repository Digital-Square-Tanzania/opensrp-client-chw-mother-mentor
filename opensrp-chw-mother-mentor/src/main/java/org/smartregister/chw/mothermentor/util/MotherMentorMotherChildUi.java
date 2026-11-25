package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.DatePickerDialog;
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
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

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
            boolean path = MotherMentorMotherChildUtils.applies(values)
                    && (!MotherMentorMotherVisitUtils.hasFirstVisitAssessment(values)
                            || form.optBoolean("mothermentor_child_section"));
            set(form, root, DBConstants.KEY.MAMA_CHILD_PATH, Boolean.toString(path));
            LocalDate dob = MotherMentorMotherChildUtils.birth(values);
            if (path) {
                JSONObject weaning = MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA);
                if (weaning != null && weaning.optBoolean("read_only")
                        && "not_breastfeeding".equals(values.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))) {
                    set(form, root, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA,
                            values.getOrDefault(DBConstants.KEY.MAMA_PREVIOUS_WEANING_DATE, ""));
                }
                validateDates(activity, form, root, dob, assessment);
            }
            refreshBirthTest(activity, form, root, values, path, dob, assessment);
            values = MotherMentorMotherChildUtils.values(form);
            List<Stage> due = path && "alive".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS))
                    ? MotherMentorMotherChildUtils.due(values, assessment) : new ArrayList<>();
            JSONArray codes = new JSONArray();
            for (Stage stage : due) codes.put(stage.getCode());
            set(form, root, DBConstants.KEY.MAMA_HEI_DUE_TESTS, codes.toString());
            Stage first = due.isEmpty() ? null : due.get(0);
            set(form, root, DBConstants.KEY.MAMA_HEI_DUE_TEST, first == null ? "" : first.getCode());
            LocalDate stop = MotherMentorChildEidHeiUtils.cessation(values);
            LocalDate dueDate = MotherMentorHeiSchedule.dueDate(first, dob, stop);
            set(form, root, DBConstants.KEY.MAMA_HEI_DUE_DATE, dueDate == null ? "" : dueDate.toString());
            set(form, root, DBConstants.KEY.MAMA_CHILD_AGE_NOTE, path ? age(activity, dob, assessment) : "");
            set(form, root, DBConstants.KEY.MAMA_HEI_NEXT_TEST_NOTE,
                    path ? next(activity, values, due, assessment) : "");
            if (firstAlive && !MotherMentorAwaitedResults.enabled(values)) {
                for (Map.Entry<Stage, TestRecord> entry : MotherMentorMotherChildUtils.parseTests(
                        values.get(DBConstants.KEY.MAMA_HEI_TEST_HISTORY)).entrySet()) {
                    if (!entry.getValue().requiresFollowUp() || !due.contains(entry.getKey())) continue;
                    set(form, root, DBConstants.KEY.MAMA_HEI_TEST_DONE, "yes");
                    set(form, root, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, entry.getKey().getCode());
                    set(form, root, MAMA_HEI_TEST_DATE,
                            entry.getValue().getDate() == null ? "" : entry.getValue().getDate().toString("dd-MM-yyyy"));
                    set(form, root, MAMA_HEI_TEST_RESULT, entry.getValue().getResult());
                    LocalDate received = entry.getValue().getReceivedDate();
                    set(form, root, MAMA_HEI_TEST_RECEIVED_DATE, received == null ? "" : received.toString("dd-MM-yyyy"));
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
            boolean finalStage = Stage.EIGHTEEN_MONTHS.getCode().equals(canonical);
            for (String key : Arrays.asList(MAMA_HEI_TEST_RESULT, MAMA_HEI_CONFIRMATORY_RESULT)) {
                if (finalStage && "pending".equals(values.get(key))) set(form, root, key, "");
                for (View view : controls(root, key)) {
                    if (view instanceof RadioButton
                            && "pending".equals(view.getTag(com.vijay.jsonwizard.R.id.childKey))) {
                        MotherMentorChildEidHeiUi.showOption((RadioButton) view, !finalStage);
                    }
                }
            }
            values = MotherMentorMotherChildUtils.values(form);
            if (path) {
                updateReceiptDate(activity, form, root, MAMA_HEI_TEST_RECEIVED_DATE,
                        values.get(MAMA_HEI_TEST_RESULT), values.get(MAMA_HEI_TEST_DATE), assessment);
            }
            updateQuestion(activity, form, root, due);
            previous = new LinkedHashMap<>(MotherMentorMotherChildUtils.values(form));
        } finally {
            refreshing = false;
        }
    }

    private void clearChangedBranches(JSONObject form, View root, Map<String, String> now) {
        if (previous == null) return;
        boolean changedContext = changed(now, DBConstants.KEY.FOLLOW_UP_STATUS)
                || changed(now, DBConstants.KEY.MAMA_PREGNANCY_OUTCOME)
                || changed(now, DBConstants.KEY.VISIT_PREGNANCY_STATUS);
        if (changedContext || changed(now, DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA)) {
            clear(form, root, MotherMentorMotherChildUtils.ANSWER_FIELDS);
            if (changedContext) clear(form, root, MotherMentorMotherBirthTest.ANSWERS);
        } else if (changed(now, DBConstants.KEY.MAMA_CHILD_STATUS)
                && previous.get(DBConstants.KEY.MAMA_CHILD_STATUS) != null
                && !previous.get(DBConstants.KEY.MAMA_CHILD_STATUS).isEmpty()) {
            clear(form, root, Arrays.asList(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI,
                    DBConstants.KEY.MAMA_BREASTFEEDING_STATUS, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
            clearTests(form, root, true);
            clear(form, root, MotherMentorMotherBirthTest.ANSWERS);
        } else if (changed(now, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS)) {
            clear(form, root, Arrays.asList(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
            clearTests(form, root, true);
        } else if (changed(now, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)) {
            clearTests(form, root, true);
        } else if (changed(now, DBConstants.KEY.MAMA_HEI_TEST_DONE)
                || changed(now, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE)) {
            clearTests(form, root, false);
        } else if (changed(now, MAMA_HEI_TEST_RESULT)) {
            set(form, root, MAMA_HEI_CONFIRMATORY_TEST_DONE, "");
            set(form, root, MAMA_HEI_CONFIRMATORY_RESULT, "");
        } else if (changed(now, MAMA_HEI_CONFIRMATORY_TEST_DONE)
                && !"yes".equals(now.get(MAMA_HEI_CONFIRMATORY_TEST_DONE))) {
            set(form, root, MAMA_HEI_CONFIRMATORY_RESULT, "");
        }
        if (changed(now, MAMA_BIRTH_TEST_DONE) && !"yes".equals(now.get(MAMA_BIRTH_TEST_DONE))) {
            clear(form, root, MotherMentorMotherBirthTest.dependentAnswers());
        } else if (changed(now, MAMA_BIRTH_TEST_RESULT)) {
            if (!"positive".equals(now.get(MAMA_BIRTH_TEST_RESULT))) {
                set(form, root, MAMA_BIRTH_TEST_CONFIRMATION_DONE, "");
                set(form, root, MAMA_BIRTH_TEST_CONFIRMATION_RESULT, "");
            }
        } else if (changed(now, MAMA_BIRTH_TEST_CONFIRMATION_DONE)
                && !"yes".equals(now.get(MAMA_BIRTH_TEST_CONFIRMATION_DONE))) {
            set(form, root, MAMA_BIRTH_TEST_CONFIRMATION_RESULT, "");
        }
    }

    private void refreshBirthTest(JsonFormActivity activity, JSONObject form, View root,
            Map<String, String> values, boolean path, LocalDate dob, LocalDate assessment) {
        if (!values.containsKey(MAMA_BIRTH_TEST_REQUIRED)) return;
        boolean required = path && MotherMentorMotherBirthTest.isRequired(values, assessment);
        String relevance = Boolean.toString(required);
        if (!relevance.equals(values.get(MAMA_BIRTH_TEST_REQUIRED))) {
            set(form, root, MAMA_BIRTH_TEST_REQUIRED, relevance);
            activity.refreshSkipLogic(MAMA_BIRTH_TEST_REQUIRED, MAMA_BIRTH_TEST_REQUIRED, false, "step1", false);
        }
        if (!required) {
            if (path) clear(form, root, MotherMentorMotherBirthTest.ANSWERS);
            return;
        }

        LocalDate latest = MotherMentorMotherBirthTest.latestSampleDate(dob, assessment);
        bindBirthDatePicker(activity, form, root, MAMA_BIRTH_TEST_DATE, dob, latest,
                R.string.mothermentor_mother_birth_invalid_date);
        JSONObject sample = MotherMentorMotherChildUtils.field(form, MAMA_BIRTH_TEST_DATE);
        LocalDate sampleDate = MotherMentorHeiSchedule.parseDate(sample.optString("value"));
        if (sampleDate == null) {
            set(form, root, MAMA_BIRTH_TEST_RECEIVED_DATE, "");
        } else {
            updateReceiptDate(activity, form, root, MAMA_BIRTH_TEST_RECEIVED_DATE,
                    values.get(MAMA_BIRTH_TEST_RESULT), sample.optString("value"), assessment);
        }
        bindBirthDatePicker(activity, form, root, MAMA_BIRTH_TEST_RECEIVED_DATE,
                sampleDate == null ? dob : sampleDate, assessment, R.string.mothermentor_eid_received_before_test);
    }

    // The native wizard captures date limits at widget creation. Use the current bounds when opened.
    private void bindBirthDatePicker(Context context, JSONObject form, View root, String key,
            LocalDate min, LocalDate max, int error) {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        if (field == null) return;
        LocalDate date = MotherMentorHeiSchedule.parseDate(field.optString("value"));
        boolean invalid = !field.optString("value").isEmpty() && !MotherMentorHeiSchedule.validDate(date, min, max);
        if (invalid) set(form, root, key, "");
        for (View view : controls(root, key)) {
            if (!(view instanceof EditText)) continue;
            EditText input = (EditText) view;
            if (invalid) input.setError(context.getString(error));
            input.setOnClickListener(clicked -> {
                LocalDate selected = MotherMentorHeiSchedule.parseDate(field.optString("value"));
                LocalDate initial = MotherMentorHeiSchedule.validDate(selected, min, max) ? selected : max;
                DatePickerDialog dialog = new DatePickerDialog(input.getContext(), (picker, year, month, day) -> {
                    String value = new LocalDate(year, month + 1, day).toString("dd-MM-yyyy", java.util.Locale.US);
                    input.setTag(com.vijay.jsonwizard.R.id.locale_independent_value, value);
                    input.setText(value);
                }, initial.getYear(), initial.getMonthOfYear() - 1, initial.getDayOfMonth());
                dialog.getDatePicker().setMinDate(min.toDate().getTime());
                dialog.getDatePicker().setMaxDate(max.toDate().getTime());
                dialog.show();
            });
        }
    }

    private boolean changed(Map<String, String> now, String key) {
        return !java.util.Objects.equals(previous.get(key), now.get(key));
    }

    private void clearTests(JSONObject form, View root, boolean includeDone) {
        if (includeDone) set(form, root, DBConstants.KEY.MAMA_HEI_TEST_DONE, "");
        clear(form, root, Arrays.asList(DBConstants.KEY.MAMA_HEI_TEST_TYPE,
                MAMA_HEI_TEST_DATE, MAMA_HEI_TEST_RESULT, MAMA_HEI_TEST_RECEIVED_DATE,
                MAMA_HEI_CONFIRMATORY_TEST_DONE, MAMA_HEI_CONFIRMATORY_RESULT));
        if (includeDone) set(form, root, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, "");
    }

    private void clear(JSONObject form, View root, List<String> keys) {
        for (String key : keys) set(form, root, key, "");
    }

    private void validateDates(Context context, JSONObject form, View root, LocalDate dob, LocalDate assessment) {
        for (String key : Arrays.asList(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI,
                DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, MAMA_HEI_TEST_DATE)) {
            validateDate(context, form, root, key, dob, assessment);
        }
    }

    private void updateReceiptDate(JsonFormActivity activity, JSONObject form, View root, String key,
            String result, String sample, LocalDate assessment) {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        if (field == null || field.optString("value").isEmpty()) return;
        if (!MotherMentorHeiSchedule.hasReceivedResult(result)) {
            set(form, root, key, "");
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate latest = assessment.isAfter(today) ? today : assessment;
        int error = MotherMentorChildEidResultForm.receiptDateError(
                field.optString("value"), MotherMentorHeiSchedule.parseDate(sample), latest);
        if (error != 0) {
            set(form, root, key, "");
            // Keep the native form cache in sync so reselecting the same date is validated again.
            try {
                activity.writeValue("step1", key, "", field.optString("openmrs_entity_parent"),
                        field.optString("openmrs_entity"), field.optString("openmrs_entity_id"));
            } catch (JSONException exception) {
                Timber.w(exception);
            }
            for (View view : controls(root, key)) if (view instanceof EditText) {
                ((EditText) view).setError(activity.getString(error));
            }
        }
    }

    private void validateDate(Context context, JSONObject form, View root, String key,
            LocalDate min, LocalDate max) {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        if (field == null) return;
        try {
            field.put("min_date", min.toString("dd-MM-yyyy"));
            field.put("max_date", max.toString("dd-MM-yyyy"));
        } catch (Exception ignored) { }
        String value = field.optString("value");
        if (!value.isEmpty() && !MotherMentorHeiSchedule.validDate(
                MotherMentorHeiSchedule.parseDate(value), min, max)) {
            set(form, root, key, "");
            for (View view : controls(root, key)) if (view instanceof EditText) {
                ((EditText) view).setError(context.getString(R.string.mothermentor_mother_child_invalid_date));
            }
        }
    }

    private static void set(JSONObject form, View root, String key, String value) {
        JSONObject field = MotherMentorMotherChildUtils.field(form, key);
        if (field == null || value.equals(field.optString("value"))) return;
        MotherMentorMotherChildUtils.put(form, key, value);
        for (View view : controls(root, key)) {
            if (view instanceof EditText) {
                if ("date_picker".equals(field.optString("type"))) {
                    view.setTag(com.vijay.jsonwizard.R.id.locale_independent_value, value);
                }
                if (!value.contentEquals(((EditText) view).getText())) ((EditText) view).setText(value);
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
        LocalDate stop = MotherMentorChildEidHeiUtils.cessation(values);
        for (Stage stage : MotherMentorHeiSchedule.routineStages()) {
            if (tests.containsKey(stage)) continue;
            if (stage == Stage.SIX_WEEKS && !day.isBefore(dob.plusMonths(9))) continue;
            if ((stage == Stage.NINE_MONTHS || stage == Stage.AFTER_WEANING)
                    && !day.isBefore(dob.plusMonths(18))) continue;
            LocalDate date = MotherMentorHeiSchedule.dueDate(stage, dob, stop);
            if (date != null) return context.getString(R.string.mothermentor_mother_hei_next,
                    label(context, stage), MotherMentorMotherPregnancyUtils.displayDate(date));
        }
        return context.getString(R.string.mothermentor_mother_hei_none);
    }

    public static String label(Context context, Stage stage) {
        if (stage == Stage.AT_BIRTH) return context.getString(R.string.mothermentor_eid_direct_at_birth);
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
