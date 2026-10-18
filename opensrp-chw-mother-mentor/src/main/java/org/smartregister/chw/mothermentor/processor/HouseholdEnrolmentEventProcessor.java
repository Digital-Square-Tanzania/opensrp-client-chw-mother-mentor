package org.smartregister.chw.mothermentor.processor;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorJsonFormUtils;

public class HouseholdEnrolmentEventProcessor {
    public static final String CONSENT_YES = "ndiyo";
    public static final String CONSENT_NO = "hapana";

    public HouseholdEnrolment process(
            JSONObject form, String fallbackHouseholdId, String fallbackEnrollmentDate)
            throws JSONException {
        if (form == null) {
            throw new JSONException("Household enrolment form is required");
        }

        String encounterType = form.optString(Constants.ENCOUNTER_TYPE);
        if (!Constants.EVENT_TYPE.MAMA_KINARA_HOUSEHOLD_ENROLMENT.equals(encounterType)) {
            throw new JSONException("Unexpected encounter type: " + encounterType);
        }

        JSONArray fields = MotherMentorJsonFormUtils.fields(form, Constants.STEP_ONE);
        String consent = getFieldValue(fields, DBConstants.KEY.CONSENT);
        if (!CONSENT_YES.equals(consent) && !CONSENT_NO.equals(consent)) {
            throw new JSONException(
                    "Household enrolment " + DBConstants.KEY.CONSENT + " must be ndiyo or hapana");
        }

        String householdId =
                StringUtils.defaultIfBlank(
                        getFieldValue(fields, DBConstants.KEY.HOUSEHOLD_ID), fallbackHouseholdId);
        if (StringUtils.isBlank(householdId)) {
            throw new JSONException(
                    "Household enrolment " + DBConstants.KEY.HOUSEHOLD_ID + " is required");
        }

        String enrollmentDate =
                StringUtils.defaultIfBlank(
                        getFieldValue(fields, DBConstants.KEY.ENROLLMENT_DATE),
                        fallbackEnrollmentDate);
        setFieldValue(fields, DBConstants.KEY.HOUSEHOLD_ID, householdId);
        setFieldValue(fields, DBConstants.KEY.ENROLLMENT_DATE, enrollmentDate);
        form.put(org.smartregister.util.JsonFormUtils.ENTITY_ID, householdId);

        return new HouseholdEnrolment(householdId, consent, enrollmentDate);
    }

    private String getFieldValue(JSONArray fields, String key) throws JSONException {
        JSONObject field = getField(fields, key);
        return field == null ? "" : field.optString(org.smartregister.util.JsonFormUtils.VALUE);
    }

    private void setFieldValue(JSONArray fields, String key, String value) throws JSONException {
        if (StringUtils.isBlank(value)) {
            return;
        }

        JSONObject field = getField(fields, key);
        if (field != null) {
            field.put(org.smartregister.util.JsonFormUtils.VALUE, value);
        }
    }

    private JSONObject getField(JSONArray fields, String key) throws JSONException {
        if (fields == null) {
            return null;
        }

        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (key.equals(field.optString("key"))) {
                return field;
            }
        }
        return null;
    }

    public static class HouseholdEnrolment {
        private final String householdId;
        private final String consent;
        private final String enrollmentDate;

        HouseholdEnrolment(String householdId, String consent, String enrollmentDate) {
            this.householdId = householdId;
            this.consent = consent;
            this.enrollmentDate = enrollmentDate;
        }

        public String getHouseholdId() {
            return householdId;
        }

        public String getConsent() {
            return consent;
        }

        public String getEnrollmentDate() {
            return enrollmentDate;
        }

        public boolean hasConsent() {
            return CONSENT_YES.equals(consent);
        }
    }
}
