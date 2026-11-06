package org.smartregister.chw.mothermentor.repository;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.content.ContentValues;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorIitReturnUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads finalized visits for the current standalone IIT enrollment. */
public final class MotherMentorIitRepository {
    private static final String TABLE = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
    private static final String VISIT_EVENT = MotherMentorFollowUpType.MOTHER_IIT.getEventType();
    private static final List<String> COUNTERS = Arrays.asList(IIT_COUNTER_AMEKATAA,
            IIT_COUNTER_HAJARUDI, IIT_COUNTER_AMESAFIRI, IIT_COUNTER_NYINGINE, IIT_COUNTER_HAJAPATIKANA);
    public static final List<String> COLUMNS = Collections.unmodifiableList(Arrays.asList(
            IIT_STATUS, IIT_TAREHE_YA_KUFUNGWA, IIT_SABABU_YA_KUFUNGWA, IIT_RETURN_STATE,
            IIT_KITUO_KIPYA, IIT_MAMA_KINARA_LINK, IIT_COUNTER_AMEKATAA, IIT_COUNTER_HAJARUDI,
            IIT_COUNTER_AMESAFIRI, IIT_COUNTER_NYINGINE, IIT_COUNTER_HAJAPATIKANA));

    private MotherMentorIitRepository() {}

    public static MotherMentorIitState getState(SQLiteDatabase db, String clientId,
            String excludedVisitId) {
        Episode episode = episode(db, clientId);
        if (episode == null) return MotherMentorIitState.active();
        return replay(episode, history(db, clientId, episode, excludedVisitId));
    }

    /** A form can show its proposed exit without writing any visit or projection. */
    public static MotherMentorIitState preview(SQLiteDatabase db, String clientId,
            String excludedVisitId, Map<String, String> answers, LocalDate serviceDate) {
        Episode episode = episode(db, clientId);
        if (episode == null) return MotherMentorIitState.active();
        List<Evidence> history = history(db, clientId, episode, excludedVisitId);
        MotherMentorIitState state = replay(episode, history);
        if (!isStatusComplete(answers) || serviceDate == null || serviceDate.isAfter(LocalDate.now())) {
            return state;
        }
        String exit = immediateExit(answers);
        return exit.isEmpty() ? advance(state, answers, serviceDate)
                : state.close(exit, serviceDate, value(answers, IIT_KITUO_KIPYA));
    }

    public static boolean isStatusComplete(Map<String, String> answers) {
        if (answers == null) return false;
        if ("found".equals(value(answers, FOLLOW_UP_STATUS))) return true;
        if (!"not_found".equals(value(answers, FOLLOW_UP_STATUS))) return false;
        switch (value(answers, IIT_NOT_FOUND_REASON)) {
            case "deceased":
            case "moved_area":
            case "travelled":
                return true;
            case "moved_facility":
                return !value(answers, IIT_KITUO_KIPYA).isEmpty();
            case "other":
                return !value(answers, IIT_NOT_FOUND_OTHER).isEmpty();
            default:
                return false;
        }
    }

    public static boolean isComplete(Map<String, String> answers, MotherMentorIitState state) {
        if (!isStatusComplete(answers)) return false;
        String rawDate = value(answers, MOTHERMENTOR_VISIT_DATE);
        LocalDate serviceDate = rawDate.isEmpty() ? LocalDate.now()
                : MotherMentorHeiSchedule.parseDate(rawDate);
        return serviceDate != null && !serviceDate.isAfter(LocalDate.now())
                && isComplete(answers, state, serviceDate);
    }

    private static boolean isComplete(Map<String, String> answers, MotherMentorIitState state,
            LocalDate serviceDate) {
        return isStatusComplete(answers) && (!"found".equals(value(answers, FOLLOW_UP_STATUS))
                || MotherMentorIitReturnUtils.isComplete(answers, state.getReturnState(),
                        state.getMissedAppointmentDate(), serviceDate));
    }

    /** Called by the explicit app migration, using the same database as the rest of the upgrade. */
    public static void upgrade(SQLiteDatabase db) {
        List<String> columns = MotherMentorMotherCascadeRepository.columns(db, TABLE);
        if (columns.isEmpty()) return;
        for (String column : COLUMNS) {
            if (!columns.contains(column)) db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + column
                    + (COUNTERS.contains(column) ? " INTEGER NOT NULL DEFAULT 0" : " TEXT"));
        }
        List<String> clients = new ArrayList<>();
        try (Cursor rows = db.rawQuery("SELECT DISTINCT " + BASE_ENTITY_ID + " FROM " + TABLE, new String[0])) {
            while (rows.moveToNext()) clients.add(rows.getString(0));
        }
        for (String clientId : clients) rebuild(db, clientId);
    }

    public static void rebuild(SQLiteDatabase db, String clientId) {
        if (!hasProjection(db)) return;
        Episode episode = episode(db, clientId);
        if (episode == null) return;
        MotherMentorIitState state = replay(episode, history(db, clientId, episode, null));
        ContentValues projection = new ContentValues();
        for (Map.Entry<String, String> entry : observationValues(state).entrySet()) {
            projection.put(entry.getKey(), entry.getValue());
        }
        projection.put(IS_CLOSED, state.isClosed() ? 1 : 0);
        db.update(TABLE, projection, BASE_ENTITY_ID + " = ?", new String[] {clientId});
    }

    static boolean hasProjection(SQLiteDatabase db) {
        return db != null && MotherMentorMotherCascadeRepository.columns(db, TABLE).containsAll(COLUMNS);
    }

    public static Map<String, String> observationValues(MotherMentorIitState state) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(IIT_STATUS, state.getStatus());
        values.put(IIT_TAREHE_YA_KUFUNGWA, state.getClosedDate());
        values.put(IIT_SABABU_YA_KUFUNGWA, state.isClosed() ? state.getStatus() : "");
        values.put(IIT_RETURN_STATE, state.getReturnState());
        values.put(IIT_KITUO_KIPYA, state.getNewFacility());
        values.put(IIT_MAMA_KINARA_LINK, state.getLinkedMotherId());
        values.put(IIT_COUNTER_AMEKATAA, Integer.toString(state.getRefusedCount()));
        values.put(IIT_COUNTER_HAJARUDI, Integer.toString(state.getNotReturnedCount()));
        values.put(IIT_COUNTER_AMESAFIRI, Integer.toString(state.getTravelledCount()));
        values.put(IIT_COUNTER_NYINGINE, Integer.toString(state.getOtherCount()));
        values.put(IIT_COUNTER_HAJAPATIKANA, Integer.toString(state.getNotFoundCount()));
        return values;
    }

    public static boolean isIitVisit(Visit visit) {
        return visit != null && VISIT_EVENT.equals(visit.getVisitType());
    }

    static void rebuildForVisit(SQLiteDatabase db, Visit visit) {
        if (isIitVisit(visit) && Boolean.TRUE.equals(visit.getProcessed())) {
            rebuild(db, visit.getBaseEntityId());
        }
    }

    private static MotherMentorIitState replay(Episode episode, List<Evidence> visits) {
        MotherMentorIitState state = state(episode, visits.size());
        for (Evidence evidence : visits) {
            if ("2".equals(value(evidence.values, IIT_FOLLOW_UP_SCHEMA))) {
                state = advance(state, evidence.values, evidence.date);
            }
        }
        return state;
    }

    private static MotherMentorIitState advance(MotherMentorIitState state, Map<String, String> answers,
            LocalDate serviceDate) {
        if (state.isClosed() || !"found".equals(value(answers, FOLLOW_UP_STATUS))
                || !isComplete(answers, state, serviceDate)) return state;
        if (MotherMentorIitState.NONE.equals(state.getReturnState())) {
            return "agreed".equals(value(answers, IIT_RETURN_AGREEMENT)) ? state.agreeToReturn() : state;
        }
        if ("yes".equals(value(answers, RETURNED_TO_FACILITY))) {
            return state.close(MotherMentorIitState.RETURNED_TO_CARE, serviceDate, "");
        }
        return state;
    }

    private static String immediateExit(Map<String, String> answers) {
        if (!"not_found".equals(value(answers, FOLLOW_UP_STATUS))) return "";
        switch (value(answers, IIT_NOT_FOUND_REASON)) {
            case "deceased": return MotherMentorIitState.DECEASED;
            case "moved_area": return MotherMentorIitState.MOVED_AREA;
            case "moved_facility": return MotherMentorIitState.MOVED_FACILITY;
            default: return "";
        }
    }

    private static MotherMentorIitState state(Episode episode, int visitCount) {
        MotherMentorIitState state = MotherMentorIitState.active(episode.id, episode.missedDate,
                episode.linkedMother, visitCount);
        String projected = value(episode.enrollment, IIT_STATUS);
        return "1".equals(value(episode.enrollment, IS_CLOSED))
                && !MotherMentorIitState.RETURNED_TO_CARE.equals(projected)
                ? state.close(MotherMentorIitState.LEGACY_CLOSED, null, "") : state;
    }

    private static Episode episode(SQLiteDatabase db, String clientId) {
        if (db == null || StringUtils.isBlank(clientId)) return null;
        Map<String, String> enrollment = MotherMentorMotherCascadeRepository.row(db, TABLE, clientId);
        if (enrollment.isEmpty()) return null;
        MotherMentorMotherCascadeRepository.Enrollment raw = MotherMentorMotherCascadeRepository
                .latestEnrollment(db, clientId, Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT);
        LocalDate missed = MotherMentorHeiSchedule.parseBirthDate(value(enrollment, IIT_EXPECTED_RETURN_DATE));
        String missedDate = missed == null ? "" : missed.toString();
        String episodeId = raw == null ? "legacy:" + clientId + ":" + missedDate : raw.id;
        String linked = MotherMentorMotherCascadeRepository.row(db,
                Constants.TABLES.MOTHERMENTOR_ENROLLMENT, clientId).isEmpty() ? "" : clientId;
        return new Episode(episodeId, raw == null ? 0 : raw.time, missedDate, linked, enrollment);
    }

    private static List<Evidence> history(SQLiteDatabase db, String clientId, Episode episode,
            String excludedVisitId) {
        if (MotherMentorMotherCascadeRepository.columns(db, VisitRepository.VISIT_TABLE).isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Evidence> bySource = new HashMap<>();
        String excludedSource = StringUtils.defaultString(excludedVisitId);
        try (Cursor rows = db.rawQuery("SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                + BASE_ENTITY_ID + " = ? AND " + VISIT_TYPE + " = ? AND " + PROCESSED + " = 1",
                new String[] {clientId, VISIT_EVENT})) {
            while (rows.moveToNext()) {
                String id = MotherMentorMotherCascadeRepository.text(rows, VISIT_ID);
                String source = MotherMentorMotherCascadeRepository.text(rows, FORM_SUBMISSION_ID);
                if (source.isEmpty()) source = id;
                if (source.isEmpty()) continue;
                if (id.equals(excludedVisitId)) excludedSource = source;
                try {
                    JSONObject event = new JSONObject(MotherMentorMotherCascadeRepository.text(rows, VISIT_JSON));
                    Map<String, String> values = MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
                    long time = timestamp(event.opt(EVENT_DATE_TIMESTAMP));
                    long version = timestamp(event.opt(EVENT_VERSION));
                    LocalDate date = MotherMentorHeiSchedule.parseBirthDate(value(values, MOTHERMENTOR_VISIT_DATE));
                    if (date == null && value(values, MOTHERMENTOR_VISIT_DATE).isEmpty() && time > 0) {
                        date = new LocalDate(time);
                    }
                    boolean valid = !event.optBoolean(EVENT_VOIDED)
                            && belongs(episode, value(values, IIT_ENROLLMENT_ID), time)
                            && date != null && !date.isAfter(LocalDate.now());
                    Evidence next = new Evidence(id, source, date, time, version, values, valid);
                    Evidence current = bySource.get(source);
                    if (current == null || revisionOrder().compare(next, current) > 0) bySource.put(source, next);
                } catch (JSONException ignored) {
                    // A malformed payload is not a clinical visit.
                }
            }
        }
        bySource.remove(excludedSource);
        List<Evidence> visits = new ArrayList<>(bySource.values());
        visits.removeIf(visit -> !visit.valid);
        visits.sort(Comparator.comparing((Evidence visit) -> visit.date)
                .thenComparingLong(visit -> visit.time).thenComparing(visit -> visit.source));
        return visits;
    }

    private static boolean belongs(Episode episode, String visitEpisode, long time) {
        return visitEpisode.isEmpty() ? episode.time == 0 || time >= episode.time : episode.id.equals(visitEpisode);
    }

    private static Comparator<Evidence> revisionOrder() {
        return Comparator.comparingLong((Evidence evidence) -> evidence.version)
                .thenComparingLong(evidence -> evidence.time).thenComparing(evidence -> evidence.id);
    }

    private static long timestamp(Object raw) {
        if (raw instanceof Number) return ((Number) raw).longValue();
        if (!(raw instanceof String)) return 0;
        try {
            return Long.parseLong((String) raw);
        } catch (NumberFormatException ignored) {
            try {
                return DateTime.parse((String) raw).getMillis();
            } catch (IllegalArgumentException invalid) {
                return 0;
            }
        }
    }

    private static String value(Map<String, String> answers, String key) {
        return StringUtils.trimToEmpty(answers.get(key));
    }

    private static final class Episode {
        final String id;
        final long time;
        final String missedDate;
        final String linkedMother;
        final Map<String, String> enrollment;

        Episode(String id, long time, String missedDate, String linkedMother, Map<String, String> enrollment) {
            this.id = id;
            this.time = time;
            this.missedDate = missedDate;
            this.linkedMother = linkedMother;
            this.enrollment = enrollment;
        }
    }

    private static final class Evidence {
        final String id;
        final String source;
        final LocalDate date;
        final long time;
        final long version;
        final Map<String, String> values;
        final boolean valid;

        Evidence(String id, String source, LocalDate date, long time, long version,
                Map<String, String> values, boolean valid) {
            this.id = id;
            this.source = source;
            this.date = date;
            this.time = time;
            this.version = version;
            this.values = values;
            this.valid = valid;
        }
    }
}
