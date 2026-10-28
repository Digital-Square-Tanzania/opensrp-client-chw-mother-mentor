package org.smartregister.chw.mothermentor.actionhelper;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Months;
import org.joda.time.Weeks;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;

import timber.log.Timber;

import java.util.Arrays;
import java.util.Map;

/** Child EID test completion and the existing age-based recommendation. */
public class MotherMentorChildEidTestTypeActionHelper extends BaseMotherMentorActionHelper {
    static final String TEST_6_WEEKS = "six_weeks";
    static final String TEST_9_MONTHS = "nine_months";
    static final String TEST_POST_WEANING = "three_months_after_weaning";
    static final String TEST_18_MONTHS = "eighteen_months";
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
        return MotherMentorDao.getChildEidTestEnrollmentValues(memberObject.getBaseEntityId());
    }

    protected DateTime currentTime() {
        return DateTime.now();
    }

    @Override
    public String evaluateSubTitle() {
        return translate(
                "mothermentor_child_eid_test_", getValue(DBConstants.KEY.CHILD_EID_TEST_TYPE));
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(StringUtils.isNotBlank(getValue(DBConstants.KEY.CHILD_EID_TEST_TYPE)));
    }

    @Override
    public String getPreProcessed() {
        String source = sourceForm;
        if (StringUtils.isBlank(source) || memberObject == null) return null;
        try {
            Map<String, String> enrollment = loadEnrollmentValues();
            String recommendation =
                    recommendTest(
                            enrollment.get(DBConstants.KEY.DOB),
                            enrollment.get(DBConstants.KEY.WEANING_DATE),
                            enrollment.get(DBConstants.KEY.LAST_TEST),
                            currentTime());
            if (recommendation == null) return source;
            JSONObject form = new JSONObject(source);
            JSONObject testField =
                    FormUtils.getFieldFromForm(form, DBConstants.KEY.CHILD_EID_TEST_TYPE);
            JSONObject noteField =
                    FormUtils.getFieldFromForm(form, DBConstants.KEY.CHILD_EID_TEST_RECOMMENDATION);
            if (testField != null
                    && StringUtils.isBlank(testField.optString(JsonFormConstants.VALUE))) {
                testField.put(JsonFormConstants.VALUE, recommendation);
            }
            if (noteField != null) {
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
                noteField.put(JsonFormConstants.TEXT, context.getString(messageId, label));
            }
            return form.toString();
        } catch (Exception e) {
            Timber.e(e, "Unable to recommend a Child EID follow-up test");
            return source;
        }
    }

    static String recommendTest(String dob, String weaningDate, String lastTest, DateTime now) {
        DateTime birth = parseDate(dob);
        if (birth == null || birth.isAfter(now)) return null;
        int months =
                Months.monthsBetween(birth.withTimeAtStartOfDay(), now.withTimeAtStartOfDay())
                        .getMonths();
        int weeks =
                Weeks.weeksBetween(birth.withTimeAtStartOfDay(), now.withTimeAtStartOfDay())
                        .getWeeks();
        if (months >= 18) return TEST_18_MONTHS;
        DateTime weaning = parseDate(weaningDate);
        if (weaning != null && !weaning.isAfter(now) && !weaning.plusMonths(3).isAfter(now))
            return TEST_POST_WEANING;
        if (months >= 9) return "dbs_9".equals(lastTest) ? null : TEST_9_MONTHS;
        if (weeks >= 4)
            return "dbs_6".equals(lastTest) || "dbs_9".equals(lastTest) ? null : TEST_6_WEEKS;
        return null;
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
                return org.joda.time.format.DateTimeFormat.forPattern(pattern).parseDateTime(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        try {
            return new DateTime(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
