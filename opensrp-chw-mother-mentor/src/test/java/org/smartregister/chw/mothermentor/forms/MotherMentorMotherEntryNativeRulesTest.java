package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.ART_INITIATED_BEFORE_VISIT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.ATTENDANCE_TYPE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FOLLOW_UP_STATUS;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MAMA_KITUO_ANACHOHAMIA;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MAMA_KITUO_KIPYA;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MAMA_NOT_FOUND_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.MAMA_SABABU_NYINGINE_TEXT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.NEW_PMTCT_ART_INITIATION;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.VISIT_ART_START_DATE;

import android.app.Application;

import com.vijay.jsonwizard.activities.JsonFormActivity;
import com.vijay.jsonwizard.rules.RulesEngineFactory;
import com.vijay.jsonwizard.utils.FormUtils;

import org.jeasy.rules.api.Facts;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

/** Exercise the SDK's dependency discovery before evaluating relevance; never supply facts manually. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherEntryNativeRulesTest {
    private static final String RULES = "mothermentor_mother_visit_relevance.yml";
    private static final String[] LANGUAGES = {"json.form", "json.form-sw"};

    @Test
    public void returnStatusReceivesTheNativeAttendanceFactInBothLanguages() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_visit_type");
            JsonFormActivity activity = nativeActivity(form);
            for (String attendance : new String[] {"new", "repeat"}) {
                set(form, ATTENDANCE_TYPE, attendance);
                Facts facts = nativeFacts(activity, field(form, FOLLOW_UP_STATUS));
                assertFact(facts, ATTENDANCE_TYPE, attendance);
                assertEquals(language + ":" + attendance, "repeat".equals(attendance),
                        relevant(activity, facts));
            }
        }
    }

    @Test
    public void newAttendanceBypassesAllFollowUpQuestionsEvenWithStaleAnswers() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_visit_type");
            set(form, ATTENDANCE_TYPE, "new");
            set(form, FOLLOW_UP_STATUS, "not_found");
            JsonFormActivity activity = nativeActivity(form);
            for (String reason : new String[] {"deceased", "moved_area", "moved_facility", "other"}) {
                set(form, MAMA_NOT_FOUND_REASON, reason);
                for (String key : new String[] {FOLLOW_UP_STATUS, MAMA_NOT_FOUND_REASON,
                        MAMA_SABABU_NYINGINE_TEXT, MAMA_KITUO_ANACHOHAMIA, MAMA_KITUO_KIPYA}) {
                    Facts facts = nativeFacts(activity, field(form, key));
                    assertFact(facts, ATTENDANCE_TYPE, "new");
                    assertFalse(language + ":" + reason + ":" + key, relevant(activity, facts));
                }
            }
        }
    }

    @Test
    public void returningNotFoundBranchesReceiveStatusAndReasonFactsInBothLanguages() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_visit_type");
            set(form, ATTENDANCE_TYPE, "repeat");
            JsonFormActivity activity = nativeActivity(form);
            for (String status : new String[] {"found", "not_found"}) {
                set(form, FOLLOW_UP_STATUS, status);
                Facts reasonFacts = nativeFacts(activity, field(form, MAMA_NOT_FOUND_REASON));
                assertFact(reasonFacts, ATTENDANCE_TYPE, "repeat");
                assertFact(reasonFacts, FOLLOW_UP_STATUS, status);
                assertEquals("not_found".equals(status), relevant(activity, reasonFacts));
                for (String reason : new String[] {"deceased", "moved_area", "moved_facility", "other"}) {
                    set(form, MAMA_NOT_FOUND_REASON, reason);
                    for (String[] branch : new String[][] {
                            {MAMA_SABABU_NYINGINE_TEXT, "other"},
                            {MAMA_KITUO_ANACHOHAMIA, "moved_area"},
                            {MAMA_KITUO_KIPYA, "moved_facility"}}) {
                        Facts facts = nativeFacts(activity, field(form, branch[0]));
                        assertFact(facts, ATTENDANCE_TYPE, "repeat");
                        assertFact(facts, FOLLOW_UP_STATUS, status);
                        assertFact(facts, MAMA_NOT_FOUND_REASON, reason);
                        assertEquals(language + ":" + status + ":" + reason + ":" + branch[0],
                                "not_found".equals(status) && branch[1].equals(reason),
                                relevant(activity, facts));
                    }
                }
            }
        }
    }

    @Test
    public void groupArtRulesReceiveHiddenAttendanceAndPriorInitiationFacts() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language, "mothermentor_visit_group_linkage");
            JsonFormActivity activity = nativeActivity(form);
            for (String attendance : new String[] {"new", "repeat"}) {
                set(form, ATTENDANCE_TYPE, attendance);
                for (String priorArt : new String[] {"false", "true"}) {
                    set(form, ART_INITIATED_BEFORE_VISIT, priorArt);
                    Facts questionFacts = nativeFacts(activity, field(form, NEW_PMTCT_ART_INITIATION));
                    assertFact(questionFacts, ATTENDANCE_TYPE, attendance);
                    assertFact(questionFacts, ART_INITIATED_BEFORE_VISIT, priorArt);
                    boolean asksArt = "new".equals(attendance) && "false".equals(priorArt);
                    assertEquals(asksArt, relevant(activity, questionFacts));
                    for (String answer : new String[] {"", "no", "yes"}) {
                        set(form, NEW_PMTCT_ART_INITIATION, answer);
                        Facts dateFacts = nativeFacts(activity, field(form, VISIT_ART_START_DATE));
                        assertFact(dateFacts, ATTENDANCE_TYPE, attendance);
                        assertFact(dateFacts, ART_INITIATED_BEFORE_VISIT, priorArt);
                        assertFact(dateFacts, NEW_PMTCT_ART_INITIATION, answer);
                        assertEquals(language + ":" + attendance + ":" + priorArt + ":" + answer,
                                asksArt && "yes".equals(answer), relevant(activity, dateFacts));
                    }
                }
            }
        }
    }

    private JsonFormActivity nativeActivity(JSONObject form) throws Exception {
        JsonFormActivity activity = Robolectric.buildActivity(JsonFormActivity.class).get();
        ReflectionHelpers.setField(activity, "mJSONObject", form);
        activity.setRulesEngineFactory(new RulesEngineFactory(
                RuntimeEnvironment.getApplication(), Collections.emptyMap()));
        Map<String, JSONObject> nativeFields = ReflectionHelpers.getField(activity, "formFields");
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            nativeFields.put("step1_" + field.getString("key"), field);
        }
        return activity;
    }

    private Facts nativeFacts(JsonFormActivity activity, JSONObject field) throws Exception {
        JSONObject configuration = field.getJSONObject("relevance")
                .getJSONObject("rules-engine").getJSONObject("ex-rules");
        assertEquals(RULES, configuration.getString("rules-file"));
        String[] address = {"rules-engine", RULES, "step1_" + field.getString("key")};
        JSONObject source = activity.getObjectUsingAddress(address, false);
        Facts facts = ReflectionHelpers.callInstanceMethod(activity, "getEntries",
                ClassParameter.from(String[].class, address), ClassParameter.from(JSONObject.class, source));
        assertFalse(facts.asMap().containsKey("value"));
        return facts;
    }

    private void assertFact(Facts facts, String key, String expected) {
        String name = "step1_" + key;
        assertTrue(name, facts.asMap().containsKey(name));
        Object actual = facts.get(name);
        assertEquals(name, expected, String.valueOf(actual));
    }

    private boolean relevant(JsonFormActivity activity, Facts facts) {
        return activity.getRulesEngineFactory().getRelevance(facts, RULES);
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertTrue(key, field != null && field.length() > 0);
        return field;
    }

    private void set(JSONObject form, String key, String value) throws Exception {
        field(form, key).put("value", value);
    }

    private JSONObject form(String language, String name) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets()
                        .open(language + "/" + name + ".json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }
}
