package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.repository.BaseRepository;

import timber.log.Timber;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VisitRepository extends BaseRepository {

    public static final String VISIT_TABLE = "visits";
    private static final String VISIT_ID = DBConstants.KEY.VISIT_ID;
    private static final String VISIT_TYPE = DBConstants.KEY.VISIT_TYPE;
    private static final String VISIT_GROUP = DBConstants.KEY.VISIT_GROUP;
    private static final String PARENT_VISIT_ID = DBConstants.KEY.PARENT_VISIT_ID;
    public static final String PARENT_VISIT_ID_INDEX =
            "CREATE INDEX "
                    + VISIT_TABLE
                    + "_"
                    + PARENT_VISIT_ID
                    + "_index ON "
                    + VISIT_TABLE
                    + "("
                    + PARENT_VISIT_ID
                    + " COLLATE NOCASE );";
    private static final String BASE_ENTITY_ID = DBConstants.KEY.BASE_ENTITY_ID;
    private static final String VISIT_DATE = DBConstants.KEY.VISIT_DATE;
    private static final String VISIT_JSON = DBConstants.KEY.VISIT_JSON;
    private static final String PRE_PROCESSED = DBConstants.KEY.PRE_PROCESSED;
    private static final String FORM_SUBMISSION_ID = DBConstants.KEY.FORM_SUBMISSION_ID;
    private static final String PROCESSED = DBConstants.KEY.PROCESSED;
    private static final String UPDATED_AT = DBConstants.KEY.UPDATED_AT;
    private static final String CREATED_AT = DBConstants.KEY.CREATED_AT;
    private static final String CREATE_VISIT_TABLE =
            "CREATE TABLE "
                    + VISIT_TABLE
                    + "("
                    + VISIT_ID
                    + " VARCHAR NULL, "
                    + VISIT_TYPE
                    + " VARCHAR NULL, "
                    + PARENT_VISIT_ID
                    + " VARCHAR NULL, "
                    + BASE_ENTITY_ID
                    + " VARCHAR NULL, "
                    + VISIT_DATE
                    + " VARCHAR NULL, "
                    + VISIT_JSON
                    + " VARCHAR NULL, "
                    + PRE_PROCESSED
                    + " VARCHAR NULL, "
                    + FORM_SUBMISSION_ID
                    + " VARCHAR NULL, "
                    + PROCESSED
                    + " Integer NULL, "
                    + UPDATED_AT
                    + " DATETIME NULL, "
                    + CREATED_AT
                    + " DATETIME NULL)";
    private static final String BASE_ENTITY_ID_INDEX =
            "CREATE INDEX "
                    + VISIT_TABLE
                    + "_"
                    + BASE_ENTITY_ID
                    + "_index ON "
                    + VISIT_TABLE
                    + "("
                    + BASE_ENTITY_ID
                    + " COLLATE NOCASE , "
                    + VISIT_TYPE
                    + " COLLATE NOCASE , "
                    + VISIT_DATE
                    + " COLLATE NOCASE);";
    private String[] VISIT_COLUMNS = {
        VISIT_ID,
        VISIT_TYPE,
        VISIT_GROUP,
        PARENT_VISIT_ID,
        BASE_ENTITY_ID,
        VISIT_DATE,
        VISIT_JSON,
        PRE_PROCESSED,
        FORM_SUBMISSION_ID,
        PROCESSED,
        UPDATED_AT,
        CREATED_AT
    };

    public static String ADD_VISIT_GROUP_COLUMN =
            "ALTER TABLE " + VISIT_TABLE + " ADD COLUMN " + VISIT_GROUP + " VARCHAR;";

    public static void createTable(SQLiteDatabase database) {
        database.execSQL(CREATE_VISIT_TABLE);
        database.execSQL(BASE_ENTITY_ID_INDEX);
        database.execSQL(PARENT_VISIT_ID_INDEX);
    }

    private ContentValues createValues(Visit visit) {
        ContentValues values = new ContentValues();
        values.put(VISIT_ID, visit.getVisitId());
        values.put(VISIT_TYPE, visit.getVisitType());
        values.put(VISIT_GROUP, visit.getVisitGroup());
        values.put(PARENT_VISIT_ID, visit.getParentVisitID());
        values.put(BASE_ENTITY_ID, visit.getBaseEntityId());
        values.put(VISIT_DATE, visit.getDate() != null ? visit.getDate().getTime() : null);
        values.put(VISIT_JSON, visit.getJson());
        values.put(PRE_PROCESSED, visit.getPreProcessedJson());
        values.put(FORM_SUBMISSION_ID, visit.getFormSubmissionId());
        values.put(PROCESSED, visit.getProcessed());
        values.put(UPDATED_AT, visit.getUpdatedAt().getTime());
        values.put(CREATED_AT, visit.getCreatedAt().getTime());
        return values;
    }

    public void addVisit(Visit visit) {
        addVisit(visit, getWritableDatabase());
    }

    public void addVisit(Visit visit, SQLiteDatabase database) {
        if (visit == null) {
            return;
        }
        // Handle updated home visit details
        database.insert(VISIT_TABLE, null, createValues(visit));
        MotherMentorMotherDeliveryRepository.rebuildForVisit(database, visit);
        MotherMentorPartnerClosureRepository.rebuildForVisit(database, visit);
        MotherMentorHouseholdClosureRepository.rebuildForVisit(database, visit);
        MotherMentorIitRepository.rebuildForVisit(database, visit);
        MotherMentorMotherChildRepository.rebuildForVisit(database, visit);
        MotherMentorChildEidRepository.rebuildForVisit(database, visit);
        MotherMentorMotherClosureRepository.rebuildForVisit(database, visit);
        EventBus.getDefault().post(visit);
    }

    /** Replace only routine mother evidence when its source revision advances. */
    public void refreshMotherVisit(Visit existing, Visit incoming, SQLiteDatabase database,
            VisitDetailsRepository detailsRepository) {
        if (existing == null || incoming == null
                || !StringUtils.equals(existing.getBaseEntityId(), incoming.getBaseEntityId())
                || !StringUtils.equals(existing.getFormSubmissionId(), incoming.getFormSubmissionId())
                || !MotherMentorMotherDeliveryRepository.isMotherVisit(existing)
                || !MotherMentorMotherDeliveryRepository.isMotherVisit(incoming)) return;
        if (database == null) database = getWritableDatabase();
        long incomingVersion = motherSourceVersion(incoming);
        boolean identical = StringUtils.equals(existing.getJson(), incoming.getJson());
        boolean replace = !identical && incomingVersion > 0
                && incomingVersion >= motherSourceVersion(existing);
        boolean ownTransaction = !database.inTransaction();
        if (ownTransaction) database.beginTransaction();
        try {
            if (replace) {
                incoming.setVisitId(existing.getVisitId());
                incoming.setCreatedAt(existing.getCreatedAt());
                incoming.setDate(existing.getDate());
                incoming.setParentVisitID(existing.getParentVisitID());
                database.update(VISIT_TABLE, createValues(incoming), VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                database.delete(VisitDetailsRepository.VISIT_DETAILS_TABLE, VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                if (incoming.getVisitDetails() != null) {
                    for (List<VisitDetail> details : incoming.getVisitDetails().values()) {
                        if (details == null) continue;
                        for (VisitDetail detail : details) {
                            detail.setVisitId(existing.getVisitId());
                            detailsRepository.addVisitDetails(detail, database);
                        }
                    }
                }
            } else if (Boolean.TRUE.equals(incoming.getProcessed())
                    && !Boolean.TRUE.equals(existing.getProcessed()) && identical) {
                ContentValues finalized = new ContentValues();
                finalized.put(PROCESSED, 1);
                finalized.put(PRE_PROCESSED, "");
                database.update(VISIT_TABLE, finalized, VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                existing.setProcessed(true);
            }
            MotherMentorMotherDeliveryRepository.rebuild(database, existing.getBaseEntityId());
            MotherMentorMotherChildRepository.rebuild(database, existing.getBaseEntityId());
            MotherMentorChildEidRepository.rebuildForMother(database, existing.getBaseEntityId());
            if (ownTransaction) database.setTransactionSuccessful();
        } finally {
            if (ownTransaction) database.endTransaction();
        }
        EventBus.getDefault().post(replace ? incoming : existing);
    }

    private long motherSourceVersion(Visit visit) {
        try {
            return Math.max(0, new JSONObject(visit.getJson()).optLong("version", 0));
        } catch (JSONException invalid) {
            return 0;
        }
    }

    /** Refresh only partner evidence on replay; other visit types keep their existing sync path. */
    public void refreshPartnerVisit(Visit existing, Visit incoming, SQLiteDatabase database,
            VisitDetailsRepository detailsRepository) {
        if (existing == null || incoming == null
                || !StringUtils.equals(existing.getBaseEntityId(), incoming.getBaseEntityId())
                || !StringUtils.equals(existing.getFormSubmissionId(), incoming.getFormSubmissionId())
                || !MotherMentorPartnerClosureRepository.isPartnerVisit(existing)
                || !StringUtils.equals(existing.getVisitType(), incoming.getVisitType())) return;
        if (database == null) database = getWritableDatabase();
        long incomingVersion = sourceVersion(incoming);
        long existingVersion = sourceVersion(existing);
        boolean identical = StringUtils.equals(existing.getJson(), incoming.getJson());
        // Unversioned replays may rebuild, but cannot replace a different source payload.
        boolean replace = !identical && incomingVersion > 0 && incomingVersion >= existingVersion;
        boolean ownTransaction = !database.inTransaction();
        if (ownTransaction) database.beginTransaction();
        try {
            if (replace) {
                incoming.setVisitId(existing.getVisitId());
                incoming.setCreatedAt(existing.getCreatedAt());
                incoming.setDate(existing.getDate());
                incoming.setParentVisitID(existing.getParentVisitID());
                database.update(VISIT_TABLE, createValues(incoming), VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                database.delete(VisitDetailsRepository.VISIT_DETAILS_TABLE, VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                if (incoming.getVisitDetails() != null) {
                    for (List<VisitDetail> details : incoming.getVisitDetails().values()) {
                        if (details == null) continue;
                        for (VisitDetail detail : details) {
                            detail.setVisitId(existing.getVisitId());
                            detailsRepository.addVisitDetails(detail, database);
                        }
                    }
                }
            }
            MotherMentorPartnerClosureRepository.rebuild(database, existing.getBaseEntityId());
            if (ownTransaction) database.setTransactionSuccessful();
        } finally {
            if (ownTransaction) database.endTransaction();
        }
        EventBus.getDefault().post(replace ? incoming : existing);
    }

    /** Refresh household source evidence without changing another workflow's replay contract. */
    public void refreshHouseholdVisit(Visit existing, Visit incoming, SQLiteDatabase database,
            VisitDetailsRepository detailsRepository) {
        if (existing == null || incoming == null
                || !StringUtils.equals(existing.getBaseEntityId(), incoming.getBaseEntityId())
                || !StringUtils.equals(existing.getFormSubmissionId(), incoming.getFormSubmissionId())
                || !MotherMentorHouseholdClosureRepository.isHouseholdVisit(existing)
                || !StringUtils.equals(existing.getVisitType(), incoming.getVisitType())) return;
        if (database == null) database = getWritableDatabase();
        boolean identical = StringUtils.equals(existing.getJson(), incoming.getJson());
        long version = householdSourceVersion(incoming);
        boolean replace = !identical && version > 0 && version >= householdSourceVersion(existing);
        boolean ownTransaction = !database.inTransaction();
        if (ownTransaction) database.beginTransaction();
        try {
            if (replace) {
                incoming.setVisitId(existing.getVisitId());
                incoming.setCreatedAt(existing.getCreatedAt());
                incoming.setDate(existing.getDate());
                incoming.setParentVisitID(existing.getParentVisitID());
                database.update(VISIT_TABLE, createValues(incoming), VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                database.delete(VisitDetailsRepository.VISIT_DETAILS_TABLE, VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                if (incoming.getVisitDetails() != null) {
                    for (List<VisitDetail> details : incoming.getVisitDetails().values()) {
                        if (details == null) continue;
                        for (VisitDetail detail : details) {
                            detail.setVisitId(existing.getVisitId());
                            detailsRepository.addVisitDetails(detail, database);
                        }
                    }
                }
            }
            MotherMentorHouseholdClosureRepository.rebuild(database, existing.getBaseEntityId());
            if (ownTransaction) database.setTransactionSuccessful();
        } finally {
            if (ownTransaction) database.endTransaction();
        }
        EventBus.getDefault().post(replace ? incoming : existing);
    }

    private long householdSourceVersion(Visit visit) {
        try {
            return Math.max(0, new JSONObject(visit.getJson()).optLong("version", 0));
        } catch (JSONException invalid) {
            return 0;
        }
    }

    private long sourceVersion(Visit visit) {
        try {
            JsonObject payload = JsonFormUtils.gson.fromJson(
                    visit.getJson(), JsonObject.class);
            // Event's constructor initializes a timestamp even when old JSON omits this field.
            if (payload == null || !payload.has("version") || payload.get("version").isJsonNull()) {
                return 0;
            }
            org.smartregister.domain.Event event =
                    JsonFormUtils.gson.fromJson(visit.getJson(), org.smartregister.domain.Event.class);
            Long version = event == null ? null : event.getVersion();
            return version == null ? 0 : Math.max(0, version);
        } catch (JsonParseException invalid) {
            return 0;
        }
    }

    /** Refresh only linked Child EID source evidence, retaining its canonical visit identity. */
    public void refreshChildEidVisit(Visit existing, Visit incoming, SQLiteDatabase database,
            VisitDetailsRepository detailsRepository) {
        if (existing == null || incoming == null
                || !StringUtils.equals(existing.getBaseEntityId(), incoming.getBaseEntityId())
                || !StringUtils.equals(existing.getFormSubmissionId(), incoming.getFormSubmissionId())
                || !MotherMentorMotherChildRepository.isChildEidVisit(existing)
                || !StringUtils.equals(existing.getVisitType(), incoming.getVisitType())) return;
        if (database == null) database = getWritableDatabase();
        boolean identical = StringUtils.equals(existing.getJson(), incoming.getJson());
        long version = childEidSourceVersion(incoming);
        boolean replace = !identical && version > 0 && version >= childEidSourceVersion(existing);
        boolean ownTransaction = !database.inTransaction();
        if (ownTransaction) database.beginTransaction();
        try {
            if (replace) {
                incoming.setVisitId(existing.getVisitId());
                incoming.setCreatedAt(existing.getCreatedAt());
                incoming.setDate(existing.getDate());
                incoming.setParentVisitID(existing.getParentVisitID());
                database.update(VISIT_TABLE, createValues(incoming), VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                database.delete(VisitDetailsRepository.VISIT_DETAILS_TABLE, VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                if (incoming.getVisitDetails() != null) {
                    for (List<VisitDetail> details : incoming.getVisitDetails().values()) {
                        if (details == null) continue;
                        for (VisitDetail detail : details) {
                            detail.setVisitId(existing.getVisitId());
                            detailsRepository.addVisitDetails(detail, database);
                        }
                    }
                }
            } else if (Boolean.TRUE.equals(incoming.getProcessed())
                    && !Boolean.TRUE.equals(existing.getProcessed()) && identical) {
                ContentValues finalized = new ContentValues();
                finalized.put(PROCESSED, 1);
                finalized.put(PRE_PROCESSED, "");
                database.update(VISIT_TABLE, finalized, VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                existing.setProcessed(true);
            }
            MotherMentorMotherChildRepository.rebuildForChild(database, existing.getBaseEntityId());
            MotherMentorChildEidRepository.rebuild(database, existing.getBaseEntityId());
            MotherMentorMotherClosureRepository.rebuildForChild(database, existing.getBaseEntityId());
            if (ownTransaction) database.setTransactionSuccessful();
        } finally {
            if (ownTransaction) database.endTransaction();
        }
        EventBus.getDefault().post(replace ? incoming : existing);
    }

    private long childEidSourceVersion(Visit visit) {
        try { return Math.max(0, new org.json.JSONObject(visit.getJson()).optLong("version", 0)); }
        catch (org.json.JSONException invalid) { return 0; }
    }

    /** Replay a versioned IIT correction without overwriting a local unfinished visit. */
    public void refreshIitVisit(Visit existing, Visit incoming, SQLiteDatabase database,
            VisitDetailsRepository detailsRepository) {
        if (!MotherMentorIitRepository.isIitVisit(existing)
                || !MotherMentorIitRepository.isIitVisit(incoming)
                || !StringUtils.equals(existing.getBaseEntityId(), incoming.getBaseEntityId())
                || !StringUtils.equals(existing.getFormSubmissionId(), incoming.getFormSubmissionId())) return;
        if (database == null) database = getWritableDatabase();
        long version = motherSourceVersion(incoming);
        boolean replace = Boolean.TRUE.equals(existing.getProcessed())
                && !StringUtils.equals(existing.getJson(), incoming.getJson())
                && version > 0 && version >= motherSourceVersion(existing);
        boolean ownTransaction = !database.inTransaction();
        if (ownTransaction) database.beginTransaction();
        try {
            if (replace) {
                incoming.setVisitId(existing.getVisitId());
                incoming.setCreatedAt(existing.getCreatedAt());
                incoming.setParentVisitID(existing.getParentVisitID());
                database.update(VISIT_TABLE, createValues(incoming), VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                database.delete(VisitDetailsRepository.VISIT_DETAILS_TABLE, VISIT_ID + " = ?",
                        new String[] {existing.getVisitId()});
                if (incoming.getVisitDetails() != null) {
                    for (List<VisitDetail> details : incoming.getVisitDetails().values()) {
                        if (details == null) continue;
                        for (VisitDetail detail : details) {
                            detail.setVisitId(existing.getVisitId());
                            detailsRepository.addVisitDetails(detail, database);
                        }
                    }
                }
            }
            MotherMentorIitRepository.rebuildForVisit(database, replace ? incoming : existing);
            if (ownTransaction) database.setTransactionSuccessful();
        } finally {
            if (ownTransaction) database.endTransaction();
        }
        EventBus.getDefault().post(replace ? incoming : existing);
    }

    public String getParentVisitEventID(
            String baseEntityID, String parentEventType, Date eventDate) {
        if (StringUtils.isBlank(baseEntityID)
                || StringUtils.isBlank(parentEventType)
                || eventDate == null) return null;

        String visitID = null;
        Cursor cursor = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String sql =
                "select "
                        + VISIT_ID
                        + " from visits where "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = ? COLLATE NOCASE and "
                        + DBConstants.KEY.VISIT_TYPE
                        + " = ? COLLATE NOCASE and strftime('%Y-%m-%d',"
                        + DBConstants.KEY.VISIT_DATE
                        + " / 1000, 'unixepoch') = ? ";
        try {
            cursor =
                    getReadableDatabase()
                            .rawQuery(
                                    sql,
                                    new String[] {
                                        baseEntityID, parentEventType, sdf.format(eventDate)
                                    });
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    visitID = cursor.getString(cursor.getColumnIndex(VISIT_ID));
                    cursor.moveToNext();
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return visitID;
    }

    public void deleteVisit(String visitID) {
        try {
            SQLiteDatabase database = getWritableDatabase();
            Set<String> mothers = MotherMentorMotherDeliveryRepository.affectedMothers(database, visitID);
            Visit removed = getVisitByVisitId(visitID);
            Set<String> households =
                    MotherMentorHouseholdClosureRepository.affectedHouseholds(database, visitID);
            Set<String> affectedPartners =
                    MotherMentorPartnerClosureRepository.affectedPartners(database, visitID);
            Set<String> affectedMothers = MotherMentorMotherClosureRepository.affectedMothers(database, visitID);
            database.delete(VISIT_TABLE, VISIT_ID + "= ?", new String[] {visitID});
            database
                    .delete(
                            VisitDetailsRepository.VISIT_DETAILS_TABLE,
                            VISIT_ID + "= ?",
                            new String[] {visitID});
            for (String mother : mothers) {
                MotherMentorMotherDeliveryRepository.rebuild(database, mother);
            }
            MotherMentorMotherChildRepository.rebuildForVisit(database, removed);
            for (String household : households) {
                MotherMentorHouseholdClosureRepository.rebuild(database, household);
            }
            for (String partner : affectedPartners) {
                MotherMentorPartnerClosureRepository.rebuild(database, partner);
            }
            MotherMentorChildEidRepository.rebuildForVisit(database, removed);
            MotherMentorIitRepository.rebuildForVisit(database, removed);
            if (removed != null && MotherMentorFollowUpType.CHILD_EID.getEventType().equals(removed.getVisitType())) {
                MotherMentorMotherClosureRepository.rebuildForChild(database, removed.getBaseEntityId());
            } else {
                for (String motherId : affectedMothers) MotherMentorMotherClosureRepository.rebuild(database, motherId);
            }
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public void completeProcessing(String visitID) {
        try {
            SQLiteDatabase database = getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(PROCESSED, 1);
            values.put(PRE_PROCESSED, "");
            database
                    .update(VISIT_TABLE, values, VISIT_ID + " = ?", new String[] {visitID});
            Visit canonical = getVisitByVisitId(visitID);
            MotherMentorMotherDeliveryRepository.rebuildForVisit(database, canonical);
            MotherMentorMotherChildRepository.rebuildForVisit(database, canonical);
            for (String household : MotherMentorHouseholdClosureRepository.affectedHouseholds(database, visitID)) {
                MotherMentorHouseholdClosureRepository.rebuild(database, household);
            }
            for (String partner : MotherMentorPartnerClosureRepository.affectedPartners(database, visitID)) {
                MotherMentorPartnerClosureRepository.rebuild(database, partner);
            }
            MotherMentorChildEidRepository.rebuildForVisit(database, canonical);
            MotherMentorIitRepository.rebuildForVisit(database, canonical);
            MotherMentorMotherClosureRepository.rebuildForVisit(database, canonical);
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public List<Visit> readVisits(Cursor cursor) {
        List<Visit> visits = new ArrayList<>();

        try {

            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {

                    Visit visit = new Visit();
                    visit.setVisitId(cursor.getString(cursor.getColumnIndex(VISIT_ID)));
                    visit.setVisitType(cursor.getString(cursor.getColumnIndex(VISIT_TYPE)));
                    visit.setVisitGroup(cursor.getString(cursor.getColumnIndex(VISIT_GROUP)));
                    visit.setParentVisitID(
                            cursor.getString(cursor.getColumnIndex(PARENT_VISIT_ID)));
                    visit.setPreProcessedJson(
                            cursor.getString(cursor.getColumnIndex(PRE_PROCESSED)));
                    visit.setBaseEntityId(cursor.getString(cursor.getColumnIndex(BASE_ENTITY_ID)));
                    visit.setDate(
                            new Date(
                                    Long.parseLong(
                                            cursor.getString(cursor.getColumnIndex(VISIT_DATE)))));
                    visit.setJson(cursor.getString(cursor.getColumnIndex(VISIT_JSON)));
                    visit.setFormSubmissionId(
                            cursor.getString(cursor.getColumnIndex(FORM_SUBMISSION_ID)));
                    visit.setProcessed(cursor.getInt(cursor.getColumnIndex(PROCESSED)) == 1);
                    visit.setCreatedAt(
                            new Date(
                                    Long.parseLong(
                                            cursor.getString(cursor.getColumnIndex(CREATED_AT)))));
                    visit.setUpdatedAt(
                            new Date(
                                    Long.parseLong(
                                            cursor.getString(cursor.getColumnIndex(UPDATED_AT)))));

                    visits.add(visit);
                    cursor.moveToNext();
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return visits;
    }

    public List<Visit> getAllUnSynced(Long last_edit_time) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    PROCESSED + " = ? AND UPDATED_AT <= ? ",
                                    new String[] {"0", last_edit_time.toString()},
                                    null,
                                    null,
                                    CREATED_AT + " ASC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public List<Visit> getAllUnSynced() {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    PROCESSED + " = ? ",
                                    new String[] {"0"},
                                    null,
                                    null,
                                    CREATED_AT + " ASC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public List<Visit> getAllVisitsProcessed(String visitType, String baseEntityId) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    PROCESSED
                                            + " = ? AND "
                                            + DBConstants.KEY.VISIT_TYPE
                                            + " = ?  AND "
                                            + DBConstants.KEY.BASE_ENTITY_ID
                                            + " = ?",
                                    new String[] {"1", visitType, baseEntityId},
                                    null,
                                    null,
                                    CREATED_AT + " ASC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public List<Visit> getAllUnSynced(Long last_edit_time, String baseEntityID) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    PROCESSED
                                            + " = ? AND UPDATED_AT <= ? AND "
                                            + BASE_ENTITY_ID
                                            + " = ? ",
                                    new String[] {"0", last_edit_time.toString(), baseEntityID},
                                    null,
                                    null,
                                    CREATED_AT + " ASC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public List<Visit> getVisits(String baseEntityID, String visitType) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    BASE_ENTITY_ID + " = ? AND " + VISIT_TYPE + " = ? ",
                                    new String[] {baseEntityID, visitType},
                                    null,
                                    null,
                                    CREATED_AT + " ASC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public List<Visit> getVisitsByGroup(String visitGroup) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    VISIT_GROUP + " = ? ",
                                    new String[] {visitGroup},
                                    null,
                                    null,
                                    CREATED_AT + " ASC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public List<Visit> getUniqueDayLatestThreeVisits(String baseEntityID, String visitType) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            String query =
                    "select STRFTIME('%Y%m%d', datetime(("
                            + VISIT_DATE
                            + ")/1000,'unixepoch')) as d,* from "
                            + VISIT_TABLE
                            + " where "
                            + VISIT_TYPE
                            + " = '"
                            + visitType
                            + "' AND "
                            + BASE_ENTITY_ID
                            + " = '"
                            + baseEntityID
                            + "'  group by d order by "
                            + VISIT_DATE
                            + " desc limit 3";
            cursor = getReadableDatabase().rawQuery(query, null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public List<Visit> getVisitsByVisitId(String visitID) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    VISIT_ID + " = ? ",
                                    new String[] {visitID},
                                    null,
                                    null,
                                    VISIT_DATE + " DESC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public Visit getVisitByVisitId(String visitID) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    VISIT_ID + " = ? ",
                                    new String[] {visitID},
                                    null,
                                    null,
                                    VISIT_DATE + " DESC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits.size() == 1 ? visits.get(0) : null;
    }

    public List<Visit> getChildEvents(String visitID) {
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    PARENT_VISIT_ID + " = ? ",
                                    new String[] {visitID},
                                    null,
                                    null,
                                    VISIT_DATE + " DESC ",
                                    null);
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visits;
    }

    public Visit getVisitByFormSubmissionID(String formSubmissionID) {
        if (StringUtils.isBlank(formSubmissionID)) return null;

        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_TABLE,
                                    VISIT_COLUMNS,
                                    FORM_SUBMISSION_ID + " = ? ",
                                    new String[] {formSubmissionID},
                                    null,
                                    null,
                                    VISIT_DATE + " DESC ",
                                    "1");
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return (visits.size() > 0) ? visits.get(0) : null;
    }

    public Visit getLatestVisit(String baseEntityID, String visitType) {
        return getLatestVisit(baseEntityID, visitType, null);
    }

    public Visit getLatestVisit(
            String baseEntityID, String visitType, SQLiteDatabase sqLiteDatabase) {
        if (sqLiteDatabase == null) {
            sqLiteDatabase = getReadableDatabase();
        }
        List<Visit> visits = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    sqLiteDatabase.query(
                            VISIT_TABLE,
                            VISIT_COLUMNS,
                            BASE_ENTITY_ID + " = ? AND " + VISIT_TYPE + " = ? ",
                            new String[] {baseEntityID, visitType},
                            null,
                            null,
                            VISIT_DATE + " DESC ",
                            "1");
            visits = readVisits(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return (visits.size() > 0) ? visits.get(0) : null;
    }

    public void setNotVisitingDate(String date, String baseID) {
        try {
            ContentValues values = new ContentValues();
            values.put(DBConstants.KEY.VISIT_NOT_DONE, date);
            getWritableDatabase()
                    .update(
                            Constants.TABLES.MOTHERMENTOR_ENROLLMENT,
                            values,
                            DBConstants.KEY.BASE_ENTITY_ID + " = ?",
                            new String[] {baseID});
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public String getLastInteractedWithAndVisitNotDone(String baseEntityID, String dateColumn) {
        SQLiteDatabase database = getReadableDatabase();
        Cursor cursor = null;
        try {
            if (database == null) {
                return null;
            }

            cursor =
                    database.query(
                            Constants.TABLES.MOTHERMENTOR_ENROLLMENT,
                            new String[] {dateColumn},
                            BASE_ENTITY_ID + " = ? " + COLLATE_NOCASE,
                            new String[] {baseEntityID},
                            null,
                            null,
                            null);

            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                String date = cursor.getString(cursor.getColumnIndex(dateColumn));
                return date;
            }
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}
