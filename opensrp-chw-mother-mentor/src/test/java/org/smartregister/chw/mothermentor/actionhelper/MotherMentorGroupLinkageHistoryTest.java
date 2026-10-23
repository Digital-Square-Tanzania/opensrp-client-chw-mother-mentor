package org.smartregister.chw.mothermentor.actionhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import com.vijay.jsonwizard.utils.FormUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorJsonFormUtils;
import org.smartregister.repository.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorGroupLinkageHistoryTest {
    private static final String CLIENT_ID = "mother-with-'quote";
    private static final String PSYCHOSOCIAL =
            DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP;
    private static final String IGA = DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP;
    private static final String FORM_NAME = "mothermentor_visit_group_linkage";
    private MotherMentorLibrary previousLibrary;
    private org.smartregister.util.FormUtils previousFormUtils;
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase cipher;
    private String formDirectory;

    @Before
    public void setUp() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        previousFormUtils =
                ReflectionHelpers.getStaticField(org.smartregister.util.FormUtils.class, "instance");
        data = SQLiteDatabase.create(null);
        data.execSQL(
                "CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                        + " processed INTEGER, visit_date TEXT)");
        data.execSQL("CREATE TABLE visit_details (visit_id TEXT, visit_key TEXT, details TEXT)");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        cipher = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        formDirectory = "json.form";
        org.smartregister.util.FormUtils formUtils = mock(org.smartregister.util.FormUtils.class);
        when(formUtils.getFormJson(FORM_NAME)).thenAnswer(call -> loadForm(formDirectory));
        ReflectionHelpers.setStaticField(
                org.smartregister.util.FormUtils.class, "instance", formUtils);
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
        ReflectionHelpers.setStaticField(
                org.smartregister.util.FormUtils.class, "instance", previousFormUtils);
    }

    @Test
    public void firstServiceLeavesBothGroupQuestionsUnansweredInBothLanguages() throws Exception {
        assertTrue(MotherMentorDao.getPriorGroupLinkages(CLIENT_ID, null).isEmpty());
        for (String language : languages()) {
            JSONObject prepared = prepare(loadForm(language), Collections.emptyMap());
            assertValues(prepared, "", "");
            for (String key : new String[] {PSYCHOSOCIAL, IGA}) {
                JSONObject field = FormUtils.getFieldFromForm(prepared, key);
                assertEquals("native_radio", field.getString("type"));
                JSONArray options = field.getJSONArray("options");
                assertEquals("yes", options.getJSONObject(0).getString("key"));
                assertEquals("no", options.getJSONObject(1).getString("key"));
                assertEquals(
                        Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                        options.getJSONObject(2).getString("key"));
            }
        }
    }

    @Test
    public void earlierPsychosocialLinkageDefaultsOnlyThatGroupInBothLanguages() throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "no");
        assertEquals(Collections.singleton(PSYCHOSOCIAL), prior(null));
        for (String language : languages()) {
            assertValues(
                    prepare(loadForm(language), Collections.emptyMap()),
                    Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                    "");
        }
    }

    @Test
    public void earlierIgaLinkageDefaultsOnlyThatGroupInBothLanguages() throws Exception {
        visit("earlier", CLIENT_ID, service(), 0, 100L, "no", "yes");
        assertEquals(Collections.singleton(IGA), prior(null));
        for (String language : languages()) {
            assertValues(
                    prepare(loadForm(language), Collections.emptyMap()),
                    "",
                    Constants.GROUP_LINKAGE.ALREADY_IN_GROUP);
        }
    }

    @Test
    public void normalizedPositiveAndAlreadyLinkedAnswersCountBeforeAndAfterProcessing()
            throws Exception {
        for (int processed : new int[] {0, 1}) {
            for (String answer : new String[] {" YES ", "Ndiyo", " ALREADY_IN_GROUP "}) {
                clearVisits();
                visit("earlier", CLIENT_ID, service(), processed, 100L, answer, answer);
                assertEquals(groups(), prior(null));
                for (String language : languages()) {
                    assertValues(
                            prepare(loadForm(language), Collections.emptyMap()),
                            Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                            Constants.GROUP_LINKAGE.ALREADY_IN_GROUP);
                }
            }
        }
    }

    @Test
    public void noAndMissingAnswersAreNotEvidenceOfLinkage() {
        for (String answer : new String[] {"no", "hapana", "", " ", "null", null}) {
            clearVisits();
            visit("negative", CLIENT_ID, service(), 1, 100L, answer, answer);
            assertTrue(prior(null).isEmpty());
        }
    }

    @Test
    public void lookupBindsTheExactClientIncludingQuotes() {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "yes");
        assertEquals(groups(), prior(null));
        assertTrue(MotherMentorDao.getPriorGroupLinkages("another-client", null).isEmpty());
        assertTrue(MotherMentorDao.getPriorGroupLinkages("' OR 1=1 --", null).isEmpty());
    }

    @Test
    public void enrollmentFollowupOtherClientsAndOtherFieldsDoNotCount() {
        visit("other", "another-client", service(), 1, 100L, "yes", "yes");
        visit(
                "enrollment",
                CLIENT_ID,
                Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT,
                1,
                100L,
                "yes",
                "yes");
        visit(
                "followup",
                CLIENT_ID,
                Constants.EVENT_TYPE.MOTHER_MENTOR_FOLLOW_UP_VISIT,
                1,
                100L,
                "yes",
                "yes");
        visit("other-service", CLIENT_ID, "ANC Home Visit", 1, 100L, "yes", "yes");
        visit("unrelated-answer", CLIENT_ID, service(), 1, 100L, null, null);
        detail("unrelated-answer", DBConstants.KEY.EDUCATION_PROVIDED, "yes");
        assertTrue(prior(null).isEmpty());
    }

    @Test
    public void editingExcludesCurrentLaterAndSameTimestampVisits() {
        visit("earlier", CLIENT_ID, service(), 1, 99L, "yes", "no");
        visit("current", CLIENT_ID, service(), 0, 100L, "no", "yes");
        visit("simultaneous", CLIENT_ID, service(), 1, 100L, "no", "yes");
        visit("later", CLIENT_ID, service(), 1, 101L, "no", "yes");
        assertEquals(Collections.singleton(PSYCHOSOCIAL), prior("current"));
        assertEquals(groups(), prior(null));
    }

    @Test
    public void editingCannotUseTheCurrentVisitsFirstPositiveAnswerAsHistory() throws Exception {
        visit("current", CLIENT_ID, service(), 0, 100L, "yes", "no");
        assertTrue(prior("current").isEmpty());
        Map<String, List<VisitDetail>> details = details("current", "yes", "no");
        for (String language : languages()) {
            assertValues(prepare(loadForm(language), details), "yes", "no");
        }
    }

    @Test
    public void editingVisitLookupMustBelongToTheSameClientAndServiceType() {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "yes");
        visit("other-client", "another-client", service(), 1, 200L, "yes", "yes");
        visit(
                "other-type",
                CLIENT_ID,
                Constants.EVENT_TYPE.MOTHER_MENTOR_FOLLOW_UP_VISIT,
                1,
                200L,
                "yes",
                "yes");
        assertTrue(prior("other-client").isEmpty());
        assertTrue(prior("other-type").isEmpty());
        assertTrue(prior("missing").isEmpty());
    }

    @Test
    public void allSavedAnswersArePreservedIndependentlyInBothLanguages() throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "yes");
        visit("current", CLIENT_ID, service(), 0, 200L, "no", "no");
        for (String language : languages()) {
            for (String psychosocial : new String[] {"yes", "no", "already_in_group"}) {
                for (String iga : new String[] {"yes", "no", "already_in_group"}) {
                    assertValues(
                            prepare(loadForm(language), details("current", psychosocial, iga)),
                            psychosocial,
                            iga);
                }
            }
        }
    }

    @Test
    public void alreadyLinkedSavedVisitContinuesToDefaultOnSubsequentReopening()
            throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "already_in_group", "already_in_group");
        for (int reopening = 0; reopening < 3; reopening++) {
            for (String language : languages()) {
                assertValues(
                        prepare(loadForm(language), Collections.emptyMap()),
                        Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                        Constants.GROUP_LINKAGE.ALREADY_IN_GROUP);
            }
        }
    }

    @Test
    public void helperDefaultsBlankAndWhitespaceFieldsWithoutReplacingAnAnsweredGroup()
            throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "yes");
        for (String language : languages()) {
            JSONObject form = loadForm(language);
            set(form, PSYCHOSOCIAL, " ");
            set(form, IGA, "no");
            assertValues(
                    prepare(form, Collections.emptyMap()),
                    Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                    "no");
        }
    }

    @Test
    public void actionBuilderPreservesHistoryDefaultsThroughSecondDetailPopulation()
            throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "no");
        visit("current", CLIENT_ID, service(), 0, 200L, "", "no");
        for (String language : languages()) {
            formDirectory = language;
            BaseMotherMentorVisitAction action =
                    new BaseMotherMentorVisitAction.Builder(
                                    RuntimeEnvironment.application, "Group Linkage")
                            .withBaseEntityID(CLIENT_ID)
                            .withFormName(FORM_NAME)
                            .withDetails(details("current", "", "no"))
                            .withHelper(new MotherMentorGroupLinkageActionHelper(member(CLIENT_ID)))
                            .build();
            assertValues(
                    new JSONObject(action.getJsonPayload()),
                    Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                    "no");
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, action.getActionStatus());
        }
    }

    @Test
    public void actionBuilderCompletesBothAlreadyLinkedDefaultsWithoutLosingSavedYes()
            throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "yes");
        visit("current", CLIENT_ID, service(), 0, 200L, "", "");
        for (String language : languages()) {
            formDirectory = language;
            for (String saved : new String[] {"", "yes"}) {
                BaseMotherMentorVisitAction action =
                        new BaseMotherMentorVisitAction.Builder(
                                        RuntimeEnvironment.application, "Group Linkage")
                                .withBaseEntityID(CLIENT_ID)
                                .withFormName(FORM_NAME)
                                .withDetails(details("current", saved, ""))
                                .withHelper(
                                        new MotherMentorGroupLinkageActionHelper(member(CLIENT_ID)))
                                .build();
                assertValues(
                        new JSONObject(action.getJsonPayload()),
                        saved.isEmpty() ? Constants.GROUP_LINKAGE.ALREADY_IN_GROUP : saved,
                        Constants.GROUP_LINKAGE.ALREADY_IN_GROUP);
                assertEquals(
                        BaseMotherMentorVisitAction.Status.COMPLETED, action.getActionStatus());
            }
        }
    }

    @Test
    public void helperResetsCurrentVisitWhenReusedForANewVisit() throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", "yes");
        MotherMentorGroupLinkageActionHelper helper =
                new MotherMentorGroupLinkageActionHelper(member(CLIENT_ID));
        helper.onJsonFormLoaded(
                loadForm("json.form").toString(),
                RuntimeEnvironment.application,
                details("earlier", "", ""));
        assertValues(new JSONObject(helper.getPreProcessed()), "", "");
        helper.onJsonFormLoaded(
                loadForm("json.form").toString(),
                RuntimeEnvironment.application,
                Collections.emptyMap());
        assertValues(
                new JSONObject(helper.getPreProcessed()),
                Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                Constants.GROUP_LINKAGE.ALREADY_IN_GROUP);
    }

    @Test
    public void missingClientIdentityDoesNotQueryOrGuessLinkage() throws Exception {
        assertTrue(MotherMentorDao.getPriorGroupLinkages(null, null).isEmpty());
        assertTrue(MotherMentorDao.getPriorGroupLinkages(" ", null).isEmpty());
        for (String client : new String[] {null, " "}) {
            MotherMentorGroupLinkageActionHelper helper =
                    new MotherMentorGroupLinkageActionHelper(member(client));
            helper.onJsonFormLoaded(
                    loadForm("json.form").toString(),
                    RuntimeEnvironment.application,
                    Collections.emptyMap());
            assertValues(new JSONObject(helper.getPreProcessed()), "", "");
        }
        MotherMentorGroupLinkageActionHelper helper = new MotherMentorGroupLinkageActionHelper();
        helper.onJsonFormLoaded(
                loadForm("json.form").toString(),
                RuntimeEnvironment.application,
                Collections.emptyMap());
        assertNull(helper.getPreProcessed());
        verifyNoInteractions(cipher);
    }

    @Test
    public void databaseFailureLeavesQuestionsUnansweredAndPreservesExistingAnswer()
            throws Exception {
        data.execSQL("DROP TABLE visits");
        for (String language : languages()) {
            JSONObject form = loadForm(language);
            set(form, IGA, "no");
            assertValues(prepare(form, Collections.emptyMap()), "", "no");
        }
    }

    @Test
    public void prepopulatedOneGroupStillRequiresTheOtherGroupAnswer() throws Exception {
        visit("earlier", CLIENT_ID, service(), 1, 100L, "yes", null);
        MotherMentorGroupLinkageActionHelper helper =
                new MotherMentorGroupLinkageActionHelper(member(CLIENT_ID));
        helper.onJsonFormLoaded(
                loadForm("json.form").toString(),
                RuntimeEnvironment.application,
                Collections.emptyMap());
        JSONObject prepared = new JSONObject(helper.getPreProcessed());
        helper.onPayloadReceived(prepared.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
        set(prepared, IGA, "no");
        helper.onPayloadReceived(prepared.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        assertFalse(value(prepared, PSYCHOSOCIAL).equals("yes"));
    }

    private Set<String> prior(String currentVisitId) {
        return MotherMentorDao.getPriorGroupLinkages(CLIENT_ID, currentVisitId);
    }

    private Set<String> groups() {
        return new HashSet<>(Arrays.asList(PSYCHOSOCIAL, IGA));
    }

    private List<String> languages() {
        return Arrays.asList("json.form", "json.form-sw");
    }

    private String service() {
        return Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES;
    }

    private JSONObject prepare(JSONObject form, Map<String, List<VisitDetail>> details)
            throws Exception {
        MotherMentorGroupLinkageActionHelper helper =
                new MotherMentorGroupLinkageActionHelper(member(CLIENT_ID));
        MotherMentorJsonFormUtils.populateForm(form, details);
        helper.onJsonFormLoaded(form.toString(), RuntimeEnvironment.application, details);
        return new JSONObject(helper.getPreProcessed());
    }

    private JSONObject loadForm(String directory) throws Exception {
        try (Scanner scanner =
                new Scanner(
                        RuntimeEnvironment.application
                                .getAssets()
                                .open(directory + "/" + FORM_NAME + ".json"),
                        "UTF-8")) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }

    private MemberObject member(String clientId) {
        MemberObject member = new MemberObject();
        member.setBaseEntityId(clientId);
        return member;
    }

    private Map<String, List<VisitDetail>> details(
            String visitId, String psychosocial, String iga) {
        Map<String, List<VisitDetail>> details = new HashMap<>();
        details.put(
                PSYCHOSOCIAL,
                Collections.singletonList(visitDetail(visitId, PSYCHOSOCIAL, psychosocial)));
        details.put(IGA, Collections.singletonList(visitDetail(visitId, IGA, iga)));
        return details;
    }

    private VisitDetail visitDetail(String visitId, String key, String value) {
        VisitDetail detail = new VisitDetail();
        detail.setVisitId(visitId);
        detail.setVisitKey(key);
        detail.setDetails(value);
        return detail;
    }

    private void visit(
            String id,
            String clientId,
            String visitType,
            int processed,
            long date,
            String psychosocial,
            String iga) {
        data.execSQL(
                "INSERT INTO visits VALUES (?, ?, ?, ?, ?)",
                new Object[] {id, clientId, visitType, processed, Long.toString(date)});
        if (psychosocial != null) detail(id, PSYCHOSOCIAL, psychosocial);
        if (iga != null) detail(id, IGA, iga);
    }

    private void detail(String visitId, String key, String answer) {
        data.execSQL(
                "INSERT INTO visit_details VALUES (?, ?, ?)",
                new Object[] {visitId, key, answer});
    }

    private void clearVisits() {
        data.delete("visits", null, null);
        data.delete("visit_details", null, null);
    }

    private void assertValues(JSONObject form, String psychosocial, String iga) {
        assertEquals(psychosocial, value(form, PSYCHOSOCIAL));
        assertEquals(iga, value(form, IGA));
    }

    private String value(JSONObject form, String key) {
        return JsonFormUtils.getValue(form, key);
    }

    private void set(JSONObject form, String key, String value) throws Exception {
        FormUtils.getFieldFromForm(form, key).put("value", value);
    }
}
