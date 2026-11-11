package org.smartregister.chw.mothermentor.repository;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.content.ContentValues;
import android.database.MatrixCursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import java.util.Date;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidReplayTest {
    @Test
    public void newerDatedChildCorrectionReplacesCanonicalEvidenceAndRebuildsMotherState() throws Exception {
        SQLiteDatabase db = databaseWithoutEnrollmentTables();
        VisitDetailsRepository details = mock(VisitDetailsRepository.class);
        Visit previous = visit("local", 10), incoming = visit("generated", 11);
        Date original = previous.getCreatedAt();
        new VisitRepository().refreshChildEidVisit(previous, incoming, db, details);
        ArgumentCaptor<ContentValues> written = ArgumentCaptor.forClass(ContentValues.class);
        verify(db).update(eq("visits"), written.capture(), eq("visit_id = ?"), eq(new String[] {"local"}));
        assertEquals("local", written.getValue().getAsString(DBConstants.KEY.VISIT_ID));
        assertEquals(incoming.getJson(), written.getValue().getAsString(DBConstants.KEY.VISIT_JSON));
        assertEquals(original, incoming.getCreatedAt());
        verify(db, atLeastOnce()).rawQuery(anyString(), eq(new String[] {Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID}));
    }

    @Test
    public void identicalSynchronizedEvidenceFinalizesTheExistingLocalDraft() throws Exception {
        SQLiteDatabase db = databaseWithoutEnrollmentTables();
        Visit previous = visit("local", 10), incoming = visit("generated", 10);
        previous.setProcessed(false);
        new VisitRepository().refreshChildEidVisit(previous, incoming, db, mock(VisitDetailsRepository.class));
        ArgumentCaptor<ContentValues> values = ArgumentCaptor.forClass(ContentValues.class);
        verify(db).update(eq("visits"), values.capture(), eq("visit_id = ?"), eq(new String[] {"local"}));
        assertEquals(Integer.valueOf(1), values.getValue().getAsInteger(DBConstants.KEY.PROCESSED));
        assertEquals("", values.getValue().getAsString(DBConstants.KEY.PRE_PROCESSED));
        assertTrue(previous.getProcessed());
        assertFalse(values.getValue().containsKey(DBConstants.KEY.VISIT_JSON));
    }

    @Test
    public void olderUnversionedOrUnrelatedEventsCannotReplaceChildEvidence() throws Exception {
        for (long version : new long[] {0, 9}) {
            SQLiteDatabase db = databaseWithoutEnrollmentTables();
            Visit previous = visit("local", 10), incoming = visit("generated", version);
            new VisitRepository().refreshChildEidVisit(previous, incoming, db, mock(VisitDetailsRepository.class));
            verify(db, never()).update(anyString(), any(), anyString(), any());
        }
        Visit unrelated = visit("other", 10);
        unrelated.setVisitType(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        new VisitRepository().refreshChildEidVisit(unrelated, visit("generated", 11), db, mock(VisitDetailsRepository.class));
        verifyNoInteractions(db);
    }

    private SQLiteDatabase databaseWithoutEnrollmentTables() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        when(db.rawQuery(startsWith("PRAGMA table_info("), any(String[].class)))
                .thenAnswer(invocation -> new MatrixCursor(new String[] {"name"}));
        return db;
    }

    private Visit visit(String id, long version) throws Exception {
        Visit visit = new Visit();
        visit.setVisitId(id); visit.setBaseEntityId("child"); visit.setFormSubmissionId("same-source");
        visit.setVisitType(MotherMentorFollowUpType.CHILD_EID.getEventType());
        visit.setJson(new JSONObject().put("version", version).put("formSubmissionId", "same-source").toString());
        visit.setCreatedAt(new Date(1)); visit.setUpdatedAt(new Date(2)); visit.setDate(new Date(3));
        visit.setProcessed(true);
        return visit;
    }
}
