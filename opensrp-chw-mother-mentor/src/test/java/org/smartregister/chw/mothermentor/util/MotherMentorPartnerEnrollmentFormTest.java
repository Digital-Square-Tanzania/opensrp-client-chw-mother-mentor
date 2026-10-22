package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import android.app.Application;

import com.vijay.jsonwizard.utils.FormUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.repository.AllSharedPreferences;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorPartnerEnrollmentFormTest {
    @Test
    public void bothLocalesNavigateFromIdentityToHivQuestionsWithoutRelationshipStep()
            throws Exception {
        for (String locale : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = form(locale);
            Set<String> visited = new HashSet<>();
            Set<String> fieldKeys = new HashSet<>();
            String stepName = "step1";
            while (!stepName.isEmpty()) {
                assertTrue("Cyclic form navigation", visited.add(stepName));
                JSONObject step = form.getJSONObject(stepName);
                JSONArray fields = step.getJSONArray("fields");
                assertTrue(fields.length() > 0);
                for (int i = 0; i < fields.length(); i++) {
                    assertTrue(fieldKeys.add(fields.getJSONObject(i).getString("key")));
                }
                stepName = step.optString("next");
            }
            assertEquals(form.getInt("count"), visited.size());
            assertEquals(3, visited.size());
            assertFalse(fieldKeys.contains(DBConstants.KEY.RELATIONSHIP_TO_MOTHER));
            assertFalse(fieldKeys.contains(DBConstants.KEY.OTHER_RELATIONSHIP));
            assertEquals(
                    DBConstants.KEY.HIV_DISCLOSURE,
                    form.getJSONObject("step2")
                            .getJSONArray("fields")
                            .getJSONObject(0)
                            .getString("key"));
            assertRelevancePointsTo(
                    form, DBConstants.KEY.TEST_RESULT, DBConstants.KEY.HIV_TESTED, "yes");
            assertRelevancePointsTo(
                    form, DBConstants.KEY.ON_CTC, DBConstants.KEY.TEST_RESULT, "positive");
            assertRelevancePointsTo(
                    form, DBConstants.KEY.PARTNER_CTC, DBConstants.KEY.ON_CTC, "yes");
        }
    }

    @Test
    public void enrollmentEventRetainsLinkedIdentitiesAndHivAnswersInBothLocales()
            throws Exception {
        MotherMentorLibrary previous =
                ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        ReflectionHelpers.setStaticField(
                MotherMentorLibrary.class, "instance", mock(MotherMentorLibrary.class));
        try {
            for (String locale : new String[] {"json.form", "json.form-sw"}) {
                JSONObject form = form(locale);
                form.put(DBConstants.KEY.ENTITY_ID, "partner-1063");
                set(form, DBConstants.KEY.MOTHER_BASE_ENTITY_ID, "mother-1063");
                set(form, DBConstants.KEY.RELATIONAL_ID, "partner-household");
                set(form, DBConstants.KEY.PARTNER_LINKING, "yes");
                set(form, DBConstants.KEY.MOTHER_NAME, "Asha Mtemi");
                set(form, DBConstants.KEY.MOTHER_CTC_NUMBER, "12-34-5678-123456");
                set(form, DBConstants.KEY.HIV_DISCLOSURE, "yes");
                set(form, DBConstants.KEY.HIV_TESTED, "yes");
                set(form, DBConstants.KEY.TEST_RESULT, "positive");
                set(form, DBConstants.KEY.ON_CTC, "yes");
                set(form, DBConstants.KEY.PARTNER_CTC, "98-76-5432-654321");
                Event event =
                        MotherMentorJsonFormUtils.processJsonForm(
                                mock(AllSharedPreferences.class), form.toString());
                assertNotNull(event);
                assertEquals("partner-1063", event.getBaseEntityId());
                assertEquals("Mother Mentor Enroll Partner", event.getEventType());
                assertEquals(
                        "mother-1063", observation(event, DBConstants.KEY.MOTHER_BASE_ENTITY_ID));
                assertEquals(
                        "partner-household", observation(event, DBConstants.KEY.RELATIONAL_ID));
                assertEquals("yes", observation(event, DBConstants.KEY.HIV_DISCLOSURE));
                assertEquals("yes", observation(event, DBConstants.KEY.HIV_TESTED));
                assertEquals("positive", observation(event, DBConstants.KEY.TEST_RESULT));
                assertEquals("yes", observation(event, DBConstants.KEY.ON_CTC));
                assertEquals("98-76-5432-654321", observation(event, DBConstants.KEY.PARTNER_CTC));
                assertNull(observation(event, DBConstants.KEY.RELATIONSHIP_TO_MOTHER));
                assertNull(observation(event, DBConstants.KEY.OTHER_RELATIONSHIP));
            }
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }

    private void assertRelevancePointsTo(JSONObject form, String key, String parent, String answer)
            throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        JSONObject relevance = field.getJSONObject("relevance");
        Iterator<String> dependencies = relevance.keys();
        assertTrue(dependencies.hasNext());
        String dependency = dependencies.next();
        assertFalse(dependencies.hasNext());
        assertEquals("step3:" + parent, dependency);
        assertEquals(
                "equalTo(., \"" + answer + "\")",
                relevance.getJSONObject(dependency).getString("ex"));
        JSONArray parentFields = form.getJSONObject("step3").getJSONArray("fields");
        boolean found = false;
        for (int i = 0; i < parentFields.length(); i++) {
            found |= parent.equals(parentFields.getJSONObject(i).getString("key"));
        }
        assertTrue("Missing relevance dependency", found);
    }

    private void set(JSONObject form, String key, String value) throws Exception {
        FormUtils.getFieldFromForm(form, key).put("value", value);
    }

    private String observation(Event event, String key) {
        for (Obs obs : event.getObs()) {
            if (key.equals(obs.getFieldCode())) return obs.getValues().get(0).toString();
        }
        return null;
    }

    private JSONObject form(String locale) throws Exception {
        try (Scanner scanner =
                new Scanner(
                        RuntimeEnvironment.application
                                .getAssets()
                                .open(locale + "/mothermentor_enroll_partner.json"),
                        "UTF-8")) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }
}
