package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;
import static org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.hasReceivedResult;
import static org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils.NEVER_BREASTFED;

import androidx.annotation.Keep;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.ArrayList;
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
            DBConstants.KEY.MAMA_HEI_TEST_RESULT, MAMA_HEI_TEST_RECEIVED_DATE,
            DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA));

    private static final List<String> CHILD_FIELDS = Arrays.asList(MAMA_CHILD_PATH,
            MAMA_CARRIED_WEANING_DATE, MAMA_PREVIOUS_WEANING_DATE, MAMA_PREVIOUS_BREASTFEEDING_STATUS,
            MAMA_HEI_TEST_HISTORY, MTOTO_SUPPRESSED_TESTS, MAMA_HEI_ASSESSMENT_DATE,
            MAMA_HEI_DUE_TESTS, MAMA_HEI_DUE_TEST, MAMA_HEI_DUE_DATE,
            MAMA_CHILD_AGE_NOTE, MAMA_HEI_NEXT_TEST_NOTE, MTOTO_CLOSED_FOLLOWUP_NOTE);

    private MotherMentorMotherChildUtils() {}

    public static boolean isChildField(String key) {
        return CHILD_FIELDS.contains(key) || ANSWER_FIELDS.contains(key);
    }

    public static boolean applies(Map<String, String> values) {
        if (values == null || !MotherMentorMotherPregnancyUtils.isFound(values)) return false;
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
        LocalDate stop = MotherMentorChildEidHeiUtils.cessation(values);
        List<Stage> due = new ArrayList<>(MotherMentorHeiSchedule.due(birth(values), stop,
                parseTests(values.get(DBConstants.KEY.MAMA_HEI_TEST_HISTORY)), assessment));
        due.removeAll(MotherMentorChildEidRepository.suppressedTests(values.get(DBConstants.KEY.MTOTO_SUPPRESSED_TESTS)));
        return due;
    }

    public static boolean isComplete(Map<String, String> values, LocalDate assessment) {
        return isComplete(values, assessment, false);
    }

    public static boolean isComplete(
            Map<String, String> values, LocalDate assessment, boolean hasBreastfeedingHistory) {
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
        if ("not_breastfeeding".equals(breastfeeding)) {
            if (!MotherMentorHeiSchedule.validDate(
                    MotherMentorChildEidHeiUtils.cessation(values), dob, assessment)) return false;
        } else if (!"breastfeeding".equals(breastfeeding)
                && !(NEVER_BREASTFED.equals(breastfeeding) && !hasBreastfeedingHistory)) {
            return false;
        }
        if (!MotherMentorMotherBirthTest.isComplete(values, assessment)) return false;
        List<Stage> due = due(values, assessment);
        if (due.isEmpty()) return StringUtils.isBlank(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE));
        if ("no".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) return true;
        if (!"yes".equals(values.get(DBConstants.KEY.MAMA_HEI_TEST_DONE))) return false;
        Stage stage = Stage.fromCode(values.get(DBConstants.KEY.MAMA_HEI_TEST_TYPE));
        LocalDate testDate = MotherMentorHeiSchedule.parseDate(values.get(MAMA_HEI_TEST_DATE));
        if (stage == null || !due.contains(stage)
                || !MotherMentorHeiSchedule.validDate(testDate, dob, assessment)) return false;
        String result = values.get(DBConstants.KEY.MAMA_HEI_TEST_RESULT);
        if ("pending".equals(result)) return true;
        if (!hasReceivedResult(result)) return false;
        // Visits recorded before this field was introduced have no receipt-date observation.
        if (values.containsKey(MAMA_HEI_TEST_RECEIVED_DATE)
                && !MotherMentorHeiSchedule.validDate(MotherMentorHeiSchedule.parseDate(
                        values.get(MAMA_HEI_TEST_RECEIVED_DATE)), testDate, assessment)) return false;
        return !"positive".equals(result) || Arrays.asList("yes", "no")
                .contains(values.get(DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA));
    }

    public static TerminalOutcome terminalOutcome(Map<String, String> values, LocalDate assessment) {
        if (!applies(values) || !isComplete(values, assessment)) return TerminalOutcome.NONE;
        if ("deceased".equals(values.get(DBConstants.KEY.MAMA_CHILD_STATUS))) return TerminalOutcome.CHILD_DECEASED;
        if (MotherMentorMotherBirthTest.isRequired(values, assessment)
                && "yes".equals(values.get(MAMA_BIRTH_TEST_DONE))
                && "positive".equals(values.get(MAMA_BIRTH_TEST_RESULT))
                && "yes".equals(values.get(MAMA_BIRTH_TEST_CONFIRMED))) {
            return TerminalOutcome.CHILD_CONFIRMED_POSITIVE;
        }
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
        if (applies(values) && "alive".equals(values.get(MAMA_CHILD_STATUS))) {
            MotherMentorMotherBirthTest.normalize(form, values, assessment);
        } else {
            for (String key : MotherMentorMotherBirthTest.ANSWERS) put(form, key, "");
        }
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
                    DBConstants.KEY.MAMA_HEI_TEST_RESULT, MAMA_HEI_TEST_RECEIVED_DATE,
                    DBConstants.KEY.HEI_CHANYA_IMETHIBITISHWA)) put(form, key, "");
        } else {
            String result = values.get(MAMA_HEI_TEST_RESULT);
            if (!hasReceivedResult(result)) put(form, MAMA_HEI_TEST_RECEIVED_DATE, "");
            if (!"positive".equals(result)) put(form, HEI_CHANYA_IMETHIBITISHWA, "");
        }
    }

    public static void prepare(JSONObject form, MotherMentorMotherChildRepository.State state,
            LocalDate serviceDate) {
        put(form, DBConstants.KEY.MAMA_HEI_ASSESSMENT_DATE, serviceDate.toString());
        put(form, DBConstants.KEY.MAMA_HEI_TEST_HISTORY, serializeTests(state.getSamplingHistory()));
        put(form, DBConstants.KEY.MTOTO_SUPPRESSED_TESTS,
                MotherMentorChildEidRepository.encodeStages(state.getTracingExcludedTests()));
        prepareBreastfeeding(form, state);
        put(form, DBConstants.KEY.MAMA_PREVIOUS_WEANING_DATE, date(state.getWeaningDate()));
        fill(form, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE,
                values(form).getOrDefault(DBConstants.KEY.MAMA_HEI_TEST_TYPE, ""));
        fill(form, DBConstants.KEY.MAMA_TAREHE_ACHA_KUNYONYESHA, date(state.getWeaningDate()));
        // Existing pending/positive-unconfirmed tests were already performed; ask for their result again.
        for (Map.Entry<Stage, TestRecord> entry : state.getSamplingHistory().entrySet()) {
            TestRecord record = entry.getValue();
            if (!record.requiresFollowUp() || state.getTracingExcludedTests().contains(entry.getKey())) continue;
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_DONE, "yes");
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_TYPE, entry.getKey().getCode());
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_TYPE_CHOICE, entry.getKey().getCode());
            fill(form, DBConstants.KEY.MAMA_HEI_TEST_DATE, date(record.getDate()));
            if (!"unknown".equals(record.getResult())) {
                fill(form, DBConstants.KEY.MAMA_HEI_TEST_RESULT, record.getResult());
            }
            if ("positive".equals(record.getResult())) {
                Map<String, String> answers = values(form);
                if ("positive".equals(answers.get(MAMA_HEI_TEST_RESULT))
                        && entry.getKey() == Stage.fromCode(answers.get(MAMA_HEI_TEST_TYPE))
                        && record.getDate() != null && record.getDate().equals(
                                MotherMentorHeiSchedule.parseDate(answers.get(MAMA_HEI_TEST_DATE)))) {
                    fill(form, MAMA_HEI_TEST_RECEIVED_DATE, date(record.getReceivedDate()));
                }
                fill(form, HEI_CHANYA_IMETHIBITISHWA, "no");
            }
            break;
        }
    }

    /** Prepares the shared feeding question from recorded evidence for this child. */
    public static void prepareBreastfeeding(JSONObject form, MotherMentorMotherChildRepository.State state) {
        JSONObject field = field(form, MAMA_BREASTFEEDING_STATUS);
        if (field == null) return;
        String status = state.getBreastfeedingStatus();
        if (state.hasBreastfeedingHistory()) {
            JSONArray options = field.optJSONArray("options");
            for (int i = options == null ? 0 : options.length(); i > 0; i--) {
                JSONObject option = options.optJSONObject(i - 1);
                if (option != null && NEVER_BREASTFED.equals(option.optString("key"))) {
                    options.remove(i - 1);
                }
            }
            if (NEVER_BREASTFED.equals(field.optString("value"))) {
                put(form, MAMA_BREASTFEEDING_STATUS, "");
            }
            if (NEVER_BREASTFED.equals(status)) status = "";
        } else if ("not_breastfeeding".equals(status) && state.getWeaningDate() == null) {
            // A legacy non-breastfeeding enrollment does not tell us whether breastfeeding ever started.
            status = "";
        }
        put(form, MAMA_PREVIOUS_BREASTFEEDING_STATUS, status);
        fill(form, MAMA_BREASTFEEDING_STATUS, status);
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
                    .put("received_date", date(record.getReceivedDate()))
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
                        entry.optBoolean("confirmed"), MotherMentorHeiSchedule.parseDate(entry.optString("service_date")),
                        MotherMentorHeiSchedule.parseDate(entry.optString("received_date"))));
            }
        } catch (Exception ignored) { }
        return result;
    }

    private static String date(LocalDate value) { return value == null ? "" : value.toString("dd-MM-yyyy"); }
}
