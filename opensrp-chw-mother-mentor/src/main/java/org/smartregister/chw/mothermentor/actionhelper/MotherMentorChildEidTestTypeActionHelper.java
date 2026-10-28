package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

import timber.log.Timber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Child EID test eligibility, completion and the existing age-based recommendation. */
public class MotherMentorChildEidTestTypeActionHelper extends BaseMotherMentorActionHelper {
    static final String TEST_6_WEEKS = "six_weeks";
    static final String TEST_9_MONTHS = "nine_months";
    static final String TEST_POST_WEANING = "three_months_after_weaning";
    static final String TEST_18_MONTHS = "eighteen_months";
    static final String TEST_NOT_APPLICABLE = "not_applicable";
    private final MemberObject memberObject;
    private final Runnable onTestChanged;

    public MotherMentorChildEidTestTypeActionHelper(MemberObject memberObject) {
        this(memberObject, null);
    }

    public MotherMentorChildEidTestTypeActionHelper(
            MemberObject memberObject, Runnable onTestChanged) {
        this.memberObject = memberObject;
        this.onTestChanged = onTestChanged;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onTestChanged != null) onTestChanged.run();
    }

    protected Map<String, String> loadEnrollmentValues() {
        return memberObject == null || StringUtils.isBlank(memberObject.getBaseEntityId())
                ? Collections.emptyMap()
                : MotherMentorDao.getChildEidTestEnrollmentValues(memberObject.getBaseEntityId());
    }

    protected DateTime currentTime() {
        return DateTime.now();
    }

    @Override
    public String evaluateSubTitle() {
        if (evaluateStatusOnPayload() != BaseMotherMentorVisitAction.Status.COMPLETED) return "";
        return translate(
                "mothermentor_child_eid_test_", getValue(DBConstants.KEY.CHILD_EID_TEST_TYPE));
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        String selected = getValue(DBConstants.KEY.CHILD_EID_TEST_TYPE);
        if (StringUtils.isBlank(selected)) return BaseMotherMentorVisitAction.Status.PENDING;
        Map<String, String> enrollment = enrollmentValues();
        return completed(
                eligibleTests(
                                enrollment.get(DBConstants.KEY.DOB),
                                enrollment.get(DBConstants.KEY.WEANING_DATE),
                                currentTime())
                        .contains(selected));
    }

    @Override
    public String getPreProcessed() {
        return prepareForm(sourceForm, true);
    }

    @Override
    public String postProcess(String source) {
        // Saved visit details are restored again after preprocessing by the action model.
        String prepared = prepareForm(source, false);
        super.onPayloadReceived(prepared);
        return prepared;
    }

    private Map<String, String> enrollmentValues() {
        try {
            return loadEnrollmentValues();
        } catch (Exception e) {
            Timber.e(e, "Unable to determine Child EID test eligibility");
            return Collections.emptyMap();
        }
    }

    private String prepareForm(String source, boolean prefill) {
        if (StringUtils.isBlank(source)) return null;
        try {
            Map<String, String> enrollment = enrollmentValues();
            List<String> eligible =
                    eligibleTests(
                            enrollment.get(DBConstants.KEY.DOB),
                            enrollment.get(DBConstants.KEY.WEANING_DATE),
                            currentTime());
            String recommendation =
                    recommendTest(eligible, enrollment.get(DBConstants.KEY.LAST_TEST));
            JSONObject form = new JSONObject(source);
            JSONObject testField =
                    FormUtils.getFieldFromForm(form, DBConstants.KEY.CHILD_EID_TEST_TYPE);
            JSONObject noteField =
                    FormUtils.getFieldFromForm(form, DBConstants.KEY.CHILD_EID_TEST_RECOMMENDATION);
            if (testField != null) {
                JSONArray options = testField.optJSONArray(JsonFormConstants.OPTIONS_FIELD_NAME);
                if (options != null) {
                    JSONArray allowed = new JSONArray();
                    for (int i = 0; i < options.length(); i++) {
                        JSONObject option = options.getJSONObject(i);
                        if (eligible.contains(option.optString(JsonFormConstants.KEY))) {
                            allowed.put(option);
                        }
                    }
                    testField.put(JsonFormConstants.OPTIONS_FIELD_NAME, allowed);
                }
                String selected = testField.optString(JsonFormConstants.VALUE);
                if (!eligible.contains(selected)) testField.put(JsonFormConstants.VALUE, "");
                if (prefill && StringUtils.isBlank(testField.optString(JsonFormConstants.VALUE))) {
                    if (eligible.contains(TEST_NOT_APPLICABLE)) {
                        testField.put(JsonFormConstants.VALUE, TEST_NOT_APPLICABLE);
                    } else if (recommendation != null) {
                        testField.put(JsonFormConstants.VALUE, recommendation);
                    }
                }
            }
            if (noteField != null) {
                String message;
                if (eligible.isEmpty()) {
                    message = context.getString(R.string.mothermentor_child_eid_dob_required);
                } else if (eligible.contains(TEST_NOT_APPLICABLE)) {
                    message = context.getString(R.string.mothermentor_child_eid_no_test_due);
                } else if (recommendation == null) {
                    message = context.getString(R.string.mothermentor_child_eid_eligible_tests);
                } else {
                    int labelId =
                            context.getResources()
                                    .getIdentifier(
                                            "mothermentor_child_eid_test_" + recommendation,
                                            "string",
                                            context.getPackageName());
                    String label = labelId == 0 ? recommendation : context.getString(labelId);
                    int messageId =
                            TEST_POST_WEANING.equals(recommendation)
                                    ? R.string.mothermentor_child_eid_weaning_recommendation
                                    : R.string.mothermentor_child_eid_recommendation;
                    message = context.getString(messageId, label);
                }
                noteField.put(JsonFormConstants.TEXT, message);
            }
            return form.toString();
        } catch (Exception e) {
            Timber.e(e, "Unable to prepare eligible Child EID follow-up tests");
            return source;
        }
    }

    static String recommendTest(String dob, String weaningDate, String lastTest, DateTime now) {
        return recommendTest(eligibleTests(dob, weaningDate, now), lastTest);
    }

    private static String recommendTest(List<String> eligible, String lastTest) {
        if (eligible.contains(TEST_18_MONTHS)) return TEST_18_MONTHS;
        if (eligible.contains(TEST_POST_WEANING)) return TEST_POST_WEANING;
        if (eligible.contains(TEST_9_MONTHS)) return "dbs_9".equals(lastTest) ? null : TEST_9_MONTHS;
        if (eligible.contains(TEST_6_WEEKS))
            return "dbs_6".equals(lastTest) || "dbs_9".equals(lastTest) ? null : TEST_6_WEEKS;
        return null;
    }

    private static List<String> eligibleTests(String dob, String weaningDate, DateTime now) {
        DateTime parsedBirth = parseDate(dob);
        LocalDate today = now.toLocalDate();
        if (parsedBirth == null || parsedBirth.toLocalDate().isAfter(today)) {
            return Collections.emptyList();
        }
        LocalDate birth = parsedBirth.toLocalDate();
        List<String> eligible = new ArrayList<>();
        // Preserve the existing HEI eligibility threshold for the six-week test.
        if (!birth.plusWeeks(4).isAfter(today)) eligible.add(TEST_6_WEEKS);
        if (!birth.plusMonths(9).isAfter(today)) eligible.add(TEST_9_MONTHS);
        DateTime parsedWeaning = parseDate(weaningDate);
        if (parsedWeaning != null) {
            LocalDate weaning = parsedWeaning.toLocalDate();
            if (!weaning.isBefore(birth) && !weaning.plusMonths(3).isAfter(today)) {
                eligible.add(TEST_POST_WEANING);
            }
        }
        if (!birth.plusMonths(18).isAfter(today)) eligible.add(TEST_18_MONTHS);
        if (eligible.isEmpty()) eligible.add(TEST_NOT_APPLICABLE);
        return eligible;
    }

    private static DateTime parseDate(String value) {
        if (StringUtils.isBlank(value)) return null;
        for (String pattern :
                Arrays.asList(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd",
                        "dd-MM-yyyy",
                        "dd/MM/yyyy")) {
            try {
                return org.joda.time.format.DateTimeFormat.forPattern(pattern)
                        .withOffsetParsed()
                        .parseDateTime(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        try {
            return org.joda.time.format.ISODateTimeFormat.dateTimeParser()
                    .withOffsetParsed()
                    .parseDateTime(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
