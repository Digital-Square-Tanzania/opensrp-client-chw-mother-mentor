package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

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
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorVisitTypeActionHelper;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.repository.Repository;

import java.util.Collections;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorServiceHistoryTest {
    private static final String CLIENT_ID = "mother-with-'quote";
    private MotherMentorLibrary previous;
    private SQLiteDatabase data;
    private net.zetetic.database.sqlcipher.SQLiteDatabase cipher;

    @Before
    public void setUp() {
        previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        data = SQLiteDatabase.create(null);
        data.execSQL(
                "CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT,"
                        + " processed INTEGER)");
        data.execSQL(
                "CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, art_status TEXT,"
                        + " art_start_date TEXT)");
        data.execSQL("CREATE TABLE visit_details (visit_id TEXT, visit_key TEXT, details TEXT)");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        cipher = mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
    }

    @After
    public void tearDown() {
        data.close();
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
    }

    @Test
    public void firstAttendanceDefaultsToNewInBothLanguages() throws Exception {
        assertFalse(MotherMentorDao.hasMotherMentorServices(CLIENT_ID));
        assertDefaultAttendance("new");
    }

    @Test
    public void locallySavedServiceDefaultsToRepeatBeforeProcessing() throws Exception {
        insertVisit(CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 0);
        assertTrue(MotherMentorDao.hasMotherMentorServices(CLIENT_ID));
        assertDefaultAttendance("repeat");
    }

    @Test
    public void processedServiceDefaultsToRepeatInBothLanguages() throws Exception {
        insertVisit(CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1);
        assertTrue(MotherMentorDao.hasMotherMentorServices(CLIENT_ID));
        assertDefaultAttendance("repeat");
    }

    @Test
    public void enrollmentAndOtherVisitsDoNotCountAsMotherMentorServices() throws Exception {
        insertVisit("another-client", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1);
        insertVisit(CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLLMENT, 1);
        insertVisit(CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_RECORD_VISIT, 1);
        insertVisit(CLIENT_ID, "ANC Home Visit", 1);
        for (MotherMentorFollowUpType type : MotherMentorFollowUpType.values()) {
            insertVisit(CLIENT_ID, type.getEventType(), 1);
        }
        assertFalse(MotherMentorDao.hasMotherMentorServices(CLIENT_ID));
        assertDefaultAttendance("new");
    }

    @Test
    public void serviceLookupBindsTheExactClientId() {
        insertVisit(CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1);
        assertTrue(MotherMentorDao.hasMotherMentorServices(CLIENT_ID));
        verify(cipher)
                .rawQuery(
                        anyString(),
                        eq(new String[] {CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES}));
        assertFalse(MotherMentorDao.hasMotherMentorServices("' OR 1 = 1 --"));
    }

    @Test
    public void editingPreservesSavedAttendanceWithoutQueryingHistory() throws Exception {
        insertVisit(CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1);
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            for (String saved : new String[] {"new", "repeat", "Mpya", "Marudio"}) {
                JSONObject form = loadForm(language);
                FormUtils.getFieldFromForm(form, DBConstants.KEY.ATTENDANCE_TYPE)
                        .put("value", saved);
                FormUtils.getFieldFromForm(form, DBConstants.KEY.ART_INITIATED_BEFORE_VISIT)
                        .put("value", "false");
                assertEquals(saved, prepareAttendance(member(CLIENT_ID), form));
            }
        }
        verifyNoInteractions(cipher);
    }

    @Test
    public void savedRepeatAttendanceRemainsReadOnlyInBothLanguages() throws Exception {
        assertSavedAttendanceReadOnly("repeat", true);
        verifyNoInteractions(cipher);
    }

    @Test
    public void savedNewAttendanceRemainsEditableInBothLanguages() throws Exception {
        insertVisit(CLIENT_ID, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, 1);
        assertSavedAttendanceReadOnly("new", false);
        verifyNoInteractions(cipher);
    }

    @Test
    public void missingClientIdentityDoesNotQueryOrGuessAttendance() throws Exception {
        assertFalse(MotherMentorDao.hasMotherMentorServices(null));
        assertFalse(MotherMentorDao.hasMotherMentorServices(" "));
        JSONObject form = loadForm("json.form");
        assertEquals("", prepareAttendance(null, form));
        assertEquals("", prepareAttendance(member(null), form));
        assertEquals("", prepareAttendance(member(" "), form));
        verifyNoInteractions(cipher);
    }

    @Test
    public void databaseFailureLeavesAttendanceUnansweredInsteadOfDefaultingToNew()
            throws Exception {
        data.execSQL("DROP TABLE visits");
        assertEquals("", prepareAttendance(member(CLIENT_ID), loadForm("json.form")));
    }

    private void assertDefaultAttendance(String expected) throws Exception {
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            JSONObject prepared = prepareForm(member(CLIENT_ID), loadForm(language));
            JSONObject attendance =
                    FormUtils.getFieldFromForm(prepared, DBConstants.KEY.ATTENDANCE_TYPE);
            assertEquals(expected, attendance.getString(JsonFormConstants.VALUE));
            assertEquals(
                    "repeat".equals(expected), attendance.optBoolean(JsonFormConstants.READ_ONLY));
            assertFalse(
                    FormUtils.getFieldFromForm(prepared, DBConstants.KEY.VISIT_PREGNANCY_STATUS)
                            .optBoolean(JsonFormConstants.READ_ONLY));
            assertFalse(
                    FormUtils.getFieldFromForm(prepared, DBConstants.KEY.FOLLOW_UP_STATUS)
                            .optBoolean(JsonFormConstants.READ_ONLY));
        }
    }

    private void assertSavedAttendanceReadOnly(String saved, boolean readOnly) throws Exception {
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = loadForm(language);
            FormUtils.getFieldFromForm(form, DBConstants.KEY.ATTENDANCE_TYPE)
                    .put(JsonFormConstants.VALUE, saved);
            FormUtils.getFieldFromForm(form, DBConstants.KEY.ART_INITIATED_BEFORE_VISIT)
                    .put(JsonFormConstants.VALUE, "false");
            JSONObject attendance =
                    FormUtils.getFieldFromForm(
                            prepareForm(member(CLIENT_ID), form), DBConstants.KEY.ATTENDANCE_TYPE);
            assertEquals(saved, attendance.getString(JsonFormConstants.VALUE));
            assertEquals(readOnly, attendance.optBoolean(JsonFormConstants.READ_ONLY));
        }
    }

    private String prepareAttendance(MemberObject member, JSONObject form) throws Exception {
        return JsonFormUtils.getValue(prepareForm(member, form), DBConstants.KEY.ATTENDANCE_TYPE);
    }

    private JSONObject prepareForm(MemberObject member, JSONObject form) throws Exception {
        MotherMentorVisitTypeActionHelper helper =
                new MotherMentorVisitTypeActionHelper(member) {
                    @Override
                    protected Map<String, String> loadEnrollmentValues() {
                        return Collections.emptyMap();
                    }
                };
        helper.onJsonFormLoaded(
                form.toString(), RuntimeEnvironment.application, Collections.emptyMap());
        return new JSONObject(helper.getPreProcessed());
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

    private MemberObject member(String clientId) {
        MemberObject member = new MemberObject();
        member.setBaseEntityId(clientId);
        return member;
    }

    private void insertVisit(String clientId, String visitType, int processed) {
        ContentValues values = new ContentValues();
        values.put(DBConstants.KEY.BASE_ENTITY_ID, clientId);
        values.put(DBConstants.KEY.VISIT_TYPE, visitType);
        values.put(DBConstants.KEY.PROCESSED, processed);
        data.insertOrThrow("visits", null, values);
    }
}
