package org.smartregister.chw.mothermentor.util;

import androidx.annotation.Keep;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Child-block preparation and validation, shared by the checklist, native UI and replay. */
@Keep
public final class MotherMentorMotherChildUtils {
    public enum TerminalOutcome { NONE, CHILD_DECEASED, CHILD_CONFIRMED_POSITIVE, GRADUATED }

    public static final List<String> ANSWER_FIELDS = Collections.unmodifiableList(Arrays.asList(
            DBConstants.KEY.MAMA_CHILD_STATUS, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS,
            DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI,
            DBConstants.KEY.MAMA_HEI_TEST_DONE, DBConstants.KEY.MAMA_HEI_TEST_TYPE,
            DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, DBConstants.KEY.MAMA_HEI_TEST_DATE,
            DBConstants.KEY.MAMA_HEI_TEST_RESULT, DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA));

    private MotherMentorMotherChildUtils() {}

    public static boolean applies(Map<String, String> values) {
        if (values == null || !"2".equals(values.get(DBConstants.KEY.MAMA_VISIT_SCHEMA))
                || !"repeat".equals(values.get(DBConstants.KEY.ATTENDANCE_TYPE))
                || !"found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))) return false;
        String original = values.get(DBConstants.KEY.MAMA_ENROLLMENT_PREGNANCY_STATUS);
        boolean enrolledWithChild = Arrays.asList("breast_feeding", "not_pregnant", "bf", "none").contains(original);
        boolean delivered = "pregnant".equals(original)
                && "not_pregnant".equals(values.get(DBConstants.KEY.VISIT_PREGNANCY_STATUS))
                && Arrays.asList("delivered", "preterm").contains(values.get(DBConstants.KEY.MAMA_PREGNANCY_OUTCOME));
        return (enrolledWithChild || delivered) && birth(values) != null;
    }

    public static LocalDate birth(Map<String, String> values) {
        return MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_KUJIFUNGUA));
    }

    public static List<Stage> due(Map<String, String> values, LocalDate assessment) {
        LocalDate stop = "not_breastfeeding".equals(values.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))
                ? MotherMentorHeiSchedule.parseDate(values.get(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)) : null;
        return MotherMentorHeiSchedule.due(birth(values), stop,
                parseTests(values.get(DBConstants.KEY.MAMA_HEI_TEST_HISTORY)), assessment);
    }

    public static boolean isComplete(Map<String, String> values, LocalDate assessment) {
        if (!applies(values)) return true;
        LocalDate dob = birth(values);
        if (assessment == null || dob.isAfter(assessment)) return false;
        String child = values.get(DBConstants.KEY.MAMA_CHILD_STATUS);
        if ("deceased".equals(child)) {
            return MotherMentorHeiSchedule.validDate(MotherMentorHeiSchedule.parseDate(
                    values.get(DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI)), dob, assessment);
        }
        if (!"alive".equals(child)) return false;
        String breastfeeding = values.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS);
        if (!Arrays.asList("breastfeeding", "not_breastfeeding").contains(breastfeeding)) return false;
        if ("not_breastfeeding".equals(breastfeeding)
                && !MotherMentorHeiSchedule.validDate(MotherMentorHeiSchedule.parseDate(
                        values.get(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA)), dob, assessment)) return false;
        List<Stage> due = due(values, assessment);
        if (due.isEmpty()) return StringUtils.isBlank(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE));
        if ("no".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) return true;
        if (!"yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) return false;
        Stage stage = Stage.fromCode(values.get(DBConstants.KEY.MAMA_HEI_TEST_TYPE));
        if (stage == null || !due.contains(stage)
                || !MotherMentorHeiSchedule.validDate(MotherMentorHeiSchedule.parseDate(
                        values.get(DBConstants.KEY.MAMA_HEI_TEST_DATE)), dob, assessment)) return false;
        String result = values.get(DBConstants.KEY.MAMA_HEI_TEST_RESULT);
        if (!Arrays.asList("negative", "positive", "pending").contains(result)) return false;
        return !"positive".equals(result) || Arrays.asList("yes", "no")
                .contains(values.get(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA));
    }

    public static TerminalOutcome terminalOutcome(Map<String, String> values, LocalDate assessment) {
        if (!applies(values) || !isComplete(values, assessment)) return TerminalOutcome.NONE;
        if ("deceased".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS))) return TerminalOutcome.CHILD_DECEASED;
        if (!"yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) return TerminalOutcome.NONE;
        String result = values.get(DBConstants.KEY.MAMA_HEI_TEST_RESULT);
        if ("positive".equals(result) && "yes".equals(values.get(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA))) {
            return TerminalOutcome.CHILD_CONFIRMED_POSITIVE;
        }
        return "negative".equals(result)
                && Stage.EIGHTEEN_MONTHS.getCode().equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_TYPE))
                ? TerminalOutcome.GRADUATED : TerminalOutcome.NONE;
    }

    /** Clears submitted answers from branches which no longer apply. */
    public static void normalize(JSONObject form, LocalDate assessment) {
        Map<String, String> values = values(form);
        if (!applies(values)) {
            for (String key : ANSWER_FIELDS) put(form, key, "");
            put(form, DBConstants.KEY.MAMA_HEI_DUE_TESTS, "[]");
            put(form, DBConstants.KEY.MAMA_HEI_DUE_TEST, "");
            put(form, DBConstants.KEY.MAMA_HEI_DUE_DATE, "");
            return;
        }
        if (!"alive".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS))) {
            for (String key : ANSWER_FIELDS) if (!Arrays.asList(DBConstants.KEY.MAMA_CHILD_STATUS,
                    DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI).contains(key)) put(form, key, "");
            put(form, DBConstants.KEY.MAMA_HEI_DUE_TESTS, "[]");
            put(form, DBConstants.KEY.MAMA_HEI_DUE_TEST, "");
            put(form, DBConstants.KEY.MAMA_HEI_DUE_DATE, "");
            return;
        }
        put(form, DBConstants.KEY.MAMA_TAREHE_MTOTO_ALIFARIKI, "");
        if (!"not_breastfeeding".equals(values.get(DBConstants.KEY.MAMA_BREASTFEEDING_STATUS))) {
            put(form, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, "");
        }
        List<Stage> due = due(values(form), assessment);
        JSONArray codes = new JSONArray(); for (Stage stage : due) codes.put(stage.getCode());
        put(form, DBConstants.KEY.MAMA_HEI_DUE_TESTS, codes.toString());
        Stage first = due.isEmpty() ? null : due.get(0);
        put(form, DBConstants.KEY.MAMA_HEI_DUE_TEST, first == null ? "" : first.getCode());
        LocalDate stop = MotherMentorHeiSchedule.parseDate(values(form).get(DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA));
        LocalDate date = MotherMentorHeiSchedule.dueDate(first, birth(values), stop);
        put(form, DBConstants.KEY.MAMA_HEI_DUE_DATE, date == null ? "" : date.toString());
        if (due.isEmpty()) put(form, DBConstants.KEY.MAMA_HEI_TEST_DONE, "");
        if (!"yes".equals(values(form).get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) {
            for (String key : Arrays.asList(DBConstants.KEY.MAMA_HEI_TEST_TYPE,
                    DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, DBConstants.KEY.MAMA_HEI_TEST_DATE,
                    DBConstants.KEY.MAMA_HEI_TEST_RESULT, DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA)) put(form, key, "");
        } else if (!"positive".equals(values(form).get(DBConstants.KEY.MAMA_HEI_TEST_RESULT))) {
            put(form, DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA, "");
        }
    }

    public static void prepare(JSONObject form, MotherMentorMotherChildRepository.State state,
            LocalDate serviceDate) {
        put(form, DBConstants.KEY.MAMA_HEI_ASSESSMENT_DATE, serviceDate.toString());
        put(form, DBConstants.KEY.MAMA_HEI_TEST_HISTORY, serializeTests(state.getSamplingHistory()));
        put(form, DBConstants.KEY.MAMA_PREVIOUS_BREASTFEEDING_STATUS, state.getBreastfeedingStatus());
        put(form, DBConstants.KEY.MAMA_PREVIOUS_WEANING_DATE, date(state.getWeaningDate()));
        fill(form, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE,
                values(form).getOrDefault(DBConstants.KEY.MAMA_HEI_TEST_TYPE, ""));
        fill(form, DBConstants.KEY.MAMA_BREASTFEEDING_STATUS, state.getBreastfeedingStatus());
        fill(form, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, date(state.getWeaningDate()));
        // Existing pending/positive-unconfirmed tests were already performed; ask for their result again.
        for (Map.Entry<Stage, TestRecord> entry : state.getSamplingHistory().entrySet()) {
            if (!entry.getValue().requiresFollowUp()) continue;
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_DONE, "yes");
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_TYPE, entry.getKey().getCode());
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, entry.getKey().getCode());
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_DATE, date(entry.getValue().getDate()));
            if (!"unknown".equals(entry.getValue().getResult())) {
                fill(form, DBConstants.KEY.MAMA_HEI_TEST_RESULT, entry.getValue().getResult());
            }
            if ("positive".equals(entry.getValue().getResult())) fill(form, DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA, "no");
            break;
        }
    }

    public static Map<String, String> values(JSONObject form) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int step = 1; step <= form.optInt("count", 1); step++) {
            JSONObject page = form.optJSONObject("step" + step);
            JSONArray fields = page == null ? null : page.optJSONArray("fields");
            if (fields == null) continue;
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                if (field != null) values.put(field.optString("key"), field.optString("value"));
            }
        }
        return values;
    }

    public static JSONObject field(JSONObject form, String key) {
        for (int step = 1; step <= form.optInt("count", 1); step++) {
            JSONObject page = form.optJSONObject("step" + step);
            JSONArray fields = page == null ? null : page.optJSONArray("fields");
            if (fields != null) for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.optJSONObject(i);
                if (field != null && key.equals(field.optString("key"))) return field;
            }
        }
        return null;
    }

    public static void put(JSONObject form, String key, String value) {
        JSONObject field = field(form, key);
        if (field != null) try { field.put("value", StringUtils.defaultString(value)); } catch (Exception ignored) { }
    }

    private static void fill(JSONObject form, String key, String value) {
        JSONObject field = field(form, key);
        if (field != null && field.optString("value").isEmpty() && StringUtils.isNotBlank(value)) put(form, key, value);
    }

    public static String serializeTests(Map<Stage, TestRecord> tests) {
        JSONObject result = new JSONObject();
        for (Map.Entry<Stage, TestRecord> entry : tests.entrySet()) try {
            TestRecord record = entry.getValue();
            result.put(entry.getKey().getCode(), new JSONObject().put("result", record.getResult())
                    .put("date", date(record.getDate())).put("confirmed", record.isConfirmed())
                    .put("service_date", date(record.getServiceDate())));
        } catch (Exception ignored) { }
        return result.toString();
    }

    public static Map<Stage, TestRecord> parseTests(String raw) {
        Map<Stage, TestRecord> result = new LinkedHashMap<>();
        try {
            JSONObject values = new JSONObject(StringUtils.defaultIfBlank(raw, "{}"));
            for (Stage stage : Stage.values()) {
                JSONObject entry = values.optJSONObject(stage.getCode());
                if (entry == null || !Arrays.asList("negative", "positive", "pending", "unknown").contains(entry.optString("result"))) continue;
                result.put(stage, new TestRecord(entry.optString("result"),
                        MotherMentorHeiSchedule.parseDate(entry.optString("date")),
                        entry.optBoolean("confirmed"), MotherMentorHeiSchedule.parseDate(entry.optString("service_date"))));
            }
        } catch (Exception ignored) { }
        return result;
    }

    private static String date(LocalDate value) { return value == null ? "" : value.toString("dd-MM-yyyy"); }
}
