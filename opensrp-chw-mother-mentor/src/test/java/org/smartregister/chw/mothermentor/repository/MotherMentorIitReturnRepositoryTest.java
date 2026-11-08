package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;

import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorIitReturnRepositoryTest {
    private static final String ENROLLMENT = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
    private static final String VISITS = VisitRepository.VISIT_TABLE;
    private static final String CLIENT = "synthetic-iit-client";

    @Test
    public void onlyFinalizedVisitsAdvanceReturnStateAndDeletingReturnRestoresPendingAgreement()
            throws Exception {
        try (SQLiteDatabase database = SQLiteDatabase.create(null)) {
            net.zetetic.database.sqlcipher.SQLiteDatabase cipher = adapter(database);
            database.execSQL("CREATE TABLE " + ENROLLMENT + " (" + BASE_ENTITY_ID
                    + " TEXT PRIMARY KEY, " + IIT_EXPECTED_RETURN_DATE + " TEXT, " + IS_CLOSED
                    + " INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("INSERT INTO " + ENROLLMENT + " (" + BASE_ENTITY_ID
                    + ", " + IIT_EXPECTED_RETURN_DATE + ") VALUES (?, ?)",
                    new String[] {CLIENT, "2024-10-20"});
            VisitRepository.createTable(cipher);
            MotherMentorIitRepository.upgrade(cipher);

            Map<String, String> agreement = answers("2024-11-01");
            agreement.put(SERVICE_INTERRUPTION_REASON, "transport_cost");
            agreement.put(IIT_RETURN_AGREEMENT, "agreed");
            assertEquals("referred", MotherMentorIitRepository.preview(cipher, CLIENT, null,
                    agreement, new LocalDate(2024, 11, 1)).getReturnState());
            assertEquals("none", state(cipher).getReturnState());
            insertVisit(database, "agree", agreement, false);
            MotherMentorIitRepository.rebuild(cipher, CLIENT);
            assertEquals("none", state(cipher).getReturnState());
            assertEquals("none", projected(database, IIT_RETURN_STATE));

            finalizeVisit(database, "agree");
            MotherMentorIitRepository.rebuild(cipher, CLIENT);
            assertEquals("referred", projected(database, IIT_RETURN_STATE));
            assertFalse(state(cipher).isClosed());

            Map<String, String> returned = answers("2024-11-05");
            returned.put(RETURNED_TO_FACILITY, "yes");
            returned.put(FACILITY_RETURN_DATE, "2024-10-19");
            assertFalse(MotherMentorIitRepository.preview(cipher, CLIENT, null, returned,
                    new LocalDate(2024, 11, 5)).isClosed());
            returned.put(FACILITY_RETURN_DATE, "2024-10-20");
            insertVisit(database, "return", returned, false);
            MotherMentorIitRepository.rebuild(cipher, CLIENT);
            assertFalse(state(cipher).isClosed());
            assertEquals("0", projected(database, IS_CLOSED));

            finalizeVisit(database, "return");
            MotherMentorIitRepository.rebuild(cipher, CLIENT);
            assertTrue(state(cipher).isClosed());
            assertEquals("I1", projected(database, IIT_STATUS));
            assertEquals("2024-11-05", projected(database, IIT_TAREHE_YA_KUFUNGWA));
            assertFalse(MotherMentorIitRepository.getState(cipher, CLIENT, "return").isClosed());

            database.delete(VISITS, VISIT_ID + " = ?", new String[] {"return"});
            MotherMentorIitRepository.rebuild(cipher, CLIENT);
            assertFalse(state(cipher).isClosed());
            assertEquals("referred", projected(database, IIT_RETURN_STATE));
            assertEquals("0", projected(database, IS_CLOSED));
        }
    }

    private static MotherMentorIitState state(net.zetetic.database.sqlcipher.SQLiteDatabase cipher) {
        return MotherMentorIitRepository.getState(cipher, CLIENT, null);
    }

    private static net.zetetic.database.sqlcipher.SQLiteDatabase adapter(SQLiteDatabase database) {
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(cipher.rawQuery(anyString(), any(String[].class))).thenAnswer(
                invocation -> database.rawQuery(invocation.getArgument(0), invocation.getArgument(1)));
        doAnswer(invocation -> {
            database.execSQL(invocation.getArgument(0));
            return null;
        }).when(cipher).execSQL(anyString());
        when(cipher.update(anyString(), any(ContentValues.class), anyString(), any(String[].class)))
                .thenAnswer(invocation -> database.update(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3)));
        return cipher;
    }

    private static Map<String, String> answers(String serviceDate) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(IIT_FOLLOW_UP_SCHEMA, "2");
        values.put(MOTHERMENTOR_VISIT_DATE, serviceDate);
        values.put(FOLLOW_UP_STATUS, "found");
        return values;
    }

    private static void insertVisit(SQLiteDatabase database, String id,
            Map<String, String> answers, boolean finalized) throws Exception {
        JSONArray observations = new JSONArray();
        for (Map.Entry<String, String> answer : answers.entrySet()) {
            observations.put(new JSONObject().put("fieldCode", answer.getKey())
                    .put("values", new JSONArray().put(answer.getValue())));
        }
        ContentValues row = new ContentValues();
        row.put(VISIT_ID, id);
        row.put(FORM_SUBMISSION_ID, id);
        row.put(BASE_ENTITY_ID, CLIENT);
        row.put(VISIT_TYPE, MotherMentorFollowUpType.MOTHER_IIT.getEventType());
        row.put(PROCESSED, finalized ? 1 : 0);
        row.put(VISIT_JSON, new JSONObject().put("obs", observations).toString());
        database.insertOrThrow(VISITS, null, row);
    }

    private static void finalizeVisit(SQLiteDatabase database, String id) {
        ContentValues values = new ContentValues();
        values.put(PROCESSED, 1);
        assertEquals(1, database.update(VISITS, values, VISIT_ID + " = ?", new String[] {id}));
    }

    private static String projected(SQLiteDatabase database, String field) {
        try (Cursor row = database.rawQuery("SELECT " + field + " FROM " + ENROLLMENT
                + " WHERE " + BASE_ENTITY_ID + " = ?", new String[] {CLIENT})) {
            assertTrue(row.moveToFirst());
            return row.getString(0);
        }
    }
}
