package org.smartregister.chw.mothermentor.actionhelper;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import com.vijay.jsonwizard.filesource.AssetsFileSource;
import com.vijay.jsonwizard.utils.FormUtils;

import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
import org.jeasy.rules.api.Rules;
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
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorJsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.repository.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorArtInitiationTest {
    private static final String CLIENT_ID = "mother-'quote";
    private MotherMentorLibrary previous;
    private SQLiteDatabase data;
    private Rules rules;

    @Before
    public void setUp() throws Exception {
        previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        data = SQLiteDatabase.create(null);
        data.execSQL(
                "CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, art_status TEXT,"
                        + " art_start_date TEXT)");
        data.execSQL(
                "CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                        + " processed INTEGER)");
        data.execSQL("CREATE TABLE visit_details (visit_id TEXT, visit_key TEXT, details TEXT)");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        rules =
                AssetsFileSource.INSTANCE.getRulesFromFile(
                        RuntimeEnvironment.application,
                        "rule/mothermentor_reporting_relevance.yml");
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
    }

    @Test
    public void enrollmentInitiationHidesQuestionAndDateInBothLanguages() throws Exception {
        enroll(CLIENT_ID, "yes", "2026-01-10");
        assertTrue(MotherMentorDao.wasArtInitiatedBeforeVisit(CLIENT_ID, null));
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            JSONObject prepared = prepare(loadForm(language), Collections.emptyMap());
            assertEquals("true", value(prepared, DBConstants.KEY.ART_INITIATED_BEFORE_VISIT));
            assertFalse(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
            // Even a stale yes answer must not reveal the initiation date.
            set(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION, "yes");
            assertFalse(relevant(prepared, DBConstants.KEY.VISIT_ART_START_DATE));
            assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values(prepared)));
        }
    }

    @Test
    public void recordedEnrollmentDateOrPositiveStatusIsEvidenceOfPriorInitiation() {
        enroll(CLIENT_ID, " YES ", null);
        enroll("date-only", null, "10-01-2026");
        enroll("swahili", "Ndiyo", "");
        assertTrue(MotherMentorDao.wasArtInitiatedBeforeVisit(CLIENT_ID, null));
        assertTrue(MotherMentorDao.wasArtInitiatedBeforeVisit("date-only", null));
        assertTrue(MotherMentorDao.wasArtInitiatedBeforeVisit("swahili", null));
    }

    @Test
    public void clientsNotYetInitiatedAreAskedAndMustAnswer() throws Exception {
        enroll(CLIENT_ID, "no", null);
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            JSONObject prepared = prepare(loadForm(language), Collections.emptyMap());
            assertEquals("false", value(prepared, DBConstants.KEY.ART_INITIATED_BEFORE_VISIT));
            assertTrue(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
            assertFalse(relevant(prepared, DBConstants.KEY.VISIT_ART_START_DATE));
            assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values(prepared)));
            set(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION, "no");
            assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values(prepared)));
            set(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION, "yes");
            assertTrue(relevant(prepared, DBConstants.KEY.VISIT_ART_START_DATE));
            assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values(prepared)));
            set(prepared, DBConstants.KEY.VISIT_ART_START_DATE, "20-10-2026");
            assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values(prepared)));
        }
    }

    @Test
    public void earlierServiceInitiationCountsBeforeAndAfterProcessing() throws Exception {
        enroll(CLIENT_ID, "no", null);
        for (int processed : new int[] {0, 1}) {
            data.delete("visits", null, null);
            data.delete("visit_details", null, null);
            visit(
                    "earlier",
                    CLIENT_ID,
                    Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,
                    processed,
                    "yes");
            assertTrue(MotherMentorDao.wasArtInitiatedBeforeVisit(CLIENT_ID, null));
            JSONObject prepared = prepare(loadForm("json.form"), Collections.emptyMap());
            assertFalse(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
        }
    }

    @Test
    public void onlyTheExactClientsExplicitServiceInitiationCounts() {
        enroll("other-client", "yes", "2026-01-10");
        enroll(CLIENT_ID, "no", " ");
        visit("other", "other-client", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1, "yes");
        visit("followup", CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_FOLLOW_UP_VISIT, 1, "yes");
        visit("negative", CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1, "no");
        assertFalse(MotherMentorDao.wasArtInitiatedBeforeVisit(CLIENT_ID, null));
        assertFalse(MotherMentorDao.wasArtInitiatedBeforeVisit("' OR 1=1 --", null));
        assertFalse(MotherMentorDao.wasArtInitiatedBeforeVisit(null, null));
        assertFalse(MotherMentorDao.wasArtInitiatedBeforeVisit(" ", null));
    }

    @Test
    public void editingLegacyInitiationExcludesTheCurrentVisit() throws Exception {
        enroll(CLIENT_ID, "no", null);
        visit("current", CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 0, "yes");
        Map<String, List<VisitDetail>> details =
                Collections.singletonMap(
                        DBConstants.KEY.NEW_PMTCT_ART_INITIATION,
                        Collections.singletonList(
                                detail(
                                        "current",
                                        DBConstants.KEY.NEW_PMTCT_ART_INITIATION,
                                        "yes")));
        JSONObject prepared = prepare(loadForm("json.form"), details);
        assertEquals("false", value(prepared, DBConstants.KEY.ART_INITIATED_BEFORE_VISIT));
        assertEquals("yes", value(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
        assertTrue(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
        assertTrue(relevant(prepared, DBConstants.KEY.VISIT_ART_START_DATE));
        assertTrue(MotherMentorDao.wasArtInitiatedBeforeVisit(CLIENT_ID, null));
        assertFalse(MotherMentorDao.wasArtInitiatedBeforeVisit(CLIENT_ID, "current"));
    }

    @Test
    public void excludingCurrentVisitStillFindsAnEarlierInitiation() {
        visit("earlier", CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1, "yes");
        visit("current", CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 0, "yes");
        assertTrue(MotherMentorDao.wasArtInitiatedBeforeVisit(CLIENT_ID, "current"));
    }

    @Test
    public void editingKeepsSavedPriorInitiationSnapshotEvenIfEnrollmentHasChanged()
            throws Exception {
        enroll(CLIENT_ID, "yes", "2026-10-20");
        Map<String, List<VisitDetail>> details =
                Collections.singletonMap(
                        DBConstants.KEY.ART_INITIATED_BEFORE_VISIT,
                        Collections.singletonList(
                                detail(
                                        "current",
                                        DBConstants.KEY.ART_INITIATED_BEFORE_VISIT,
                                        "false")));
        JSONObject prepared = prepare(loadForm("json.form"), details);
        assertEquals("false", value(prepared, DBConstants.KEY.ART_INITIATED_BEFORE_VISIT));
        assertTrue(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
    }

    @Test
    public void newQuestionKeepsExistingPrimaryAndFullVisitRestrictions() throws Exception {
        JSONObject prepared = prepare(loadForm("json.form"), Collections.emptyMap());
        set(prepared, DBConstants.KEY.ATTENDANCE_TYPE, "repeat");
        set(prepared, DBConstants.KEY.FOLLOW_UP_STATUS, "continuing_services");
        assertTrue(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
        set(prepared, DBConstants.KEY.FOLLOW_UP_STATUS, "not_available");
        assertFalse(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
        set(prepared, DBConstants.KEY.ATTENDANCE_TYPE, "new");
        set(prepared, DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT, "false");
        assertFalse(relevant(prepared, DBConstants.KEY.NEW_PMTCT_ART_INITIATION));
    }

    @Test
    public void legacyVisitWithoutSnapshotStillRequiresTheInitiationAnswer() {
        Map<String, String> values = new HashMap<>();
        values.put(DBConstants.KEY.MOTHERMENTOR_REPORTING_VERSION, "2");
        values.put(DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT, "true");
        values.put(DBConstants.KEY.ATTENDANCE_TYPE, "new");
        values.put(DBConstants.KEY.VISIT_PREGNANCY_STATUS, "pregnant");
        assertFalse(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        values.put(DBConstants.KEY.NEW_PMTCT_ART_INITIATION, "no");
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
    }

    private JSONObject prepare(JSONObject form, Map<String, List<VisitDetail>> details)
            throws Exception {
        MemberObject member = new MemberObject();
        member.setBaseEntityId(CLIENT_ID);
        MotherMentorVisitTypeActionHelper helper =
                new MotherMentorVisitTypeActionHelper(member) {
                    @Override
                    protected Map<String, String> loadEnrollmentValues() {
                        return Collections.singletonMap(
                                DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT, "true");
                    }
                };
        MotherMentorJsonFormUtils.populateForm(form, details);
        helper.onJsonFormLoaded(form.toString(), RuntimeEnvironment.application, details);
        JSONObject prepared = new JSONObject(helper.getPreProcessed());
        MotherMentorJsonFormUtils.populateForm(prepared, details);
        set(prepared, DBConstants.KEY.VISIT_PREGNANCY_STATUS, "pregnant");
        set(prepared, DBConstants.KEY.FOLLOW_UP_STATUS, "continuing_services");
        return prepared;
    }

    private boolean relevant(JSONObject form, String key) throws Exception {
        String file =
                FormUtils.getFieldFromForm(form, key)
                        .getJSONObject("relevance")
                        .getJSONObject("rules-engine")
                        .getJSONObject("ex-rules")
                        .getString("rules-file");
        assertEquals("mothermentor_reporting_relevance.yml", file);
        Facts facts = new Facts();
        for (Map.Entry<String, String> entry : values(form).entrySet()) {
            facts.put("step1_" + entry.getKey(), entry.getValue());
        }
        for (Rule rule : rules) {
            if (rule.getName().equals("step1_" + key)) return rule.evaluate(facts);
        }
        throw new AssertionError("Missing relevance rule for " + key);
    }

    private JSONObject loadForm(String directory) throws Exception {
        try (Scanner scanner =
                new Scanner(
                        RuntimeEnvironment.application
                                .getAssets()
                                .open(directory + "/mothermentor_visit_type.json"),
                        "UTF-8")) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }

    private Map<String, String> values(JSONObject form) throws Exception {
        Map<String, String> values = new HashMap<>();
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            values.put(field.getString("key"), field.optString("value"));
        }
        return values;
    }

    private void enroll(String clientId, String status, String date) {
        data.execSQL(
                "INSERT INTO ec_mothermentor_enrollment VALUES (?, ?, ?)",
                new Object[] {clientId, status, date});
    }

    private void visit(
            String visitId, String clientId, String type, int processed, String initiated) {
        data.execSQL(
                "INSERT INTO visits VALUES (?, ?, ?, ?)",
                new Object[] {visitId, clientId, type, processed});
        data.execSQL(
                "INSERT INTO visit_details VALUES (?, ?, ?)",
                new Object[] {visitId, DBConstants.KEY.NEW_PMTCT_ART_INITIATION, initiated});
    }

    private VisitDetail detail(String visitId, String key, String value) {
        VisitDetail detail = new VisitDetail();
        detail.setVisitId(visitId);
        detail.setVisitKey(key);
        detail.setDetails(value);
        return detail;
    }

    private String value(JSONObject form, String key) {
        return JsonFormUtils.getValue(form, key);
    }

    private void set(JSONObject form, String key, String value) throws Exception {
        FormUtils.getFieldFromForm(form, key).put("value", value);
    }
}
