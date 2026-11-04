package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;
import java.util.HashMap;
import java.util.Map;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.domain.db.EventClient;
import org.smartregister.repository.Repository;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherClosureLifecycleTest {
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase db;
    private MotherMentorLibrary previous;

    @Before
    public void setUp() throws Exception {
        data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, pregnancy_status TEXT,"
                + " child_dob TEXT, event_date TEXT, enrollment_date TEXT, last_interacted_with INTEGER, is_closed INTEGER)");
        data.execSQL("CREATE TABLE ec_family_member (base_entity_id TEXT, relational_id TEXT, date_died TEXT)");
        data.execSQL("CREATE TABLE event (baseEntityId TEXT, eventType TEXT, eventDate TEXT, formSubmissionId TEXT, json TEXT)");
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                + " form_submission_id TEXT, processed INTEGER, visit_date INTEGER, updated_at INTEGER,"
                + " created_at INTEGER, visit_json TEXT, pre_processed TEXT, visit_group TEXT, parent_visit_id TEXT)");
        data.execSQL("CREATE TABLE visit_details (visit_id TEXT)");
        data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','pregnant','',"
                + "'2026-10-01T08:00:00.000+03:00','2026-10-01',1,0)");
        data.execSQL("INSERT INTO ec_family_member VALUES ('mother','household','')");
        db = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(db.rawQuery(anyString(), nullable(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> { data.execSQL(call.getArgument(0)); return null; }).when(db).execSQL(anyString());
        doAnswer(call -> { data.execSQL(call.getArgument(0), call.getArgument(1)); return null; })
                .when(db).execSQL(anyString(), any(Object[].class));
        when(db.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(call -> data.update(call.getArgument(0), call.getArgument(1),
                        call.getArgument(2), call.getArgument(3)));
        when(db.insert(anyString(), nullable(String.class), any(ContentValues.class)))
                .thenAnswer(call -> data.insert(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(db.delete(anyString(), anyString(), any(String[].class)))
                .thenAnswer(call -> data.delete(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(db.inTransaction()).thenAnswer(call -> data.inTransaction());
        doAnswer(call -> { data.beginTransaction(); return null; }).when(db).beginTransaction();
        doAnswer(call -> { data.setTransactionSuccessful(); return null; }).when(db).setTransactionSuccessful();
        doAnswer(call -> { data.endTransaction(); return null; }).when(db).endTransaction();
        enrollment("one", "2026-10-01T08:00:00.000+03:00");
        previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getWritableDatabase()).thenReturn(db);
        when(library.visitRepository()).thenReturn(new FixtureVisits());
        when(library.visitDetailsRepository()).thenReturn(mock(VisitDetailsRepository.class));
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
    }

    @After public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        data.close();
    }


    @Test
    public void completingAndDeletingTheThirdVisitRebuildsClosureFromFinalizedHistory() throws Exception {
        visit("one", "one", "one", "2026-10-02", 1, true, other());
        visit("two", "two", "one", "2026-10-03", 1, true, other());
        visit("draft", "three", "one", "2026-10-04", 1, false, other());
        MotherMentorMotherClosureRepository.upgrade(db);
        assertEquals("active", column(MAMA_STATUS));
        FixtureVisits visits = new FixtureVisits();
        visits.completeProcessing("draft");
        assertEquals("not_found_other_3", column(MAMA_STATUS));
        assertEquals("1", column(IS_CLOSED));
        visits.deleteVisit("draft");
        assertEquals("active", column(MAMA_STATUS));
        assertEquals("0", column(IS_CLOSED));
        assertEquals("2", column(MAMA_COUNTER_NYINGINE));
        assertEquals(2, count("visits"));
    }

    @Test
    public void savingFinalizedEvidenceUpdatesClosureWithoutAnExtraProcessingPass() throws Exception {
        visit("one", "one", "one", "2026-10-02", 1, true, other());
        visit("two", "two", "one", "2026-10-03", 1, true, other());
        MotherMentorMotherClosureRepository.upgrade(db);
        visit("third", "three", "one", "2026-10-04", 1, true, other());
        FixtureVisits visits = new FixtureVisits();
        Visit finalized = visits.getVisitByVisitId("third");
        data.delete("visits", "visit_id = ?", new String[] {"third"});
        visits.addVisit(finalized, db);
        assertEquals("not_found_other_3", column(MAMA_STATUS));
        assertEquals(3, count("visits"));
    }

    @Test
    public void synchronizedCorrectionRebuildsClosureUsingTheUpdatedCanonicalVisit() throws Exception {
        visit("one", "one", "one", "2026-10-02", 1, true, other());
        visit("two", "two", "one", "2026-10-03", 1, true, other());
        visit("third", "three", "one", "2026-10-04", 1, true, other());
        MotherMentorMotherClosureRepository.upgrade(db);
        assertEquals("not_found_other_3", column(MAMA_STATUS));
        Map<String, String> answers = found();
        answers.put(MAMA_ENROLLMENT_ID, "one");
        answers.put(MOTHERMENTOR_VISIT_DATE, "2026-10-04");
        JSONObject json = new JSONObject().put("baseEntityId", "mother")
                .put("eventType", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES)
                .put("formSubmissionId", "three").put("version", 2)
                .put("eventDate", "2026-10-04T14:00:00.000+03:00").put("obs", obs(answers));
        org.smartregister.domain.Event event = org.smartregister.util.JsonFormUtils.gson
                .fromJson(json.toString(), org.smartregister.domain.Event.class);
        NCUtils.processHomeVisit(new EventClient(event), db);
        assertEquals("active", column(MAMA_STATUS));
        assertEquals("0", column(IS_CLOSED));
        assertEquals(3, count("visits"));
        assertEquals("third", new FixtureVisits().getVisitByFormSubmissionID("three").getVisitId());
    }

    @Test
    public void householdEnrollmentReplayRebuildsOnlyMothersInTheCurrentFamily() throws Exception {
        visit("one", "one", "one", "2026-10-02", 1, true, other());
        visit("two", "two", "one", "2026-10-03", 1, true, other());
        visit("three", "three", "one", "2026-10-04", 1, true, other());
        MotherMentorMotherClosureRepository.upgrade(db);
        data.execSQL("UPDATE ec_mothermentor_enrollment SET is_closed=0,mama_status='active'");
        MotherMentorMotherClosureRepository.rebuildForHousehold(db, "different-household");
        assertEquals("0", column(IS_CLOSED));
        MotherMentorMotherClosureRepository.rebuildForHousehold(db, "household");
        assertEquals("1", column(IS_CLOSED));
        assertEquals("not_found_other_3", column(MAMA_STATUS));
    }

    private final class FixtureVisits extends VisitRepository {
        @Override public net.zetetic.database.sqlcipher.SQLiteDatabase getReadableDatabase() { return db; }
        @Override public net.zetetic.database.sqlcipher.SQLiteDatabase getWritableDatabase() { return db; }
        @Override public Visit getVisitByVisitId(String id) {
            return one("visit_id", id);
        }
        @Override public Visit getVisitByFormSubmissionID(String id) {
            return one("form_submission_id", id);
        }
        private Visit one(String column, String id) {
            java.util.List<Visit> visits = readVisits(data.rawQuery(
                    "SELECT * FROM visits WHERE " + column + " = ?", new String[] {id}));
            return visits.isEmpty() ? null : visits.get(0);
        }
    }

    private static Map<String,String> other() {
        Map<String,String> v=new HashMap<>();v.put(MAMA_VISIT_SCHEMA,"2");v.put(ATTENDANCE_TYPE,"repeat");
        v.put(FOLLOW_UP_STATUS,"not_found");v.put(MAMA_NOT_FOUND_REASON,"other");v.put(MAMA_SABABU_NYINGINE_TEXT,"away");return v;
    }
    private static Map<String,String> found() {
        Map<String,String> v=other();v.put(FOLLOW_UP_STATUS,"found");v.put(VISIT_PREGNANCY_STATUS,"pregnant");return v;
    }
    private void enrollment(String source,String date) throws Exception {
        JSONObject event=new JSONObject().put("formSubmissionId",source).put("eventDate",date)
                .put("obs",obs(java.util.Collections.singletonMap(PREGNANCY_STATUS,"pregnant")));
        data.execSQL("INSERT INTO event VALUES (?,?,?,?,?)",new Object[]{"mother",Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT,date,source,event.toString()});
    }
    private void visit(String id,String source,String episode,String date,int version,boolean processed,Map<String,String> values) throws Exception {
        Map<String,String> all=new HashMap<>(values);all.put(MAMA_ENROLLMENT_ID,episode);all.put(MOTHERMENTOR_VISIT_DATE,date);
        JSONObject event=new JSONObject().put("formSubmissionId",source).put("eventDate",date+"T14:00:00.000+03:00")
                .put("version",version).put("obs",obs(all));
        data.execSQL("INSERT INTO visits (visit_id,base_entity_id,visit_type,form_submission_id,processed,visit_date,updated_at,created_at,visit_json,pre_processed) VALUES (?,?,?,?,?,?,?,?,?,?)",new Object[]{id,"mother",Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,source,processed?1:0,new LocalDate(date).toDate().getTime(),version,version,event.toString(),""});
    }
    private static JSONArray obs(Map<String,String> values) throws Exception {
        JSONArray obs=new JSONArray();for(Map.Entry<String,String> e:values.entrySet())obs.put(new JSONObject().put("fieldCode",e.getKey()).put("values",new JSONArray().put(e.getValue())));return obs;
    }
    private String column(String key) { try(Cursor c=data.rawQuery("SELECT "+key+" FROM ec_mothermentor_enrollment",null)){assertTrue(c.moveToFirst());return c.getString(0);} }
    private int count(String table) {try(Cursor c=data.rawQuery("SELECT COUNT(*) FROM "+table,null)){assertTrue(c.moveToFirst());return c.getInt(0);} }
}
