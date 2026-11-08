package org.smartregister.chw.mothermentor.repository;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.content.ContentValues;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.dao.MotherMentorMotherVisitDao;
import org.smartregister.chw.mothermentor.processor.HouseholdEnrolmentEventProcessor;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidExit;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Cross-module closure ownership. Never rewrite enrollment/visit source events or shared people. */
public final class MotherMentorMotherCascadeRepository {
    private static final String LEDGER = Constants.TABLES.MOTHERMENTOR_MOTHER_CASCADE;
    private static final String IIT = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
    private static final String CHILD = Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID;
    private static final String HOUSEHOLD = Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT;
    private static final String MOTHERS = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
    private static final String MEMBERS = "ec_family_member";
    private static final String[] TARGET_KEY = {MAMA_CASCADE_TARGET_TABLE, MAMA_CASCADE_TARGET_ID,
            MAMA_CASCADE_TARGET_ENROLLMENT_ID};

    private MotherMentorMotherCascadeRepository() {}

    /** Called by database 65 before the first mother closure backfill. */
    public static void upgrade(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + LEDGER + " ("
                + MAMA_CASCADE_TARGET_TABLE + " TEXT NOT NULL, " + MAMA_CASCADE_TARGET_ID + " TEXT NOT NULL, "
                + MAMA_CASCADE_TARGET_ENROLLMENT_ID + " TEXT NOT NULL, "
                + MAMA_CASCADE_PREVIOUS_VALUES + " TEXT NOT NULL, "
                + MAMA_CASCADE_APPLIED_VALUES + " TEXT NOT NULL, " + MAMA_CASCADE_CLAIMS + " TEXT NOT NULL, "
                + "PRIMARY KEY (" + StringUtils.join(TARGET_KEY, ", ") + "))");
    }

    public static void rebuild(SQLiteDatabase db, String motherId, MotherMentorMotherClosure state) {
        if (StringUtils.isBlank(motherId) || !hasTable(db, LEDGER)) return;
        boolean ownTransaction = !db.inTransaction();
        if (ownTransaction) db.beginTransaction();
        try {
            rebuildTargets(db, motherId, state);
            if (ownTransaction) db.setTransactionSuccessful();
        } finally {
            if (ownTransaction) db.endTransaction();
        }
    }

    private static void rebuildTargets(SQLiteDatabase db, String motherId, MotherMentorMotherClosure state) {
        MotherMentorMotherVisitDao.Context context = MotherMentorMotherVisitDao.getContext(db, motherId);
        Map<String, Desired> desired = new LinkedHashMap<>();
        boolean replayIit = MotherMentorIitRepository.hasProjection(db);
        if (state != null && state.isClosed() && !context.getEnrollmentId().isEmpty()) {
            Claim claim = new Claim(motherId, context.getEnrollmentId(), state);
            if (!replayIit && Arrays.asList(MotherMentorMotherClosure.DECEASED, MotherMentorMotherClosure.MOVED_AREA,
                    MotherMentorMotherClosure.MOVED_FACILITY).contains(state.getStatus())) {
                want(db, desired, IIT, motherId, claim);
            }
            if (MotherMentorChildEidExit.fromMotherStatus(state.getStatus()) != null) {
                LocalDate dob = MotherMentorMotherPregnancyUtils.parseDate(context.getChildDob());
                String childId = MotherMentorMotherChildRepository.resolveChild(db, motherId, dob);
                if (!childId.isEmpty() && !row(db, MEMBERS, childId).isEmpty()) {
                    want(db, desired, CHILD, childId, claim);
                }
            }
        }
        List<Owned> owned = ownership(db);
        Set<String> families = new LinkedHashSet<>();
        if (!context.getFamilyId().isEmpty()) families.add(context.getFamilyId());
        for (Owned item : owned) if (HOUSEHOLD.equals(item.target.table)
                && item.claims.stream().anyMatch(claim -> motherId.equals(claim.mother))) {
            families.add(item.target.id);
        }
        for (String family : families) householdClaims(db, family, motherId, state, desired);
        for (Owned item : owned) {
            if (replayIit && IIT.equals(item.target.table)) continue;
            for (Claim previous : item.claims) {
                if (motherId.equals(previous.mother) && !context.getEnrollmentId().equals(previous.episode)) {
                    // A fresh mother episode cannot reopen completed enrollments from her history.
                    for (java.util.Iterator<String> keys = item.applied.keys(); keys.hasNext();) {
                        String key = keys.next();
                        try { item.previous.put(key, item.applied.opt(key)); }
                        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
                    }
                }
            }
            if (HOUSEHOLD.equals(item.target.table) && families.contains(item.target.id)) {
                item.claims.clear(); // Recomputed from all current mothers, never from projected markers.
            } else {
                item.claims.removeIf(claim -> motherId.equals(claim.mother));
            }
            Desired wanted = desired.remove(item.target.key());
            if (wanted != null) item.claims.addAll(wanted.claims);
            reconcile(db, item);
        }
        for (Desired item : desired.values()) {
            if (closed(item.target.values)) continue; // No ownership of pre-existing closures.
            Owned itemOwned = new Owned(item.target, snapshot(item.target), snapshot(item.target), item.claims);
            reconcile(db, itemOwned);
        }
    }

    /** Distinguish a pre-upgrade derived IIT flag from an externally closed enrollment. */
    static boolean ownsIitClosure(SQLiteDatabase db, String clientId) {
        if (!hasTable(db, LEDGER)) return false;
        Target current = target(db, IIT, clientId);
        if (current == null) return false;
        try (Cursor rows = db.rawQuery("SELECT " + MAMA_CASCADE_APPLIED_VALUES + ", "
                + MAMA_CASCADE_CLAIMS + " FROM " + LEDGER
                + " WHERE " + MAMA_CASCADE_TARGET_TABLE + " = ? AND " + MAMA_CASCADE_TARGET_ID
                + " = ? AND " + MAMA_CASCADE_TARGET_ENROLLMENT_ID + " = ?",
                new String[] {IIT, clientId, current.episode})) {
            if (!rows.moveToFirst() || !same(snapshot(current), new JSONObject(rows.getString(0)))) {
                return false;
            }
            String currentMother = MotherMentorMotherVisitDao.getContext(db, clientId).getEnrollmentId();
            JSONArray claims = new JSONArray(rows.getString(1));
            for (int index = 0; index < claims.length(); index++) {
                Claim claim = new Claim(claims.getJSONObject(index));
                if (clientId.equals(claim.mother) && currentMother.equals(claim.episode)) return true;
            }
            return false;
        } catch (JSONException ignored) {
            return false;
        }
    }

    private static void householdClaims(SQLiteDatabase db, String family, String currentMother,
            MotherMentorMotherClosure currentState, Map<String, Desired> desired) {
        Target household = target(db, HOUSEHOLD, family);
        if (household == null || !HouseholdEnrolmentEventProcessor.CONSENT_YES.equals(
                household.values.get(CONSENT))) return;
        List<Map<String, String>> mothers = familyMothers(db, family);
        LocalDate today = LocalDate.now();
        for (Map<String, String> mother : mothers) {
            Map<String, String> person = row(db, MEMBERS, mother.get(BASE_ENTITY_ID));
            if (!closed(mother) && !closed(person) && !deceased(person)
                    && eligible(mother, today)) return;
        }
        for (Map<String, String> mother : mothers) {
            String id = mother.get(BASE_ENTITY_ID);
            MotherMentorMotherClosure exit = currentMother.equals(id) ? currentState
                    : MotherMentorMotherClosureRepository.getState(db, id, null);
            if (exit == null || !(MotherMentorMotherClosure.DECEASED.equals(exit.getStatus())
                    || MotherMentorMotherClosure.CHILD_DECEASED.equals(exit.getStatus()))) continue;
            if (MotherMentorMotherClosure.DECEASED.equals(exit.getStatus()) && liveChild(db, id, mother)) continue;
            String episode = MotherMentorMotherVisitDao.getContext(db, id).getEnrollmentId();
            if (!episode.isEmpty()) want(db, desired, HOUSEHOLD, family, new Claim(id, episode, exit));
        }
    }

    private static boolean eligible(Map<String, String> mother, LocalDate day) {
        String status = MotherMentorMotherPregnancyUtils.normalizeStatus(
                StringUtils.trimToEmpty(mother.get(PREGNANCY_STATUS)).toLowerCase(Locale.ROOT));
        String currentBreastfeeding = StringUtils.trimToEmpty(
                mother.get(MAMA_BREASTFEEDING_STATUS)).toLowerCase(Locale.ROOT);
        // A current child assessment supersedes the older enrollment breastfeeding label.
        boolean breastfeeding = "breastfeeding".equals(currentBreastfeeding)
                || (!"not_breastfeeding".equals(currentBreastfeeding) && "breast_feeding".equals(status));
        LocalDate dob = MotherMentorMotherPregnancyUtils.parseDate(mother.get(CHILD_DOB));
        return "pregnant".equals(status) || breastfeeding
                || dob != null && !dob.isAfter(day) && !dob.plusMonths(18).isBefore(day);
    }

    private static boolean liveChild(SQLiteDatabase db, String motherId, Map<String, String> mother) {
        String rawDob = StringUtils.trimToEmpty(mother.get(CHILD_DOB));
        LocalDate dob = MotherMentorMotherPregnancyUtils.parseDate(rawDob);
        boolean childDeceased = "deceased".equals(mother.get(MAMA_CHILD_STATUS));
        String currentChild = dob == null ? "" : MotherMentorMotherChildRepository.resolveChild(db, motherId, dob);
        if (columns(db, CHILD).containsAll(Arrays.asList(MOTHER_BASE_ENTITY_ID, BASE_ENTITY_ID, CHILD_DOB))) {
            try (Cursor children = db.rawQuery("SELECT " + BASE_ENTITY_ID + ", " + CHILD_DOB
                    + " FROM " + CHILD + " WHERE " + MOTHER_BASE_ENTITY_ID + " = ?", new String[] {motherId})) {
                while (children.moveToNext()) {
                    String id = children.getString(0);
                    if (childDeceased && id.equals(currentChild)) continue;
                    LocalDate linkedDob = MotherMentorMotherPregnancyUtils.parseDate(children.getString(1));
                    boolean youngOrUnknown = linkedDob == null || linkedDob.isAfter(LocalDate.now())
                            || !linkedDob.plusMonths(18).isBefore(LocalDate.now());
                    if (youngOrUnknown && !deceased(row(db, MEMBERS, id))) return true;
                }
            }
        }
        if (rawDob.isEmpty() || childDeceased) return false;
        if (dob == null || dob.isAfter(LocalDate.now())) return true; // Unknown is not evidence of death.
        if (dob.plusMonths(18).isBefore(LocalDate.now())) return false;
        String childId = MotherMentorMotherChildRepository.resolveChild(db, motherId, dob);
        return childId.isEmpty() || !deceased(row(db, MEMBERS, childId));
    }

    private static List<Map<String, String>> familyMothers(SQLiteDatabase db, String family) {
        if (!hasTable(db, MOTHERS) || !columns(db, MEMBERS).contains(RELATIONAL_ID)) return Collections.emptyList();
        List<Map<String, String>> result = new ArrayList<>();
        try (Cursor rows = db.rawQuery("SELECT e.* FROM " + MOTHERS + " e JOIN " + MEMBERS
                + " f ON f." + BASE_ENTITY_ID + " = e." + BASE_ENTITY_ID + " WHERE f."
                + RELATIONAL_ID + " = ?", new String[] {family})) {
            while (rows.moveToNext()) result.add(values(rows));
        }
        return result;
    }

    private static void want(SQLiteDatabase db, Map<String, Desired> desired, String table,
            String id, Claim claim) {
        Target target = target(db, table, id);
        LocalDate date = MotherMentorMotherPregnancyUtils.parseDate(claim.date);
        if (target == null || date == null || date.isAfter(LocalDate.now())
                || target.started != null && date.isBefore(target.started)
                || servedAnotherEnrollment(db, target, claim)) return;
        Desired item = desired.get(target.key());
        if (item == null) { item = new Desired(target); desired.put(target.key(), item); }
        item.claims.add(claim);
    }

    /** Retained old-target rows are proof that this mother enrollment already served that exit. */
    private static boolean servedAnotherEnrollment(SQLiteDatabase db, Target target, Claim claim) {
        try (Cursor rows = db.rawQuery("SELECT " + MAMA_CASCADE_CLAIMS + " FROM " + LEDGER
                + " WHERE " + MAMA_CASCADE_TARGET_TABLE + " = ? AND " + MAMA_CASCADE_TARGET_ID
                + " = ? AND " + MAMA_CASCADE_TARGET_ENROLLMENT_ID + " <> ?",
                new String[] {target.table, target.id, target.episode})) {
            while (rows.moveToNext()) {
                try {
                    JSONArray claims = new JSONArray(rows.getString(0));
                    for (int i = 0; i < claims.length(); i++) {
                        Claim previous = new Claim(claims.getJSONObject(i));
                        if (claim.mother.equals(previous.mother)
                                && claim.episode.equals(previous.episode)) return true;
                    }
                } catch (JSONException ignored) {
                    // Invalid ownership metadata cannot establish a served source enrollment.
                }
            }
        }
        return false;
    }

    private static void reconcile(SQLiteDatabase db, Owned owned) {
        Target current = target(db, owned.target.table, owned.target.id);
        if (current == null || !current.episode.equals(owned.target.episode)) {
            // Keep the served-claim history. Later replays must not apply this old exit to
            // the new target, even when both enrollments occurred on the same calendar day.
            return;
        }
        JSONObject currentValues = snapshot(current);
        if ((!CHILD.equals(current.table) && currentValues.length() != owned.applied.length())
                || !same(currentValues, owned.applied)) {
            // A later external change is not ours to roll back; preserve the historical proof.
            return;
        }
        if (CHILD.equals(current.table)) {
            owned.claims.removeIf(claim -> !claim.mother.equals(current.values.get(MOTHER_BASE_ENTITY_ID)));
            for (String field : MotherMentorChildEidRepository.CLOSURE_PROJECTIONS) {
                if (!owned.previous.has(field) && currentValues.has(field)) {
                    try { owned.previous.put(field, currentValues.opt(field)); }
                    catch (JSONException invalid) { throw new IllegalStateException(invalid); }
                }
            }
        }
        if (owned.claims.isEmpty()) {
            Map<String, String> person = row(db, MEMBERS, current.id);
            if (!current.episode.isEmpty() && (HOUSEHOLD.equals(current.table)
                    || !closed(person) && !deceased(person))) {
                JSONObject restored = owned.previous;
                if (HOUSEHOLD.equals(current.table)) {
                    MotherMentorHouseholdClosure own = MotherMentorHouseholdClosureRepository
                            .getVisitState(db, current.id, null);
                    if (own.isClosed()) restored = householdValues(own);
                }
                update(db, current, restored);
            }
            delete(db, current);
            return;
        }
        owned.claims.sort(Comparator.comparing((Claim claim) -> claim.date)
                .thenComparing(claim -> claim.mother).thenComparing(claim -> claim.episode));
        JSONObject applied = snapshot(current);
        try { applied.put(IS_CLOSED, "1"); } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        if (CHILD.equals(current.table)) {
            Claim first = owned.claims.get(0);
            MotherMentorChildEidExit exit = MotherMentorChildEidExit.fromMotherStatus(first.status);
            if (exit != null && current.values.containsKey(MTOTO_EXIT_SOURCE)) {
                LocalDate date = MotherMentorMotherPregnancyUtils.parseDate(first.date);
                String deathDate = "";
                if (exit == MotherMentorChildEidExit.E5) {
                    LocalDate death = MotherMentorMotherChildRepository.getState(db, first.mother, null).getChildDeathDate();
                    if (death != null) deathDate = death.toString();
                }
                for (Map.Entry<String, String> field : MotherMentorChildEidRepository.closureProjection(
                        exit, date, first.facility, deathDate, MotherMentorChildEidExit.MOTHER_VISIT).entrySet()) {
                    try { applied.put(field.getKey(), field.getValue()); }
                    catch (JSONException invalid) { throw new IllegalStateException(invalid); }
                }
            }
        } else if (HOUSEHOLD.equals(current.table)) {
            Claim first = owned.claims.get(0);
            String reason = MotherMentorMotherClosure.DECEASED.equals(first.status)
                    ? MotherMentorHouseholdClosure.MOTHER_DECEASED : MotherMentorHouseholdClosure.CHILD_DECEASED;
            MotherMentorHouseholdClosure own = MotherMentorHouseholdClosureRepository.getVisitState(db, current.id, null);
            MotherMentorHouseholdClosure state = own.isClosed() && own.getClosedDate().compareTo(first.date) <= 0
                    ? own : own.withMemberClosure(reason, first.date);
            JSONObject closure = householdValues(state);
            for (java.util.Iterator<String> keys = closure.keys(); keys.hasNext();) {
                String key = keys.next();
                try { applied.put(key, closure.opt(key)); }
                catch (JSONException impossible) { throw new IllegalStateException(impossible); }
            }
        }
        update(db, current, applied);
        save(db, new Owned(current, owned.previous, applied, owned.claims));
    }

    private static JSONObject householdValues(MotherMentorHouseholdClosure state) {
        JSONObject values = new JSONObject(MotherMentorHouseholdClosureRepository.observationValues(state));
        try { values.put(IS_CLOSED, "1"); } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return values;
    }

    private static Target target(SQLiteDatabase db, String table, String id) {
        if (!(IIT.equals(table) || CHILD.equals(table) || HOUSEHOLD.equals(table))) return null;
        Map<String, String> row = row(db, table, id);
        if (row.isEmpty() || !row.containsKey(IS_CLOSED)) return null;
        String event = IIT.equals(table) ? Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_IIT
                : CHILD.equals(table) ? Constants.EVENT_TYPE.MOTHER_MENTOR_ENROLL_CHILD_EID
                : Constants.EVENT_TYPE.MAMA_KINARA_HOUSEHOLD_ENROLMENT;
        String stable = StringUtils.defaultIfBlank(row.get(EVENT_DATE),
                StringUtils.defaultIfBlank(row.get(ENROLLMENT_DATE), row.get(REGISTRATION_DATE)));
        LocalDate started = MotherMentorMotherPregnancyUtils.parseDate(stable);
        String episode = stable == null || stable.trim().isEmpty() ? "" : "date:" + stable;
        Enrollment latest = latestEnrollment(db, id, event);
        if (latest != null && (started == null || !latest.date.isBefore(started))) {
            episode = latest.id; started = latest.date;
        }
        return new Target(table, id, episode, started, row);
    }

    static Enrollment latestEnrollment(SQLiteDatabase db, String id, String type) {
        if (!columns(db, "event").containsAll(Arrays.asList(EVENT_BASE_ENTITY_ID, EVENT_TYPE, EVENT_JSON))) return null;
        Map<String, Enrollment> sources = new HashMap<>();
        try (Cursor rows = db.rawQuery("SELECT * FROM event WHERE " + EVENT_BASE_ENTITY_ID
                + " = ? AND " + EVENT_TYPE + " = ?", new String[] {id, type})) {
            while (rows.moveToNext()) {
                try {
                    JSONObject event = new JSONObject(rows.getString(rows.getColumnIndexOrThrow(EVENT_JSON)));
                    String source = event.optString(EVENT_FORM_SUBMISSION_ID);
                    if (source.isEmpty()) source = text(rows, EVENT_FORM_SUBMISSION_ID);
                    String raw = event.optString(EVENT_DATE_TIMESTAMP);
                    LocalDate date = MotherMentorMotherPregnancyUtils.parseDate(raw);
                    if (source.isEmpty() || date == null) continue;
                    long time = new DateTime(raw).getMillis();
                    Enrollment candidate = new Enrollment(source, date, time, event.optLong("version"),
                            !event.optBoolean("voided") && !date.isAfter(LocalDate.now()));
                    Enrollment previous = sources.get(source);
                    if (previous == null || candidate.version > previous.version
                            || candidate.version == previous.version && candidate.time > previous.time) {
                        sources.put(source, candidate);
                    }
                } catch (JSONException | IllegalArgumentException ignored) {
                    // Invalid raw events cannot identify a current target enrollment.
                }
            }
        }
        return sources.values().stream().filter(item -> item.valid)
                .max(Comparator.comparingLong((Enrollment item) -> item.time).thenComparing(item -> item.id)).orElse(null);
    }

    private static List<Owned> ownership(SQLiteDatabase db) {
        List<Owned> result = new ArrayList<>();
        try (Cursor rows = db.rawQuery("SELECT * FROM " + LEDGER, new String[0])) {
            while (rows.moveToNext()) {
                try {
                    String table = text(rows, MAMA_CASCADE_TARGET_TABLE), id = text(rows, MAMA_CASCADE_TARGET_ID);
                    Target target = new Target(table, id, text(rows, MAMA_CASCADE_TARGET_ENROLLMENT_ID),
                            null, Collections.emptyMap());
                    List<Claim> claims = new ArrayList<>();
                    JSONArray raw = new JSONArray(text(rows, MAMA_CASCADE_CLAIMS));
                    for (int i = 0; i < raw.length(); i++) claims.add(new Claim(raw.getJSONObject(i)));
                    result.add(new Owned(target, new JSONObject(text(rows, MAMA_CASCADE_PREVIOUS_VALUES)),
                            new JSONObject(text(rows, MAMA_CASCADE_APPLIED_VALUES)), claims));
                } catch (JSONException ignored) {
                    // Damaged ownership metadata must never reopen an enrollment.
                }
            }
        }
        return result;
    }

    private static JSONObject snapshot(Target target) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(Arrays.asList(IS_CLOSED, LAST_INTERACTED_WITH));
        if (HOUSEHOLD.equals(target.table)) keys.add(CONSENT);
        if (HOUSEHOLD.equals(target.table)) keys.addAll(MotherMentorHouseholdClosureRepository.CLOSURE_COLUMNS);
        if (CHILD.equals(target.table) && target.values.containsKey(MTOTO_EXIT_SOURCE)) {
            keys.addAll(MotherMentorChildEidRepository.CLOSURE_PROJECTIONS);
        }
        for (String key : keys) if (target.values.containsKey(key)) values.put(key, target.values.get(key));
        return new JSONObject(values);
    }

    private static boolean same(JSONObject first, JSONObject second) {
        for (java.util.Iterator<String> keys = second.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!first.has(key) || !StringUtils.equals(first.optString(key, null), second.optString(key, null))) return false;
        }
        return true;
    }

    private static void update(SQLiteDatabase db, Target target, JSONObject fields) {
        ContentValues values = new ContentValues();
        for (java.util.Iterator<String> keys = fields.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!IS_CLOSED.equals(key) && !(HOUSEHOLD.equals(target.table)
                    && MotherMentorHouseholdClosureRepository.CLOSURE_COLUMNS.contains(key))
                    && !(CHILD.equals(target.table)
                        && MotherMentorChildEidRepository.CLOSURE_PROJECTIONS.contains(key))) continue;
            if (fields.isNull(key)) values.putNull(key); else values.put(key, fields.optString(key));
        }
        db.update(target.table, values, BASE_ENTITY_ID + " = ?", new String[] {target.id});
    }

    private static void save(SQLiteDatabase db, Owned item) {
        JSONArray claims = new JSONArray();
        for (Claim claim : item.claims) claims.put(claim.json());
        db.execSQL("INSERT OR REPLACE INTO " + LEDGER + " (" + StringUtils.join(TARGET_KEY, ", ")
                + ", " + MAMA_CASCADE_PREVIOUS_VALUES + ", " + MAMA_CASCADE_APPLIED_VALUES + ", "
                + MAMA_CASCADE_CLAIMS + ") VALUES (?, ?, ?, ?, ?, ?)", new Object[] {item.target.table,
                        item.target.id, item.target.episode, item.previous.toString(), item.applied.toString(), claims.toString()});
    }

    private static void delete(SQLiteDatabase db, Target target) {
        db.delete(LEDGER, MAMA_CASCADE_TARGET_TABLE + " = ? AND " + MAMA_CASCADE_TARGET_ID + " = ? AND "
                + MAMA_CASCADE_TARGET_ENROLLMENT_ID + " = ?", new String[] {target.table, target.id, target.episode});
    }

    static Map<String, String> row(SQLiteDatabase db, String table, String id) {
        if (!columns(db, table).contains(BASE_ENTITY_ID)) return Collections.emptyMap();
        try (Cursor rows = db.rawQuery("SELECT * FROM " + table + " WHERE " + BASE_ENTITY_ID + " = ? LIMIT 1", new String[] {id})) {
            return rows.moveToFirst() ? values(rows) : Collections.emptyMap();
        }
    }

    private static Map<String, String> values(Cursor row) {
        Map<String, String> result = new HashMap<>();
        for (String key : row.getColumnNames()) result.put(key, row.getString(row.getColumnIndexOrThrow(key)));
        return result;
    }

    private static boolean closed(Map<String, String> values) { return "1".equals(values.get(IS_CLOSED)); }
    private static boolean deceased(Map<String, String> values) {
        LocalDate recorded = MotherMentorMotherPregnancyUtils.parseDate(values.get(DATE_DIED));
        LocalDate dod = MotherMentorMotherPregnancyUtils.parseDate(values.get(DOD));
        return recorded != null && !recorded.isAfter(LocalDate.now())
                || dod != null && !dod.isAfter(LocalDate.now());
    }
    private static boolean hasTable(SQLiteDatabase db, String table) { return !columns(db, table).isEmpty(); }
    static List<String> columns(SQLiteDatabase db, String table) {
        List<String> result = new ArrayList<>();
        try (Cursor rows = db.rawQuery("PRAGMA table_info(" + table + ")", new String[0])) {
            while (rows.moveToNext()) result.add(rows.getString(rows.getColumnIndexOrThrow("name")));
        }
        return result;
    }
    static String text(Cursor rows, String column) {
        int index = rows.getColumnIndex(column); return index < 0 ? "" : StringUtils.defaultString(rows.getString(index));
    }

    private static final class Target {
        final String table, id, episode; final LocalDate started; final Map<String, String> values;
        Target(String table, String id, String episode, LocalDate started, Map<String, String> values) {
            this.table = table; this.id = id; this.episode = episode; this.started = started; this.values = values;
        }
        String key() { return table + ":" + id + ":" + episode; }
    }
    private static final class Claim {
        final String mother, episode, status, date, facility;
        Claim(String mother, String episode, MotherMentorMotherClosure state) {
            this.mother = mother; this.episode = episode; status = state.getStatus(); date = state.getClosedDate(); facility = state.getNewFacility();
        }
        Claim(JSONObject values) {
            mother = values.optString(BASE_ENTITY_ID); episode = values.optString(MAMA_ENROLLMENT_ID);
            status = values.optString(MAMA_STATUS); date = values.optString(MAMA_TAREHE_YA_KUFUNGWA); facility = values.optString(MAMA_KITUO_KIPYA);
        }
        JSONObject json() {
            Map<String, String> values = new LinkedHashMap<>(); values.put(BASE_ENTITY_ID, mother);
            values.put(MAMA_ENROLLMENT_ID, episode); values.put(MAMA_STATUS, status);
            values.put(MAMA_TAREHE_YA_KUFUNGWA, date); values.put(MAMA_KITUO_KIPYA, facility);
            return new JSONObject(values);
        }
    }
    private static final class Desired {
        final Target target; final List<Claim> claims = new ArrayList<>(); Desired(Target target) { this.target = target; }
    }
    private static final class Owned {
        final Target target; final JSONObject previous, applied; final List<Claim> claims;
        Owned(Target target, JSONObject previous, JSONObject applied, List<Claim> claims) {
            this.target = target; this.previous = previous; this.applied = applied; this.claims = claims;
        }
    }
    static final class Enrollment {
        final String id; final LocalDate date; final long time, version; final boolean valid;
        Enrollment(String id, LocalDate date, long time, long version, boolean valid) {
            this.id = id; this.date = date; this.time = time; this.version = version; this.valid = valid;
        }
    }
}
