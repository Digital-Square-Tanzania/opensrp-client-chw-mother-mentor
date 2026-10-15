package org.smartregister.chw.mothermentor_sample.processor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.smartregister.chw.mothermentor.processor.HouseholdEnrolmentEventProcessor;

public class HouseholdEnrolmentEventProcessorTest {

    @Test
    public void testProcessConsentGivenUsesFallbackHouseholdData() throws Exception {
        JSONObject form = form("ndiyo", "", "");

        HouseholdEnrolmentEventProcessor.HouseholdEnrolment result =
                new HouseholdEnrolmentEventProcessor().process(form, "family-123", "2026-06-23");

        Assert.assertTrue(result.hasConsent());
        Assert.assertEquals("family-123", result.getHouseholdId());
        Assert.assertEquals("2026-06-23", result.getEnrollmentDate());
        Assert.assertEquals("family-123", form.getString("entity_id"));
        Assert.assertEquals("family-123", field(form, "household_id").getString("value"));
        Assert.assertEquals("2026-06-23", field(form, "enrollment_date").getString("value"));
    }

    @Test
    public void testProcessNoConsent() throws Exception {
        JSONObject form = form("hapana", "family-456", "2026-06-23");

        HouseholdEnrolmentEventProcessor.HouseholdEnrolment result =
                new HouseholdEnrolmentEventProcessor().process(form, null, null);

        Assert.assertFalse(result.hasConsent());
        Assert.assertEquals("family-456", result.getHouseholdId());
    }

    @Test(expected = JSONException.class)
    public void testProcessRejectsMissingConsent() throws Exception {
        new HouseholdEnrolmentEventProcessor().process(form("", "family-456", "2026-06-23"), null, null);
    }

    private JSONObject form(String consent, String householdId, String enrollmentDate) throws JSONException {
        JSONArray fields = new JSONArray()
                .put(new JSONObject().put("key", "household_id").put("value", householdId))
                .put(new JSONObject().put("key", "enrollment_date").put("value", enrollmentDate))
                .put(new JSONObject().put("key", "consent").put("value", consent));
        return new JSONObject()
                .put("count", "1")
                .put("encounter_type", "mama_kinara_household_enrolment")
                .put("entity_id", "")
                .put("step1", new JSONObject().put("fields", fields));
    }

    private JSONObject field(JSONObject form, String key) throws JSONException {
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (key.equals(field.getString("key"))) {
                return field;
            }
        }
        throw new JSONException("Missing field " + key);
    }
}
