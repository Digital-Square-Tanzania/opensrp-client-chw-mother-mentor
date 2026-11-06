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
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorIitReturnUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState.Outcome;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
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
            IIT_COUNTER_AMESAFIRI, IIT_COUNTER_NYINGINE, IIT_COUNTER_HAJAPATIKANA,
            IIT_CASCADE_SOURCE_EPISODE));

    private MotherMentorIitRepository() {}

    public static MotherMentorIitState getState(SQLiteDatabase db, String clientId,
            String excludedVisitId) {
        Episode episode = episode(db, clientId);
        if (episode == null) return MotherMentorIitState.active();
        return replay(db, episode, history(db, clientId, episode, excludedVisitId), motherClosure(db, episode));
    }

    /** Only IIT's own visit history can close a linked mother card. */
    static MotherMentorIitState ownClosure(SQLiteDatabase db, String clientId) {
        Episode episode = episode(db, clientId);
        return episode == null ? MotherMentorIitState.active()
                : replay(db, episode, history(db, clientId, episode, null), null);
    }

    public static boolean belongsToCurrentEnrollment(SQLiteDatabase db, String clientId, String visitJson) {
        Episode episode = episode(db, clientId);
        if (episode == null || StringUtils.isBlank(visitJson)) return false;
        try {
            JSONObject event = new JSONObject(visitJson);
            Map<String, String> answers = MotherMentorVisitCompletion.observationValues(event.optJSONArray("obs"));
            return !event.optBoolean(EVENT_VOIDED) && belongs(episode, value(answers, IIT_ENROLLMENT_ID),
                    timestamp(event.opt(EVENT_DATE_TIMESTAMP)));
        } catch (JSONException invalid) {
            return false;
        }
    }

    /** A form can show its proposed exit without writing any visit or projection. */
    public static MotherMentorIitState preview(SQLiteDatabase db, String clientId,
            String excludedVisitId, Map<String, String> answers, LocalDate serviceDate) {
        Episode episode = episode(db, clientId);
        if (episode == null) return MotherMentorIitState.active();
        List<Evidence> history = history(db, clientId, episode, excludedVisitId);
        long time = System.currentTimeMillis();
        if (isStatusComplete(answers) && duringEpisode(episode.time, serviceDate, time)) {
            if (StringUtils.isNotBlank(excludedVisitId)) {
                for (Evidence existing : history(db, clientId, episode, null)) {
                    if (existing.id.equals(excludedVisitId) || existing.source.equals(excludedVisitId)) {
                        time = existing.time;
                        break;
                    }
                }
            }
            Map<String, String> prospective = new HashMap<>(answers);
            prospective.put(IIT_FOLLOW_UP_SCHEMA, "2");
            history.add(new Evidence("~preview", "~preview", serviceDate, time, 0, prospective, true));
        }
        return replay(db, episode, history, motherClosure(db, episode));
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
        if (!isStatusComplete(answers)) return false;
        String missedDate = StringUtils.defaultIfBlank(value(answers, IIT_MISSED_APPOINTMENT_DATE),
                state.getMissedAppointmentDate());
        return !"found".equals(value(answers, FOLLOW_UP_STATUS))
                || MotherMentorIitReturnUtils.isComplete(answers, state.getReturnState(),
                        missedDate, serviceDate);
    }

    /** Called by the explicit app migration, using the same database as the rest of the upgrade. */
    public static void upgrade(SQLiteDatabase db) {
        List<String> columns = MotherMentorMotherCascadeRepository.columns(db, TABLE);
        if (columns.isEmpty()) return;
        for (String column : COLUMNS) {
            if (!columns.contains(column)) db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + column
                    + (COUNTERS.contains(column) ? " INTEGER NOT NULL DEFAULT 0" : " TEXT"));
        }
        String mothers = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
        List<String> motherColumns = MotherMentorMotherCascadeRepository.columns(db, mothers);
        if (!motherColumns.isEmpty() && !motherColumns.contains(IIT_CASCADE_SOURCE_EPISODE)) {
            db.execSQL("ALTER TABLE " + mothers + " ADD COLUMN " + IIT_CASCADE_SOURCE_EPISODE + " TEXT");
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
        if (MotherMentorMotherClosureRepository.canRebuild(db, episode.linkedMother)) {
            // The mother rebuild writes both cards under the same transaction.
            MotherMentorMotherClosureRepository.rebuild(db, episode.linkedMother);
        } else {
            writeProjection(db, clientId, episode);
        }
    }

    static void rebuildFromMother(SQLiteDatabase db, String clientId) {
        if (!hasProjection(db)) return;
        Episode episode = episode(db, clientId);
        if (episode != null) writeProjection(db, clientId, episode);
    }

    private static void writeProjection(SQLiteDatabase db, String clientId, Episode episode) {
        MotherMentorIitState state = replay(db, episode, history(db, clientId, episode, null),
                motherClosure(db, episode));
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
        values.put(IIT_CASCADE_SOURCE_EPISODE, state.getCascadeSourceEpisode());
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

    private static MotherMentorIitState replay(SQLiteDatabase db, Episode episode, List<Evidence> visits,
            Evidence motherClosure) {
        MotherMentorIitState state = state(db, episode, (int) visits.stream()
                .filter(visit -> !"~preview".equals(visit.id)).count());
        if (motherClosure != null) visits.add(motherClosure);
        visits.sort(Comparator.comparing((Evidence visit) -> visit.date)
                .thenComparingLong(visit -> visit.time).thenComparing(visit -> visit.source));
        for (Evidence evidence : visits) {
            if (state.isClosed()) break;
            if (!"2".equals(value(evidence.values, IIT_FOLLOW_UP_SCHEMA))) continue;
            Outcome outcome = outcome(state, evidence.values, evidence.date);
            String facility = outcome == Outcome.MOVED_AREA || outcome == Outcome.MOVED_FACILITY
                    ? value(evidence.values, IIT_KITUO_KIPYA) : "";
            state = state.advance(outcome, evidence.date, facility, evidence.time);
            if (state.isClosed() && !evidence.cascadeEpisode.isEmpty()) {
                state = state.withCascadeSourceEpisode(evidence.cascadeEpisode);
            }
        }
        return state;
    }

    private static Outcome outcome(MotherMentorIitState state, Map<String, String> answers,
            LocalDate serviceDate) {
        if (!isComplete(answers, state, serviceDate)) return null;
        if ("not_found".equals(value(answers, FOLLOW_UP_STATUS))) {
            switch (value(answers, IIT_NOT_FOUND_REASON)) {
                case "deceased": return Outcome.DECEASED;
                case "moved_area": return Outcome.MOVED_AREA;
                case "moved_facility": return Outcome.MOVED_FACILITY;
                case "travelled": return Outcome.TRAVELLED;
                case "other": return Outcome.OTHER;
                default: return null;
            }
        }
        if (MotherMentorIitState.NONE.equals(state.getReturnState())) {
            return "agreed".equals(value(answers, IIT_RETURN_AGREEMENT)) ? Outcome.AGREED : Outcome.REFUSED;
        }
        return "yes".equals(value(answers, RETURNED_TO_FACILITY)) ? Outcome.RETURNED : Outcome.NOT_RETURNED;
    }

    private static Evidence motherClosure(SQLiteDatabase db, Episode episode) {
        if (episode.linkedMother.isEmpty() || !hasProjection(db)) return null;
        String previous = value(episode.enrollment, IIT_CASCADE_SOURCE_EPISODE);
        String current = MotherMentorMotherVisitDao.getContext(db, episode.linkedMother).getEnrollmentId();
        if (!previous.isEmpty() && !current.isEmpty() && !previous.equals(current)
                && "1".equals(value(episode.enrollment, IS_CLOSED))) {
            String status = value(episode.enrollment, IIT_STATUS);
            String previousReason = MotherMentorIitState.DECEASED.equals(status)
                    ? MotherMentorMotherClosure.DECEASED : MotherMentorIitState.MOVED_AREA.equals(status)
                            ? MotherMentorMotherClosure.MOVED_AREA : MotherMentorMotherClosure.MOVED_FACILITY;
            LocalDate date = MotherMentorHeiSchedule.parseDate(
                    value(episode.enrollment, IIT_TAREHE_YA_KUFUNGWA));
            if (date != null) return linkedClosure(previousReason, date, Long.MAX_VALUE,
                    value(episode.enrollment, IIT_KITUO_KIPYA), previous);
        }
        MotherMentorMotherClosureRepository.ClosureSource source =
                MotherMentorMotherClosureRepository.ownClosure(db, episode.linkedMother);
        String status = source.state.getStatus();
        if (!Arrays.asList(MotherMentorMotherClosure.DECEASED, MotherMentorMotherClosure.MOVED_AREA,
                MotherMentorMotherClosure.MOVED_FACILITY).contains(status)) return null;
        LocalDate date = MotherMentorHeiSchedule.parseDate(source.state.getClosedDate());
        if (!duringEpisode(episode.time, date, source.time)) return null;
        return linkedClosure(status, date, source.time, source.state.getNewFacility(), current);
    }

    private static Evidence linkedClosure(String reason, LocalDate date, long time, String facility,
            String sourceEpisode) {
        Map<String, String> answers = new HashMap<>();
        answers.put(IIT_FOLLOW_UP_SCHEMA, "2");
        answers.put(FOLLOW_UP_STATUS, "not_found");
        answers.put(IIT_NOT_FOUND_REASON, reason);
        answers.put(IIT_KITUO_KIPYA, facility);
        return new Evidence("~mother", "~mother", date, time, 0, answers, true, sourceEpisode);
    }

    static boolean duringEpisode(long enrollmentTime, LocalDate date, long eventTime) {
        if (date == null || date.isAfter(LocalDate.now())) return false;
        return enrollmentTime == 0 || (eventTime == 0 || eventTime >= enrollmentTime)
                && !date.isBefore(new LocalDate(enrollmentTime));
    }

    private static MotherMentorIitState state(SQLiteDatabase db, Episode episode, int visitCount) {
        MotherMentorIitState state = MotherMentorIitState.active(episode.id, episode.missedDate,
                episode.linkedMother, visitCount);
        String projected = value(episode.enrollment, IIT_STATUS);
        if ("1".equals(value(episode.enrollment, IS_CLOSED)) && !projected.matches("I[1-9]")
                && !MotherMentorMotherCascadeRepository.ownsIitClosure(db, episode.clientId)) {
            return state.close(MotherMentorIitState.LEGACY_CLOSED, null, "");
        }
        return state;
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
        return new Episode(clientId, episodeId, raw == null ? 0 : raw.time, missedDate, linked, enrollment);
    }

    static String currentEpisodeId(SQLiteDatabase db, String clientId) {
        Episode episode = episode(db, clientId);
        return episode == null ? "" : episode.id;
    }

    private static List<Evidence> history(SQLiteDatabase db, String clientId, Episode episode,
            String excludedVisitId) {
        if (MotherMentorMotherCascadeRepository.columns(db, VisitRepository.VISIT_TABLE).isEmpty()) {
            return new ArrayList<>();
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
                    String visitEpisode = value(values, IIT_ENROLLMENT_ID);
                    long version = timestamp(event.opt(EVENT_VERSION));
                    LocalDate date = MotherMentorHeiSchedule.parseBirthDate(value(values, MOTHERMENTOR_VISIT_DATE));
                    if (date == null && value(values, MOTHERMENTOR_VISIT_DATE).isEmpty() && time > 0) {
                        LocalDate sourceDate = MotherMentorHeiSchedule.parseBirthDate(
                                event.optString(EVENT_DATE_TIMESTAMP));
                        date = sourceDate == null ? new LocalDate(time) : sourceDate;
                    }
                    // Earlier IIT builds persisted the service date at midnight. A matching
                    // episode marker is enough to place such a visit on that enrollment day.
                    if (time > 0 && time < episode.time && episode.id.equals(visitEpisode)
                            && new LocalDate(episode.time).equals(date)) time = episode.time;
                    boolean valid = !event.optBoolean(EVENT_VOIDED)
                            && (value(values, FOLLOW_UP_CATEGORY).isEmpty()
                                    || MotherMentorFollowUpType.MOTHER_IIT.getValue()
                                            .equals(value(values, FOLLOW_UP_CATEGORY)))
                            && duringEpisode(episode.time, date, time)
                            && belongs(episode, visitEpisode, time);
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
        return visits;
    }

    private static boolean belongs(Episode episode, String visitEpisode, long time) {
        return visitEpisode.isEmpty() ? episode.time == 0 || time >= episode.time : episode.id.equals(visitEpisode);
    }

    private static Comparator<Evidence> revisionOrder() {
        return Comparator.comparingLong((Evidence evidence) -> evidence.version)
                .thenComparingLong(evidence -> evidence.time).thenComparing(evidence -> evidence.id);
    }

    static long timestamp(Object raw) {
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
        final String clientId;
        final String id;
        final long time;
        final String missedDate;
        final String linkedMother;
        final Map<String, String> enrollment;

        Episode(String clientId, String id, long time, String missedDate, String linkedMother,
                Map<String, String> enrollment) {
            this.clientId = clientId;
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
        final String cascadeEpisode;

        Evidence(String id, String source, LocalDate date, long time, long version,
                Map<String, String> values, boolean valid) {
            this(id, source, date, time, version, values, valid, "");
        }

        Evidence(String id, String source, LocalDate date, long time, long version,
                Map<String, String> values, boolean valid, String cascadeEpisode) {
            this.id = id;
            this.source = source;
            this.date = date;
            this.time = time;
            this.version = version;
            this.values = values;
            this.valid = valid;
            this.cascadeEpisode = cascadeEpisode;
        }
    }
}
