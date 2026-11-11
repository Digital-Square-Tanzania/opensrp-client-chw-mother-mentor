package org.smartregister.chw.mothermentor.repository;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.EVENT_BASE_ENTITY_ID;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.EVENT_DATE_TIMESTAMP;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.EVENT_JSON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.EVENT_TYPE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.EVENT_VOIDED;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.HOUSEHOLD_ENROLLMENT_ID;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;
import org.smartregister.chw.mothermentor.util.NCUtils;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.domain.SyncStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/** An undo event suppresses the specific set of exits that caused an automatic closure. */
public final class MotherMentorHouseholdClosureUndo {
    private static final String CLOSURE_KEY = "kaya_auto_closure_key";
    private static final String UNDO_EVENT = Constants.EVENT_TYPE.MAMA_KINARA_HOUSEHOLD_CLOSURE_UNDO;

    private MotherMentorHouseholdClosureUndo() {}

    public static boolean canUndo(SQLiteDatabase db, String householdId) {
        return !undoKey(db, householdId, LocalDate.now()).isEmpty();
    }

    static String undoKey(SQLiteDatabase db, String householdId, LocalDate day) {
        MotherMentorHouseholdClosure closure = MotherMentorHouseholdClosureRepository.getState(db, householdId, null);
        if (!MotherMentorHouseholdClosure.isMemberClosure(closure.getStatus())
                || !day.toString().equals(closure.getClosedDate())) return "";
        return MotherMentorMotherCascadeRepository.currentHouseholdClosureKey(db, householdId);
    }

    public static boolean undo(SQLiteDatabase db, String householdId) throws Exception {
        Date now = new Date();
        LocalDate day = new LocalDate(now);
        String key = undoKey(db, householdId, day);
        if (key.isEmpty()) return false;
        String episode = MotherMentorMotherCascadeRepository.householdEpisode(db, householdId);
        if (episode.isEmpty()) return false;
        Event event = (Event) new Event().withBaseEntityId(householdId).withEventDate(now)
                .withEventType(UNDO_EVENT).withEntityType(Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT)
                .withFormSubmissionId(UUID.randomUUID().toString());
        event.setSyncStatus(SyncStatus.PENDING.value());
        addObservation(event, HOUSEHOLD_ENROLLMENT_ID, episode);
        addObservation(event, CLOSURE_KEY, key);
        NCUtils.addEvent(MotherMentorLibrary.getInstance().context().allSharedPreferences(), event);
        if (!wasUndone(db, householdId, episode, key, day.toString())) return false;
        MotherMentorHouseholdClosureRepository.rebuildMemberExit(db, householdId);
        return !MotherMentorHouseholdClosureRepository.getState(db, householdId, null).isClosed();
    }

    private static void addObservation(Event event, String key, String value) {
        event.addObs(new Obs("concept", "text", key, "", new ArrayList<>(Collections.singletonList(value)),
                new ArrayList<>(), null, key));
    }

    static boolean wasUndone(SQLiteDatabase db, String householdId, String episode, String key, String date) {
        if (!MotherMentorMotherCascadeRepository.columns(db, "event").containsAll(
                Arrays.asList(EVENT_BASE_ENTITY_ID, EVENT_TYPE, EVENT_JSON))) return false;
        try (Cursor rows = db.rawQuery("SELECT " + EVENT_JSON + " FROM event WHERE " + EVENT_BASE_ENTITY_ID
                + " = ? AND " + EVENT_TYPE + " = ?", new String[] {householdId, UNDO_EVENT})) {
            while (rows.moveToNext()) {
                String json = rows.getString(0);
                if (json == null) continue;
                try {
                    JSONObject event = new JSONObject(json);
                    long timestamp = MotherMentorIitRepository.timestamp(event.opt(EVENT_DATE_TIMESTAMP));
                    if (event.optBoolean(EVENT_VOIDED) || timestamp <= 0
                            || !date.equals(new LocalDate(timestamp).toString())) continue;
                    Map<String, String> values = MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
                    if (episode.equals(values.get(HOUSEHOLD_ENROLLMENT_ID)) && key.equals(values.get(CLOSURE_KEY))) return true;
                } catch (JSONException invalid) {
                    // An incomplete synced event cannot undo a closure.
                }
            }
        }
        return false;
    }
}
