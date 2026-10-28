package org.smartregister.chw.mothermentor.repository;

import android.content.ContentValues;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.repository.BaseRepository;

import timber.log.Timber;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VisitDetailsRepository extends BaseRepository {

    public static final String VISIT_DETAILS_TABLE = "visit_details";
    private static final String VISIT_DETAILS_ID = DBConstants.KEY.VISIT_DETAILS_ID;
    private static final String VISIT_ID = DBConstants.KEY.VISIT_ID;
    private static final String VISIT_KEY = DBConstants.KEY.VISIT_KEY;
    private static final String PARENT_CODE = DBConstants.KEY.PARENT_CODE;
    private static final String DETAILS = DBConstants.KEY.DETAILS;
    private static final String HUMAN_READABLE = DBConstants.KEY.HUMAN_READABLE_DETAILS;
    private static final String JSON_DETAILS = DBConstants.KEY.JSON_DETAILS;
    private static final String PRE_PROCESSED_JSON = DBConstants.KEY.PREPROCESSED_DETAILS;
    private static final String PRE_PROCESSED_TYPE = DBConstants.KEY.PREPROCESSED_TYPE;
    private static final String PROCESSED = DBConstants.KEY.PROCESSED;
    private static final String UPDATED_AT = DBConstants.KEY.UPDATED_AT;
    private static final String CREATED_AT = DBConstants.KEY.CREATED_AT;

    private static final String CREATE_VISIT_TABLE =
            "CREATE TABLE "
                    + VISIT_DETAILS_TABLE
                    + "("
                    + VISIT_DETAILS_ID
                    + " VARCHAR NULL, "
                    + VISIT_ID
                    + " VARCHAR NULL, "
                    + VISIT_KEY
                    + " VARCHAR NULL, "
                    + PARENT_CODE
                    + " VARCHAR NULL, "
                    + JSON_DETAILS
                    + " VARCHAR NULL, "
                    + PRE_PROCESSED_JSON
                    + " VARCHAR NULL, "
                    + PRE_PROCESSED_TYPE
                    + " VARCHAR NULL, "
                    + DETAILS
                    + " VARCHAR NULL, "
                    + HUMAN_READABLE
                    + " VARCHAR NULL, "
                    + PROCESSED
                    + " Integer NULL, "
                    + UPDATED_AT
                    + " DATETIME NULL, "
                    + CREATED_AT
                    + " DATETIME NULL)";

    private static final String VISIT_ID_INDEX =
            "CREATE INDEX "
                    + VISIT_DETAILS_TABLE
                    + "_"
                    + VISIT_ID
                    + "_index ON "
                    + VISIT_DETAILS_TABLE
                    + "("
                    + VISIT_ID
                    + " COLLATE NOCASE , "
                    + VISIT_KEY
                    + " COLLATE NOCASE );";

    private String[] VISIT_DETAILS_COLUMNS = {
        VISIT_ID,
        VISIT_KEY,
        PARENT_CODE,
        VISIT_DETAILS_ID,
        HUMAN_READABLE,
        JSON_DETAILS,
        PRE_PROCESSED_JSON,
        PRE_PROCESSED_TYPE,
        DETAILS,
        PROCESSED,
        UPDATED_AT,
        CREATED_AT
    };

    public static void createTable(SQLiteDatabase database) {
        database.execSQL(CREATE_VISIT_TABLE);
        database.execSQL(VISIT_ID_INDEX);
        createReportIndex(database);
    }

    /** Report lookups use case-sensitive visit IDs and keys, unlike the legacy NOCASE index. */
    public static void createReportIndex(SQLiteDatabase database) {
        database.execSQL("CREATE INDEX IF NOT EXISTS mothermentor_report_visit_details ON "
                + VISIT_DETAILS_TABLE + " (" + VISIT_ID + " COLLATE BINARY, "
                + VISIT_KEY + " COLLATE BINARY)");
    }

    private ContentValues createValues(VisitDetail visitDetail) {
        ContentValues values = new ContentValues();
        values.put(VISIT_DETAILS_ID, visitDetail.getVisitDetailsId());
        values.put(VISIT_ID, visitDetail.getVisitId());
        values.put(VISIT_KEY, visitDetail.getVisitKey());
        values.put(PARENT_CODE, visitDetail.getParentCode());
        values.put(JSON_DETAILS, visitDetail.getJsonDetails());
        values.put(PRE_PROCESSED_JSON, visitDetail.getPreProcessedJson());
        values.put(PRE_PROCESSED_TYPE, visitDetail.getPreProcessedType());
        values.put(DETAILS, visitDetail.getDetails());
        values.put(HUMAN_READABLE, visitDetail.getHumanReadable());
        values.put(PROCESSED, visitDetail.getProcessed() ? 1 : 0);
        values.put(UPDATED_AT, visitDetail.getUpdatedAt().getTime());
        values.put(CREATED_AT, visitDetail.getCreatedAt().getTime());
        return values;
    }

    public void addVisitDetails(VisitDetail visitDetail) {
        addVisitDetails(visitDetail, getWritableDatabase());
    }

    public void addVisitDetails(VisitDetail visitDetail, SQLiteDatabase database) {
        if (visitDetail == null) {
            return;
        }
        // Handle updated home visit details
        database.insert(VISIT_DETAILS_TABLE, null, createValues(visitDetail));
    }

    public void completeProcessing(String visitDetailsID) {
        try {
            ContentValues values = new ContentValues();
            values.put(PROCESSED, 1);
            values.put(PRE_PROCESSED_JSON, "");
            values.put(PRE_PROCESSED_TYPE, "");
            getWritableDatabase()
                    .update(
                            VISIT_DETAILS_TABLE,
                            values,
                            VISIT_DETAILS_ID + " = ?",
                            new String[] {visitDetailsID});
        } catch (Exception e) {
            Timber.e(e);
        }
    }

    public List<VisitDetail> getVisits(String visitID) {
        List<VisitDetail> visitDetails = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor =
                    getReadableDatabase()
                            .query(
                                    VISIT_DETAILS_TABLE,
                                    VISIT_DETAILS_COLUMNS,
                                    VISIT_ID + " = ? ",
                                    new String[] {visitID},
                                    null,
                                    null,
                                    null,
                                    null);
            visitDetails = readVisitDetails(cursor);
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return visitDetails;
    }

    public List<VisitDetail> readVisitDetails(Cursor cursor) {
        List<VisitDetail> visitDetailList = new ArrayList<>();

        try {

            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {

                    VisitDetail visitDetail = new VisitDetail();
                    visitDetail.setVisitId(cursor.getString(cursor.getColumnIndex(VISIT_ID)));
                    visitDetail.setVisitDetailsId(
                            cursor.getString(cursor.getColumnIndex(VISIT_DETAILS_ID)));
                    visitDetail.setVisitKey(cursor.getString(cursor.getColumnIndex(VISIT_KEY)));
                    visitDetail.setParentCode(cursor.getString(cursor.getColumnIndex(PARENT_CODE)));
                    visitDetail.setJsonDetails(
                            cursor.getString(cursor.getColumnIndex(JSON_DETAILS)));
                    visitDetail.setPreProcessedJson(
                            cursor.getString(cursor.getColumnIndex(PRE_PROCESSED_JSON)));
                    visitDetail.setPreProcessedType(
                            cursor.getString(cursor.getColumnIndex(PRE_PROCESSED_TYPE)));
                    visitDetail.setDetails(cursor.getString(cursor.getColumnIndex(DETAILS)));
                    visitDetail.setHumanReadable(
                            cursor.getString(cursor.getColumnIndex(HUMAN_READABLE)));
                    visitDetail.setProcessed(cursor.getInt(cursor.getColumnIndex(PROCESSED)) == 1);
                    visitDetail.setCreatedAt(
                            new Date(
                                    Long.parseLong(
                                            cursor.getString(cursor.getColumnIndex(CREATED_AT)))));
                    visitDetail.setUpdatedAt(
                            new Date(
                                    Long.parseLong(
                                            cursor.getString(cursor.getColumnIndex(UPDATED_AT)))));

                    visitDetailList.add(visitDetail);
                    cursor.moveToNext();
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return visitDetailList;
    }

    public void deleteVisitDetails(String visitID) {
        SQLiteDatabase database = getWritableDatabase();
        database.delete(VISIT_DETAILS_TABLE, VISIT_ID + " = ? ", new String[] {visitID});
    }
}
