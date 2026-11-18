package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.dao.MotherMentorChildEidDao;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

/** Direct enrollment uses the existing HEI form with the registered child as its identity. */
public final class MotherMentorChildEidDirectEnrollment {
    public static final String SOURCE = "direct";
    public static final String CONFIRMATION_NEEDED = "confirmation_needed";
    public static final String CONFIRMATION_REFERRED = "confirmation_referred";
    public static final String HEI_PATTERN = "[0-9]{2}-[0-9]{2}-[0-9]{4}-[0-9]{6}-C[0-9]{2}";
    private static final String NO_TEST = "no_test_has_been_done_yet";
    private static final String WEANED_LAST_TEST = "direct_enrollment_last_test_weaned";
    private static final String RULES = "mothermentor_child_eid_direct_enrollment.yml";

    private MotherMentorChildEidDirectEnrollment() {}

    public static JSONObject prepareForm(Context context, JSONObject form, Map<String, String> child,
            String householdName) throws JSONException {
        LocalDate today = LocalDate.now();
        LocalDate dob = birthDate(context, child, today);
        JSONArray registration = MotherMentorJsonFormUtils.fields(form, "step1");
        remove(registration, MOTHER_CTC_NUMBER, HEI_FOLLOW_UP_NUMBER, CHILD_SEX);
        readOnly(registration, CHILD_NAME, name(child));
        readOnly(registration, CHILD_DOB, MotherMentorMotherPregnancyUtils.displayDate(dob));
        registration.put(readOnlyDisplay("direct_enrollment_age", context.getString(R.string.mothermentor_eid_direct_age),
                MotherMentorChildEidLabels.age(context, dob.toString(), today)));
        registration.put(readOnlyDisplay("direct_enrollment_household",
                context.getString(R.string.mothermentor_eid_direct_household), householdName));
        JSONObject number = find(registration, HEI_NUMBER);
        JSONObject relevance = number.getJSONObject("relevance");
        int index = indexOf(registration, HEI_NUMBER);
        registration.put(index, heiField(context).put("relevance", relevance));
        registration.put(field("direct_hei_format", "toaster_notes")
                .put("text", context.getString(R.string.mothermentor_eid_direct_hei_format))
                .put("toaster_type", "info").put("relevance", relevance));
        find(registration, "hei_toaster_info").put("text", context.getString(R.string.mothermentor_eid_direct_advise));

        JSONObject breastfeeding = form.getJSONObject("step2");
        JSONObject tests = form.getJSONObject("step3");
        form.put("step2", step(context.getString(R.string.mothermentor_eid_direct_caregiver),
                new JSONArray().put(text(MTOTO_CAREGIVER_NAME, context.getString(R.string.mothermentor_eid_direct_caregiver), true))
                        .put(caregiverRelationship(context))
                        .put(text(MTOTO_CAREGIVER_PHONE, context.getString(R.string.mothermentor_eid_direct_phone), false)
                                .put("edit_type", "number").put("v_regex", constraint("^(?:[0-9]{10})?$",
                                        context.getString(R.string.mothermentor_eid_direct_phone_invalid))))));
        breastfeeding.put("next", "step4");
        JSONArray feeding = breastfeeding.getJSONArray("fields");
        JSONObject feedingStatus = find(feeding, BREASTFEEDING_STATUS);
        JSONArray choices = feedingStatus.getJSONArray("options");
        choices.put(indexOf(choices, "not_breast_feeding"), option("never_breastfed",
                context.getString(R.string.mothermentor_eid_direct_never)));
        JSONObject weaning = find(feeding, WEANING_DATE);
        weaning.put("min_date", dob.toString("dd-MM-yyyy"));
        weaning.put("relevance", answer("step3:" + BREASTFEEDING_STATUS, "stop_breast_feeding"));
        form.put("step3", breastfeeding);

        JSONArray fields = tests.getJSONArray("fields");
        remove(fields, LAST_TEST_RESULTS_RETURNED);
        JSONObject last = find(fields, LAST_TEST);
        JSONObject result = find(fields, LAST_TEST_RESULT);
        result.put("relevance", rule());
        result.getJSONArray("options").put(option("pending", context.getString(R.string.mothermentor_eid_result_pending)));
        JSONArray stages = last.getJSONArray("options");
        if (today.isBefore(dob.plusMonths(9))) remove(stages, "dbs_9");
        if (today.isBefore(dob.plusWeeks(6))) remove(stages, "dbs_6");
        last.put("relevance", rule());
        JSONObject weaned = new JSONObject(last.toString());
        weaned.put("key", WEANED_LAST_TEST).put("openmrs_entity_id", WEANED_LAST_TEST);
        if (!today.isBefore(dob.plusMonths(4))) {
            weaned.getJSONArray("options").put(option(Stage.AFTER_WEANING.getCode(),
                    MotherMentorChildEidLabels.test(context, Stage.AFTER_WEANING.getCode())));
        }
        fields = new JSONArray().put(last).put(weaned).put(result);
        fields.put(field(MTOTO_ENTRY_TEST_DATE, "date_picker")
                .put("hint", context.getString(R.string.mothermentor_eid_direct_test_date))
                .put("min_date", dob.toString("dd-MM-yyyy")).put("max_date", "today").put("expanded", false)
                .put("v_required", constraint("true", context.getString(R.string.mothermentor_eid_direct_test_date_invalid)))
                .put("relevance", answer("step4:" + LAST_TEST_RESULT, "pending")));
        tests.remove("next");
        tests.put("fields", fields);
        form.put("step4", tests);
        form.put("entity_id", value(child, BASE_ENTITY_ID));
        return form;
    }

    public static void prepareForEvent(Context context, SQLiteDatabase db, JSONObject form,
            Map<String, String> child, String householdName) throws JSONException {
        LocalDate day = LocalDate.now();
        LocalDate dob = birthDate(context, child, day);
        String childId = value(child, BASE_ENTITY_ID);
        Map<String, String> answers = MotherMentorMotherChildUtils.values(form);
        String registered = value(answers, HEI_REGISTERED);
        String hei = "yes".equals(registered) ? value(answers, HEI_NUMBER) : "";
        String caregiver = value(answers, MTOTO_CAREGIVER_NAME);
        String relationship = value(answers, MTOTO_CAREGIVER_RELATIONSHIP);
        String phone = value(answers, MTOTO_CAREGIVER_PHONE);
        if (!Arrays.asList("yes", "no").contains(registered) || caregiver.isEmpty()
                || relationshipLabel(relationship) == 0) throw error(context, R.string.mothermentor_eid_direct_required);
        if (!phone.isEmpty() && !phone.matches("[0-9]{10}")) {
            throw error(context, R.string.mothermentor_eid_direct_phone_invalid);
        }
        validateHeiNumber(context, db, childId, hei);
        String feeding = value(answers, BREASTFEEDING_STATUS);
        LocalDate weaning = "stop_breast_feeding".equals(feeding)
                ? MotherMentorHeiSchedule.parseDate(value(answers, WEANING_DATE)) : null;
        if (!Arrays.asList("breast_feeding", "stop_breast_feeding", "never_breastfed").contains(feeding)) {
            throw error(context, R.string.mothermentor_eid_direct_required);
        }
        if ("stop_breast_feeding".equals(feeding) && !MotherMentorHeiSchedule.validDate(weaning, dob, day)) {
            throw error(context, R.string.mothermentor_eid_direct_weaning_invalid);
        }
        String test = value(answers, WEANED_LAST_TEST);
        if (weaning == null || test.isEmpty()) test = value(answers, LAST_TEST);
        Stage last = enrollmentStage(test);
        if (!NO_TEST.equals(test) && !eligibleLast(last, dob, weaning, day)) {
            throw error(context, R.string.mothermentor_eid_direct_last_test_invalid);
        }
        String result = last == null ? "" : value(answers, LAST_TEST_RESULT);
        if (last != null && !Arrays.asList("negative", "positive", "pending").contains(result)) {
            throw error(context, R.string.mothermentor_eid_direct_required);
        }
        LocalDate sample = "pending".equals(result)
                ? MotherMentorHeiSchedule.parseDate(value(answers, MTOTO_ENTRY_TEST_DATE)) : null;
        if ("pending".equals(result) && !MotherMentorHeiSchedule.validDate(sample, dob, day)) {
            throw error(context, R.string.mothermentor_eid_direct_test_date_invalid);
        }

        Set<Stage> completed = completedThrough(last);
        Stage missed = null;
        LocalDate due = null;
        String state = "none";
        if ("positive".equals(result)) state = CONFIRMATION_NEEDED;
        else if ("pending".equals(result)) state = MotherMentorChildEidReturnVisit.AWAITING_RESULT;
        else {
            Map<Stage, TestRecord> history = new EnumMap<>(Stage.class);
            for (Stage stage : completed) history.put(stage, new TestRecord("negative", null, false, day));
            List<Stage> eligible = MotherMentorHeiSchedule.eligibleForSampling(dob, weaning, history, day);
            for (Stage stage : eligible) {
                LocalDate scheduled = MotherMentorHeiSchedule.dueDate(stage, dob, weaning);
                if (MotherMentorChildEidRepository.isPastGrace(day, scheduled)) {
                    missed = stage;
                    due = scheduled;
                    break;
                }
            }
            if (missed == null) {
                Stage next = eligible.isEmpty() ? MotherMentorHeiSchedule.next(dob, weaning, history, day)
                        : eligible.get(0);
                if (next == null) throw error(context, R.string.mothermentor_eid_direct_no_missed);
                throw new IllegalArgumentException(context.getString(R.string.mothermentor_eid_direct_not_due,
                        MotherMentorChildEidLabels.test(context, next.getCode()),
                        MotherMentorMotherPregnancyUtils.displayDate(MotherMentorHeiSchedule.dueDate(next, dob, weaning))));
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(RELATIONAL_ID, value(child, RELATIONAL_ID));
        fields.put(MOTHER_BASE_ENTITY_ID, "");
        fields.put(MOTHER_NAME, "");
        fields.put(CHILD_NAME, name(child));
        fields.put(CHILD_DOB, dob.toString());
        fields.put(CHILD_SEX, value(child, GENDER));
        fields.put(HEI_NUMBER, hei);
        fields.put(MTOTO_CAREGIVER_NAME, caregiver);
        fields.put(MTOTO_CAREGIVER_RELATIONSHIP, relationship);
        fields.put(MTOTO_CAREGIVER_PHONE, phone);
        fields.put(BREASTFEEDING_STATUS, feeding);
        fields.put(WEANING_DATE, iso(weaning));
        fields.put(LAST_TEST, test);
        fields.put(LAST_TEST_RESULT, result);
        fields.put(LAST_TEST_RESULTS_RETURNED, last == null ? "" : sample == null ? "yes" : "no");
        fields.put(MTOTO_ENTRY_SOURCE, SOURCE);
        fields.put(MTOTO_ENTRY_TEST, last == null ? "" : last.getMetadataCode());
        fields.put(MTOTO_ENTRY_RESULT, result);
        fields.put(MTOTO_ENTRY_TEST_DATE, iso(sample));
        fields.put(MTOTO_MOTHER_ENROLLMENT_ID, "");
        fields.put(MTOTO_MISSED_TEST, missed == null ? "" : missed.getMetadataCode());
        fields.put(MTOTO_MISSED_DUE, iso(due));
        fields.put(MTOTO_RUFAA_STATE, state);
        fields.put(MTOTO_RUFAA_TEST, last == null || "none".equals(state) ? "" : last.getMetadataCode());
        fields.put(MTOTO_SUPPRESSED_TESTS, MotherMentorChildEidRepository.encodeStages(completed));
        fields.put(MTOTO_STATUS, "active");
        fields.put(IS_CLOSED, "0");
        form.put("entity_id", childId);
        for (Map.Entry<String, String> entry : fields.entrySet()) setEventValue(form, entry.getKey(), entry.getValue());
    }

    public static void validateHeiNumber(Context context, SQLiteDatabase db, String childId, String value) {
        if (StringUtils.isBlank(value)) return;
        if (!value.matches(HEI_PATTERN)) throw error(context, R.string.mothermentor_eid_direct_hei_invalid);
        if (MotherMentorChildEidDao.isHeiNumberRegistered(db, childId, value)) {
            throw error(context, R.string.mothermentor_eid_direct_hei_duplicate);
        }
    }

    public static JSONObject heiField(Context context) throws JSONException {
        return field(HEI_NUMBER, "mask_edit_text")
                .put("hint", context.getString(R.string.mothermentor_eid_direct_hei))
                .put("mask", "##-##-####-######-C##").put("mask_hint", "0000000000000000")
                .put("allowed_chars", "0123456789").put("edit_type", "number")
                .put("v_regex", constraint("^(?:" + HEI_PATTERN + ")?$",
                        context.getString(R.string.mothermentor_eid_direct_hei_invalid)));
    }

    public static int relationshipLabel(String relationship) {
        switch (StringUtils.defaultString(relationship)) {
            case "father": return R.string.mothermentor_eid_direct_father;
            case "grandparent": return R.string.mothermentor_eid_direct_grandparent;
            case "other_relative": return R.string.mothermentor_eid_direct_relative;
            case "other_caregiver": return R.string.mothermentor_eid_direct_other;
            default: return 0;
        }
    }

    public static void addOptionalVisitNumber(Context context, JSONObject form) throws JSONException {
        JSONArray fields = MotherMentorJsonFormUtils.fields(form, "step1");
        fields.put(field("direct_hei_help", "toaster_notes")
                .put("text", context.getString(R.string.mothermentor_eid_direct_hei_help))
                .put("toaster_type", "info").put("relevance", rule()));
        fields.put(heiField(context).put("relevance", rule()));
    }

    private static JSONObject caregiverRelationship(Context context) throws JSONException {
        JSONArray options = new JSONArray();
        for (String code : Arrays.asList("father", "grandparent", "other_relative", "other_caregiver")) {
            options.put(option(code, context.getString(relationshipLabel(code))));
        }
        return field(MTOTO_CAREGIVER_RELATIONSHIP, "native_radio")
                .put("label", context.getString(R.string.mothermentor_eid_direct_relationship))
                .put("options", options).put("v_required",
                        constraint("true", context.getString(R.string.mothermentor_eid_direct_required)));
    }

    private static LocalDate birthDate(Context context, Map<String, String> child, LocalDate day) {
        LocalDate date = MotherMentorHeiSchedule.parseBirthDate(value(child, DOB));
        if (date == null || date.isAfter(day) || !day.isBefore(date.plusMonths(24))
                || value(child, BASE_ENTITY_ID).isEmpty()) {
            throw error(context, R.string.mothermentor_eid_direct_child_unavailable);
        }
        return date;
    }

    private static Stage enrollmentStage(String value) {
        if ("dbs_6".equals(value)) return Stage.SIX_WEEKS;
        if ("dbs_9".equals(value)) return Stage.NINE_MONTHS;
        return Stage.fromCode(value);
    }

    private static boolean eligibleLast(Stage stage, LocalDate dob, LocalDate stop, LocalDate day) {
        if (stage == Stage.AT_BIRTH) return true;
        if (stage == Stage.EIGHTEEN_MONTHS || stage == null) return false;
        LocalDate date = MotherMentorHeiSchedule.dueDate(stage, dob, stop);
        return date != null && !day.isBefore(date);
    }

    private static Set<Stage> completedThrough(Stage last) {
        Set<Stage> result = new LinkedHashSet<>();
        if (last == null) return result;
        for (Stage stage : Stage.values()) {
            if (stage != Stage.AFTER_WEANING || stage == last) result.add(stage);
            if (stage == last) break;
        }
        return result;
    }

    private static JSONObject step(String title, JSONArray fields) throws JSONException {
        return new JSONObject().put("title", title).put("next", "step3").put("fields", fields);
    }

    private static JSONObject field(String key, String type) throws JSONException {
        return new JSONObject().put("key", key).put("type", type).put("openmrs_entity", "concept")
                .put("openmrs_entity_id", key).put("openmrs_entity_parent", "");
    }

    private static JSONObject text(String key, String label, boolean required) throws JSONException {
        JSONObject field = field(key, "edit_text").put("hint", label);
        if (required) field.put("v_required", constraint("true", label));
        return field;
    }

    private static JSONObject option(String code, String label) throws JSONException {
        return new JSONObject().put("key", code).put("text", label)
                .put("openmrs_entity", "concept").put("openmrs_entity_id", code).put("openmrs_entity_parent", "");
    }

    private static JSONObject constraint(String value, String error) throws JSONException {
        return new JSONObject().put("value", value).put("err", error);
    }

    private static JSONObject answer(String field, String code) throws JSONException {
        return new JSONObject().put(field,
                new JSONObject().put("type", "string").put("ex", "equalTo(., \"" + code + "\")"));
    }

    private static JSONObject rule() throws JSONException {
        return new JSONObject().put("rules-engine",
                new JSONObject().put("ex-rules", new JSONObject().put("rules-file", RULES)));
    }

    private static void remove(JSONArray from, String... keys) {
        for (String key : keys) {
            int index = indexOf(from, key);
            if (index >= 0) from.remove(index);
        }
    }

    private static int indexOf(JSONArray array, String key) {
        for (int i = 0; i < array.length(); i++) {
            if (key.equals(array.optJSONObject(i).optString("key"))) return i;
        }
        return -1;
    }

    private static JSONObject find(JSONArray array, String key) throws JSONException {
        return array.getJSONObject(indexOf(array, key));
    }

    private static void readOnly(JSONArray array, String key, String value) throws JSONException {
        JSONObject original = find(array, key);
        original.remove("relevance");
        original.put("value", value);
    }

    private static JSONObject readOnlyDisplay(String key, String label, String value) throws JSONException {
        return text(key, label, false).put("read_only", true).put("editable", false).put("value", value);
    }

    private static void setEventValue(JSONObject form, String key, String value) throws JSONException {
        JSONObject existing = MotherMentorMotherChildUtils.field(form, key);
        if (existing == null) {
            MotherMentorJsonFormUtils.fields(form, "step1").put(field(key, "hidden").put("value", value));
        } else {
            existing.put("value", value);
        }
    }

    private static String name(Map<String, String> child) {
        return StringUtils.normalizeSpace(value(child, FIRST_NAME) + " " + value(child, MIDDLE_NAME)
                + " " + value(child, LAST_NAME));
    }

    private static String value(Map<String, String> values, String key) {
        return StringUtils.trimToEmpty(values.get(key));
    }

    private static String iso(LocalDate d) { return d == null ? "" : d.toString(); }

    private static IllegalArgumentException error(Context context, int message) {
        return new IllegalArgumentException(context.getString(message));
    }
}
