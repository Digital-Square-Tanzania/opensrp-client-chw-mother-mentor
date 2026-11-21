package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MAMA_CHILD_STATUS;

import android.content.Context;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherResultEvent.Answers;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherResultEvent.PendingTest;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;

import java.util.List;
import java.util.Map;

/** The answers live in the numbered child section of the maternal visit. */
public final class MotherMentorAwaitedResults {
    public static final String WORKFLOW = "mama_awaited_workflow";
    public static final String SAMPLE = "sample";
    public static final String RECEIVED = "received";
    public static final String RESULT = "result";
    public static final String RECEIPT = "receipt";
    public static final String CONFIRMATION_DONE = "confirmation_done";
    public static final String CONFIRMATION = "confirmation";
    private static final String PREFIX = "mama_awaited_";

    private MotherMentorAwaitedResults() {}

    public static String key(Stage stage, String field) {
        return PREFIX + stage.getCode() + "_" + field;
    }

    static boolean isField(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    public static boolean enabled(Map<String, String> values) {
        return "true".equals(values.get(WORKFLOW));
    }

    public static boolean isComplete(List<PendingTest> samples, Map<String, String> values, LocalDate day) {
        if (!"alive".equals(values.get(MAMA_CHILD_STATUS))) return true;
        for (PendingTest sample : samples) {
            Stage stage = sample.getStage();
            if (!sample.getKey().equals(values.get(key(stage, SAMPLE)))) return false;
            String received = values.get(key(stage, RECEIVED));
            if ("no".equals(received)) continue;
            if (!"yes".equals(received) || !answers(sample, values).isValid(sample, day)) return false;
        }
        return true;
    }

    public static Answers received(PendingTest sample, Map<String, String> values, LocalDate day) {
        if (!sample.getKey().equals(values.get(key(sample.getStage(), SAMPLE)))
                || !"yes".equals(values.get(key(sample.getStage(), RECEIVED)))) return null;
        Answers result = answers(sample, values);
        return result.isValid(sample, day) ? result : null;
    }

    private static Answers answers(PendingTest sample, Map<String, String> values) {
        Stage stage = sample.getStage();
        LocalDate receipt = MotherMentorHeiSchedule.parseDate(values.get(key(stage, RECEIPT)));
        String confirmation = values.get(key(stage, CONFIRMATION));
        return sample.isAwaitingConfirmation() ? Answers.confirmation("yes", confirmation, receipt)
                : new Answers(values.get(key(stage, RESULT)), receipt,
                        values.get(key(stage, CONFIRMATION_DONE)), confirmation);
    }

    // Preview only. The clinical projection checks the key against the exact outstanding sample.
    static boolean confirmsPositive(Map<String, String> values, LocalDate day) {
        if (!enabled(values) || !"alive".equals(values.get(MAMA_CHILD_STATUS))) return false;
        for (Stage stage : Stage.values()) {
            String sample = values.getOrDefault(key(stage, SAMPLE), "");
            if (sample.isEmpty() || !"yes".equals(values.get(key(stage, RECEIVED)))
                    || !"positive".equals(values.get(key(stage, CONFIRMATION)))) continue;
            boolean awaitingConfirmation = sample.endsWith("/awaiting_confirm");
            if (!awaitingConfirmation && !("positive".equals(values.get(key(stage, RESULT)))
                    && "yes".equals(values.get(key(stage, CONFIRMATION_DONE))))) continue;
            String[] parts = sample.split("/");
            if (parts.length < 4) continue;
            LocalDate test = MotherMentorHeiSchedule.parseDate(parts[parts.length - 2]);
            LocalDate receipt = MotherMentorHeiSchedule.parseDate(values.get(key(stage, RECEIPT)));
            if (MotherMentorHeiSchedule.validDate(receipt, test, day)) return true;
        }
        return false;
    }

    public static void append(Context context, JSONObject form, List<PendingTest> samples,
            Map<String, String> saved, LocalDate day) throws JSONException {
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        fields.put(field(WORKFLOW, "hidden").put("value", "true"));
        for (PendingTest sample : samples) {
            Stage stage = sample.getStage();
            String test = MotherMentorMotherChildUi.label(context, stage);
            String name = sample.isAwaitingConfirmation()
                    ? context.getString(R.string.mothermentor_awaited_confirmation_name, test) : test;
            String received = key(stage, RECEIVED);
            String result = key(stage, RESULT);
            String done = key(stage, CONFIRMATION_DONE);
            String confirmation = key(stage, CONFIRMATION);
            String summary = context.getString(R.string.mothermentor_awaited_test, name, display(sample.getTestDate()));
            if (sample.isOverdue(day)) {
                summary += " · " + context.getString(R.string.mothermentor_eid_result_overdue);
            }
            fields.put(field(key(stage, SAMPLE), "hidden").put("value", sample.getKey()));
            fields.put(note(key(stage, "note"), summary, MAMA_CHILD_STATUS, "alive"));
            String question = context.getString(R.string.mothermentor_awaited_question, name);
            fields.put(radio(received, question, context.getString(R.string.mothermentor_awaited_required), yesNo(context))
                    .put("relevance", relevance(MAMA_CHILD_STATUS, "alive")));
            fields.put(note(key(stage, "follow"), context.getString(R.string.mothermentor_awaited_follow), received, "no"));
            String resultLabel = context.getString(R.string.mothermentor_awaited_result, name);
            if (!sample.isAwaitingConfirmation()) {
                fields.put(radio(result, resultLabel, resultLabel, results(context))
                        .put("relevance", relevance(received, "yes")));
            }
            String dateLabel = context.getString(R.string.mothermentor_awaited_receipt);
            fields.put(field(key(stage, RECEIPT), "date_picker").put("hint", dateLabel)
                    .put("display_format", "dd/MM/yyyy").put("min_date", display(sample.getTestDate()))
                    .put("max_date", display(day)).put("expanded", false)
                    .put("v_required", required(dateLabel)).put("relevance", relevance(received, "yes")));
            JSONArray confirmationOptions = results(context);
            if (!sample.isAwaitingConfirmation()) {
                fields.put(radio(done, context.getString(R.string.mothermentor_awaited_confirmation_done),
                        context.getString(R.string.mothermentor_awaited_required), yesNo(context))
                        .put("relevance", relevance(result, "positive")));
                if (stage != Stage.EIGHTEEN_MONTHS) {
                    confirmationOptions.put(option("pending", context.getString(R.string.mothermentor_eid_result_pending)));
                }
            }
            String confirmationLabel = context.getString(R.string.mothermentor_awaited_confirmation_result);
            fields.put(radio(confirmation, sample.isAwaitingConfirmation() ? resultLabel : confirmationLabel,
                    confirmationLabel, confirmationOptions).put("relevance", relevance(
                            sample.isAwaitingConfirmation() ? received : done, "yes")));
            if (sample.getKey().equals(saved.get(key(stage, SAMPLE)))) {
                for (String answer : new String[]{RECEIVED, RESULT, CONFIRMATION_DONE, CONFIRMATION, RECEIPT}) {
                    String key = key(stage, answer);
                    MotherMentorMotherChildUtils.put(form, key, saved.get(key));
                }
            }
        }
    }

    public static void normalize(JSONObject form, List<PendingTest> samples) {
        Map<String, String> values = MotherMentorMotherChildUtils.values(form);
        for (PendingTest sample : samples) {
            Stage stage = sample.getStage();
            boolean alive = "alive".equals(values.get(MAMA_CHILD_STATUS));
            boolean received = alive && "yes".equals(values.get(key(stage, RECEIVED)));
            if (!alive) {
                MotherMentorMotherChildUtils.put(form, key(stage, SAMPLE), "");
                MotherMentorMotherChildUtils.put(form, key(stage, RECEIVED), "");
            }
            if (!received) {
                for (String field : new String[]{RESULT, RECEIPT, CONFIRMATION_DONE, CONFIRMATION}) {
                    MotherMentorMotherChildUtils.put(form, key(stage, field), "");
                }
            } else if (!sample.isAwaitingConfirmation()) {
                if (!"positive".equals(values.get(key(stage, RESULT)))) {
                    MotherMentorMotherChildUtils.put(form, key(stage, CONFIRMATION_DONE), "");
                    MotherMentorMotherChildUtils.put(form, key(stage, CONFIRMATION), "");
                } else if (!"yes".equals(values.get(key(stage, CONFIRMATION_DONE)))) {
                    MotherMentorMotherChildUtils.put(form, key(stage, CONFIRMATION), "");
                }
            }
        }
    }

    private static String display(LocalDate date) {
        return date.toString("dd-MM-yyyy");
    }

    private static JSONObject note(String key, String text, String dependency, String value) throws JSONException {
        return field(key, "label").put("openmrs_entity", "ui_only").put("text", text)
                .put("relevance", relevance(dependency, value));
    }

    private static JSONObject field(String key, String type) throws JSONException {
        return new JSONObject().put("key", key).put("type", type).put("value", "")
                .put("openmrs_entity", "concept").put("openmrs_entity_id", key).put("openmrs_entity_parent", "");
    }

    private static JSONObject radio(String key, String label, String error, JSONArray options) throws JSONException {
        return field(key, "native_radio").put("label", label).put("options", options).put("v_required", required(error));
    }

    private static JSONObject required(String error) throws JSONException {
        return new JSONObject().put("value", true).put("err", error);
    }

    private static JSONObject relevance(String key, String value) throws JSONException {
        return new JSONObject().put("step1:" + key,
                new JSONObject().put("type", "string").put("ex", "equalTo(., \"" + value + "\")"));
    }

    private static JSONArray yesNo(Context context) throws JSONException {
        return new JSONArray().put(option("yes", context.getString(R.string.mothermentor_awaited_yes)))
                .put(option("no", context.getString(R.string.mothermentor_awaited_no)));
    }

    private static JSONArray results(Context context) throws JSONException {
        return new JSONArray().put(option("negative", context.getString(R.string.mothermentor_eid_negative)))
                .put(option("positive", context.getString(R.string.mothermentor_eid_positive)));
    }

    private static JSONObject option(String code, String label) throws JSONException {
        return new JSONObject().put("key", code).put("text", label).put("openmrs_entity", "concept")
                .put("openmrs_entity_id", code).put("openmrs_entity_parent", "");
    }
}
