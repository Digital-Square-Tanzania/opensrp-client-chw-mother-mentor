package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;

import com.vijay.jsonwizard.activities.JsonFormActivity;
import com.vijay.jsonwizard.rules.RuleConstant;
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
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorHouseholdCounsellingActionHelper;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdCounselling;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.domain.tag.FormTag;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdCounsellingFormTest {
    private static final String[] LANGUAGES = {"json.form", "json.form-sw"};

    @Test
    public void newFormsReplaceSexAggregatesWithFourConditionalRoleCounts() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language);
            for (String key : new String[] {"household_members_found", "counselled_male", "counselled_female"}) {
                assertEquals(key, 0, FormUtils.getFieldFromForm(form, key).length());
            }
            JSONArray options = field(form, "participants").getJSONArray("options");
            assertEquals(6, options.length());
            assertTrue(field(form, "participants").getJSONObject("v_required").getBoolean("value"));
            for (String role : new String[] {"child", "relative", "neighbour", "other"}) {
                JSONObject count = field(form, "counselled_" + role + "_count");
                assertTrue(count.getJSONObject("v_required").getBoolean("value"));
                assertEquals("1", count.getJSONObject("v_min").getString("value"));
                assertNotNull(count.getJSONObject("relevance"));
            }
            assertTrue(field(form, "other").getJSONObject("v_required").getBoolean("value"));
            assertTrue(field(form, "counselled_total").getBoolean("read_only"));
            assertEquals("2", field(form, "household_counselling_schema").getString("value"));
            if ("json.form-sw".equals(language)) {
                assertEquals("Walioshiriki", field(form, "participants").getString("label"));
                assertEquals("Mwenzi", options.getJSONObject(1).getString("text"));
            }
        }
    }

    @Test
    public void selectingAChildRequiresItsCountBeforeTheActionCanComplete() throws Exception {
        JSONObject form = form("json.form");
        field(form, "participants").put("value", new JSONArray().put("child"));
        JSONObject schema = new JSONObject().put("key", "household_counselling_schema").put("value", "2");
        if (FormUtils.getFieldFromForm(form, "household_counselling_schema") == null) {
            form.getJSONObject("step1").getJSONArray("fields").put(schema);
        }
        MotherMentorHouseholdCounsellingActionHelper helper = new MotherMentorHouseholdCounsellingActionHelper();
        helper.onPayloadReceived(form.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
    }

    @Test
    public void nativeLiveCalculationCountsOnlySelectedRolesInBothLanguages() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language);
            assertEquals("0", calculated(form, "", "", "", "", ""));
            assertEquals("1", calculated(form, "mother", "8", "9", "10", "11"));
            assertEquals("2", calculated(form, "mother,partner", "8", "9", "10", "11"));
            assertEquals("16", calculated(form, "mother,partner,child,relative,neighbour,other", "2", "3", "4", "5"));
            assertEquals("5", calculated(form, "child,relative", "2", "3", "77", "88"));
            assertEquals("0", calculated(form, "child", "", "", "", ""));
            assertEquals("0", calculated(form, "child", "invalid", "", "", ""));
            assertEquals("399998", calculated(form, "mother,partner,child,relative,neighbour,other", "99999", "99999", "99999", "99999"));
        }
    }

    @Test
    public void nativeRelevanceAndCalculationKeepTheSameNamedCheckboxFacts() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = selected(form(language), "mother");
            JsonFormActivity activity = Robolectric.buildActivity(JsonFormActivity.class).get();
            ReflectionHelpers.setField(activity, "mJSONObject", form);
            activity.setRulesEngineFactory(new RulesEngineFactory(
                    RuntimeEnvironment.getApplication(), Collections.emptyMap()));
            Map<String, JSONObject> nativeFields = ReflectionHelpers.getField(activity, "formFields");
            JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
            for (int index = 0; index < fields.length(); index++) {
                JSONObject field = fields.getJSONObject(index);
                nativeFields.put("step1_" + field.getString("key"), field);
            }
            for (String key : new String[] {"counselled_child_count", "counselled_relative_count",
                    "counselled_neighbour_count", "counselled_other_count", "other"}) {
                Facts relevance = nativeFacts(activity, field(form, key), "relevance");
                assertTrue(key, relevance.asMap().containsKey("step1_participants"));
                assertFalse(key, relevance.asMap().containsKey("value"));
                assertEquals(Collections.singletonList("mother"), relevance.get("step1_participants"));
            }
            Facts calculation = nativeFacts(activity, field(form, "counselled_total"), "calculation");
            assertEquals(Integer.valueOf(0), calculation.get("step1_counselled_child_count"));
            assertEquals("1", activity.getRulesEngineFactory().getCalculation(calculation,
                    "mothermentor_household_counselling_calculation.yml"));

            String[] roles = {"child", "relative", "neighbour", "other"};
            List<String> selected = new ArrayList<>(Arrays.asList("mother", "partner"));
            int total = 2;
            for (int index = 0; index < roles.length; index++) {
                selected.add(roles[index]);
                selected(form, selected.toArray(new String[0]));
                String key = "counselled_" + roles[index] + "_count";
                Facts relevance = nativeFacts(activity, field(form, key), "relevance");
                assertTrue(relevance.asMap().containsKey("step1_participants"));
                assertFalse(relevance.asMap().containsKey("value"));
                assertTrue(activity.getRulesEngineFactory().getRelevance(relevance,
                        "mothermentor_household_counselling_relevance.yml"));
                field(form, key).put("value", Integer.toString(index + 2));
                total += index + 2;
                assertEquals(Integer.toString(total), nativeCalculated(activity, form));
            }
            assertTrue(activity.getRulesEngineFactory().getRelevance(
                    nativeFacts(activity, field(form, "other"), "relevance"),
                    "mothermentor_household_counselling_relevance.yml"));

            selected(form, "mother", "partner");
            for (String key : new String[] {"counselled_child_count", "counselled_relative_count",
                    "counselled_neighbour_count", "counselled_other_count", "other"}) {
                assertFalse(activity.getRulesEngineFactory().getRelevance(
                        nativeFacts(activity, field(form, key), "relevance"),
                        "mothermentor_household_counselling_relevance.yml"));
                ReflectionHelpers.callInstanceMethod(activity, "clearHiddenViewsValues",
                        ClassParameter.from(JSONObject.class, field(form, key)),
                        ClassParameter.from(String.class, "step1:" + key));
                assertFalse(field(form, key).has("value"));
            }
            assertEquals("2", nativeCalculated(activity, form));
            selected(form, "mother", "partner", "child");
            assertEquals("2", nativeCalculated(activity, form));
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper(form).evaluateStatusOnPayload());
            field(form, "counselled_child_count").put("value", "3");
            assertEquals("5", nativeCalculated(activity, form));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper(form).evaluateStatusOnPayload());
        }
    }

    @Test
    public void motherAndPartnerCountOnceAndUntickedRoleAnswersAreCleared() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = selected(form(language), "mother", "partner");
            for (String role : new String[] {"child", "relative", "neighbour", "other"}) {
                field(form, "counselled_" + role + "_count").put("value", "7");
            }
            field(form, "other").put("value", "Old description");
            field(form, "counselled_total").put("value", "99999");
            MotherMentorHouseholdCounsellingActionHelper helper = helper(form);
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            JSONObject saved = new JSONObject(helper.postProcess(form.toString()));
            assertEquals("2", field(saved, "counselled_total").getString("value"));
            for (String role : new String[] {"child", "relative", "neighbour", "other"}) {
                assertEquals("", field(saved, "counselled_" + role + "_count").getString("value"));
            }
            assertEquals("", field(saved, "other").getString("value"));
        }
    }

    @Test
    public void everyCountedRoleRequiresAPositiveWholeNumber() throws Exception {
        for (String role : new String[] {"child", "relative", "neighbour", "other"}) {
            for (String invalid : new String[] {"", "0", "-1", "1.5", "one", "100000"}) {
                JSONObject form = selected(form("json.form"), role);
                field(form, "counselled_" + role + "_count").put("value", invalid);
                field(form, "other").put("value", "Other person");
                assertEquals(role + ":" + invalid, BaseMotherMentorVisitAction.Status.PENDING,
                        helper(form).evaluateStatusOnPayload());
            }
            JSONObject form = selected(form("json.form"), role);
            field(form, "counselled_" + role + "_count").put("value", "1");
            field(form, "other").put("value", "Other person");
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper(form).evaluateStatusOnPayload());
        }
    }

    @Test
    public void otherNeedsAnIdentificationAndAtLeastOneRoleMustBeSelected() throws Exception {
        JSONObject form = selected(form("json.form"), "other");
        field(form, "counselled_other_count").put("value", "2");
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper(form).evaluateStatusOnPayload());
        field(form, "other").put("value", "Juma");
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper(form).evaluateStatusOnPayload());
        for (String role : new String[] {"", "unknown"}) {
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING,
                    helper(selected(form("json.form"), role)).evaluateStatusOnPayload());
        }
    }

    @Test
    public void nativeOptionFlagsUseStableKeysRatherThanTranslatedLabels() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = form(language);
            JSONObject participants = field(form, "participants");
            participants.remove("value");
            JSONArray options = participants.getJSONArray("options");
            for (int index = 0; index < options.length(); index++) {
                options.getJSONObject(index).put("value", index < 2);
            }
            MotherMentorHouseholdCounsellingActionHelper helper = helper(form);
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            assertEquals("2", field(new JSONObject(helper.postProcess(form.toString())), "counselled_total").getString("value"));
        }
    }

    @Test
    public void legacyPayloadsKeepTheirOriginalCountsWithoutRoleConversion() throws Exception {
        JSONObject legacy = new JSONObject().put("count", "1").put("step1", new JSONObject()
                .put("fields", new JSONArray()
                        .put(answer("participants", "[mother, child]"))
                        .put(answer("household_members_found", "9"))
                        .put(answer("counselled_male", "3"))
                        .put(answer("counselled_female", "4"))
                        .put(answer("counselled_total", "7"))));
        String original = legacy.toString();
        MotherMentorHouseholdCounsellingActionHelper helper = helper(legacy);
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        assertNull(helper.postProcess(original));
        assertEquals(original, legacy.toString());
    }

    @Test
    public void rawCombinedCheckboxObservationRetainsEveryRoleForCompletion() throws Exception {
        JSONArray observations = new JSONArray()
                .put(observation("household_counselling_schema", "2"))
                .put(observation("participants", "mother", "child", "other"))
                .put(observation("counselled_child_count", "2"))
                .put(observation("counselled_other_count", "1"));
        Map<String, String> values = MotherMentorHouseholdCounselling.observationValues(observations);
        assertEquals(3, MotherMentorHouseholdCounselling.selectedRoles(values.get("participants")).size());
        assertFalse(MotherMentorHouseholdCounselling.isComplete(values));
        values.put("other", "Relative from another household");
        assertTrue(MotherMentorHouseholdCounselling.isComplete(values));
        assertEquals(4, MotherMentorHouseholdCounselling.total(values));
    }

    @Test
    public void nativeEventRoundTripPersistsRoleCountsAndEveryCombinedRole() throws Exception {
        for (String language : LANGUAGES) {
            JSONObject form = selected(form(language), "mother", "partner", "child", "other");
            field(form, "counselled_child_count").put("value", "2");
            field(form, "counselled_other_count").put("value", "1");
            field(form, "other").put("value", "Other person");
            MotherMentorHouseholdCounsellingActionHelper helper = helper(form);
            JSONObject processed = new JSONObject(helper.postProcess(form.toString()));
            Event event = org.smartregister.util.JsonFormUtils.createEvent(
                    processed.getJSONObject("step1").getJSONArray("fields"), new JSONObject(),
                    new FormTag(), "simulated-household", "MotherMentor Services", "ec_family");
            JSONArray observations = new JSONArray();
            for (Obs observation : event.getObs()) {
                observations.put(new JSONObject().put("fieldCode", observation.getFieldCode())
                        .put("values", new JSONArray(observation.getValues())));
            }
            Map<String, String> values = MotherMentorHouseholdCounselling.observationValues(observations);
            assertEquals(4, MotherMentorHouseholdCounselling.selectedRoles(values.get("participants")).size());
            assertEquals("2", values.get("household_counselling_schema"));
            assertEquals("2", values.get("counselled_child_count"));
            assertEquals("1", values.get("counselled_other_count"));
            assertEquals("5", values.get("counselled_total"));
            assertTrue(MotherMentorHouseholdCounselling.isComplete(values));
            assertEquals(5, MotherMentorHouseholdCounselling.total(values));
            assertFalse(values.containsKey("counselled_male"));
            assertFalse(values.containsKey("counselled_female"));
        }
    }

    @Test
    public void existingLegacyDraftResumesTheOriginalFormAndPreservesItsRawObservations() throws Exception {
        org.smartregister.util.FormUtils previous = ReflectionHelpers.getStaticField(
                org.smartregister.util.FormUtils.class, "instance");
        try {
            for (String language : LANGUAGES) {
                org.smartregister.util.FormUtils forms = mock(org.smartregister.util.FormUtils.class);
                when(forms.getFormJson(anyString())).thenAnswer(call -> readForm(language, call.getArgument(0)));
                ReflectionHelpers.setStaticField(org.smartregister.util.FormUtils.class, "instance", forms);
                Map<String, List<VisitDetail>> details = new HashMap<>();
                for (String[] answer : new String[][] {{"participants", "mother, child"},
                        {"household_members_found", "9"}, {"counselled_male", "3"},
                        {"counselled_female", "4"}, {"counselled_total", "7"}}) {
                    VisitDetail detail = new VisitDetail();
                    detail.setVisitKey(answer[0]);
                    detail.setDetails(answer[1]);
                    details.put(answer[0], Collections.singletonList(detail));
                }
                MotherMentorHouseholdCounsellingActionHelper helper = new MotherMentorHouseholdCounsellingActionHelper();
                BaseMotherMentorVisitAction action = new BaseMotherMentorVisitAction.Builder(
                        RuntimeEnvironment.getApplication(), "Counselling")
                        .withFormName("mother_mentor_household_members_who_received_counselling")
                        .withDetails(details).withHelper(helper).build();
                JSONObject resumed = new JSONObject(action.getJsonPayload());
                assertTrue(resumed.getJSONObject("step1").getString("title").contains(
                        language.endsWith("-sw") ? "muundo wa zamani" : "legacy format"));
                assertEquals("9", field(resumed, "household_members_found").getString("value"));
                assertEquals("3", field(resumed, "counselled_male").getString("value"));
                assertEquals("4", field(resumed, "counselled_female").getString("value"));
                assertEquals("7", field(resumed, "counselled_total").getString("value"));
                assertEquals(0, FormUtils.getFieldFromForm(resumed, "household_counselling_schema").length());
                assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, action.getActionStatus());
                Event event = org.smartregister.util.JsonFormUtils.createEvent(
                        resumed.getJSONObject("step1").getJSONArray("fields"), new JSONObject(),
                        new FormTag(), "simulated-household", "MotherMentor Services", "ec_family");
                Map<String, String> raw = new HashMap<>();
                for (Obs observation : event.getObs()) {
                    raw.put(observation.getFieldCode(), observation.getValues().get(0).toString());
                }
                assertEquals("9", raw.get("household_members_found"));
                assertEquals("3", raw.get("counselled_male"));
                assertEquals("4", raw.get("counselled_female"));
                assertEquals("7", raw.get("counselled_total"));
                assertFalse(raw.containsKey("counselled_child_count"));
                assertFalse(raw.containsKey("household_counselling_schema"));

                // The same helper lifecycle must not retain another client's legacy selection.
                helper.onJsonFormLoaded(form(language).toString(), RuntimeEnvironment.getApplication(),
                        Collections.emptyMap());
                assertNull(helper.getPreProcessed());
                assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            }
        } finally {
            ReflectionHelpers.setStaticField(org.smartregister.util.FormUtils.class, "instance", previous);
        }
    }

    @Test
    public void unrelatedVisitDetailsDoNotSelectTheLegacyCounsellingForm() throws Exception {
        Map<String, List<VisitDetail>> details = new HashMap<>();
        VisitDetail purpose = new VisitDetail();
        purpose.setDetails("routine_follow_up");
        details.put("purpose_of_visit", Collections.singletonList(purpose));
        MotherMentorHouseholdCounsellingActionHelper helper = new MotherMentorHouseholdCounsellingActionHelper();
        helper.onJsonFormLoaded(form("json.form").toString(), RuntimeEnvironment.getApplication(), details);
        assertNull(helper.getPreProcessed());
    }

    private JSONObject readForm(String language, String name) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(
                language + "/" + name + ".json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }

    private JSONObject observation(String key, String... values) throws Exception {
        return new JSONObject().put("fieldCode", key).put("values", new JSONArray(Arrays.asList(values)));
    }

    private JSONObject answer(String key, String value) throws Exception {
        return new JSONObject().put("key", key).put("value", value);
    }

    private MotherMentorHouseholdCounsellingActionHelper helper(JSONObject form) {
        MotherMentorHouseholdCounsellingActionHelper helper = new MotherMentorHouseholdCounsellingActionHelper();
        helper.onPayloadReceived(form.toString());
        return helper;
    }

    private JSONObject selected(JSONObject form, String... roles) throws Exception {
        JSONObject participants = field(form, "participants");
        participants.put("value", new JSONArray(Arrays.asList(roles)));
        JSONArray options = participants.getJSONArray("options");
        for (int index = 0; index < options.length(); index++) {
            JSONObject option = options.getJSONObject(index);
            option.put("value", Arrays.asList(roles).contains(option.getString("key")));
        }
        return form;
    }

    private String calculated(JSONObject form, String roles, String child, String relative,
            String neighbour, String other) throws Exception {
        Facts facts = new Facts();
        facts.put(RuleConstant.SELECTED_RULE, "step1_counselled_total");
        facts.put("step1_participants", roles.isEmpty() ? Collections.emptyList() : Arrays.asList(roles.split(",")));
        facts.put("step1_counselled_child_count", child);
        facts.put("step1_counselled_relative_count", relative);
        facts.put("step1_counselled_neighbour_count", neighbour);
        facts.put("step1_counselled_other_count", other);
        String file = field(form, "counselled_total").getJSONObject("calculation")
                .getJSONObject("rules-engine").getJSONObject("ex-rules").getString("rules-file");
        return new RulesEngineFactory(RuntimeEnvironment.getApplication(), Collections.emptyMap())
                .getCalculation(facts, file);
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }

    private Facts nativeFacts(JsonFormActivity activity, JSONObject field, String logic) throws Exception {
        JSONObject configuration = field.getJSONObject(logic);
        String[] address;
        if (configuration.has("rules-engine")) {
            String file = configuration.getJSONObject("rules-engine").getJSONObject("ex-rules")
                    .getString("rules-file");
            address = new String[] {"rules-engine", file, "step1_" + field.getString("key")};
        } else {
            address = configuration.keys().next().split(":");
        }
        JSONObject source = activity.getObjectUsingAddress(address, false);
        return ReflectionHelpers.callInstanceMethod(activity, "getEntries",
                ClassParameter.from(String[].class, address), ClassParameter.from(JSONObject.class, source));
    }

    private String nativeCalculated(JsonFormActivity activity, JSONObject form) throws Exception {
        Facts facts = nativeFacts(activity, field(form, "counselled_total"), "calculation");
        assertTrue(facts.asMap().containsKey("step1_participants"));
        assertFalse(facts.asMap().containsKey("value"));
        return activity.getRulesEngineFactory().getCalculation(facts,
                "mothermentor_household_counselling_calculation.yml");
    }

    private JSONObject form(String language) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(
                language + "/mother_mentor_household_members_who_received_counselling.json");
                Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(scanner.next());
        }
    }
}
