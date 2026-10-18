package org.smartregister.chw.mothermentor.dao;

import android.annotation.SuppressLint;
import android.database.Cursor;

import org.smartregister.chw.mothermentor.model.MotherMentorMobilizationModel;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.dao.AbstractDao;

import java.util.List;

public class MotherMentorMobilizationDao extends AbstractDao {

    public static void updateData(
            String baseEntityID,
            String mobilization_date,
            String female_clients_reached,
            String male_clients_reached) {
        String sql =
                "INSERT INTO ec_mothermentor_mobilization            ("
                        + DBConstants.KEY.ID
                        + ","
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ", "
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ", "
                        + DBConstants.KEY.FEMALE_CLIENTS_REACHED
                        + ", "
                        + DBConstants.KEY.MALE_CLIENTS_REACHED
                        + ")            VALUES (                   '"
                        + baseEntityID
                        + "',                    '"
                        + baseEntityID
                        + "',                    '"
                        + mobilization_date
                        + "',                    '"
                        + female_clients_reached
                        + "',                    '"
                        + male_clients_reached
                        + "')  ON CONFLICT ("
                        + DBConstants.KEY.ID
                        + ") DO UPDATE SET "
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + " = EXCLUDED."
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ",                                "
                        + DBConstants.KEY.FEMALE_CLIENTS_REACHED
                        + " = EXCLUDED."
                        + DBConstants.KEY.FEMALE_CLIENTS_REACHED
                        + ",                                "
                        + DBConstants.KEY.MALE_CLIENTS_REACHED
                        + " = EXCLUDED."
                        + DBConstants.KEY.MALE_CLIENTS_REACHED
                        + ";";

        updateDB(sql);
    }

    public static void saveMobilizationSession(
            String baseEntityId,
            String shTarehe,
            String shAina,
            String shAinaOther,
            String gps,
            String shMada,
            String shMadaOther,
            String elimM,
            String elimF,
            String elimTotal,
            String maoni,
            long lastInteractedWith) {
        String sql =
                "INSERT INTO ec_mothermentor_mobilization            ("
                        + DBConstants.KEY.ID
                        + ", "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ", "
                        + DBConstants.KEY.SH_TAREHE
                        + ", "
                        + DBConstants.KEY.SH_AINA
                        + ", "
                        + DBConstants.KEY.SH_AINA_OTHER
                        + ", "
                        + DBConstants.KEY.GPS
                        + ", "
                        + DBConstants.KEY.SH_MADA
                        + ", "
                        + DBConstants.KEY.SH_MADA_OTHER
                        + ", "
                        + DBConstants.KEY.ELIM_M
                        + ", "
                        + DBConstants.KEY.ELIM_F
                        + ", "
                        + DBConstants.KEY.ELIM_TOTAL
                        + ", "
                        + DBConstants.KEY.MAONI
                        + ", "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + ")            VALUES ("
                        + sqlValue(baseEntityId)
                        + ", "
                        + sqlValue(baseEntityId)
                        + ", "
                        + sqlValue(shTarehe)
                        + ", "
                        + sqlValue(shAina)
                        + ", "
                        + sqlValue(shAinaOther)
                        + ", "
                        + sqlValue(gps)
                        + ", "
                        + sqlValue(shMada)
                        + ", "
                        + sqlValue(shMadaOther)
                        + ", "
                        + sqlValue(elimM)
                        + ", "
                        + sqlValue(elimF)
                        + ", "
                        + sqlValue(elimTotal)
                        + ", "
                        + sqlValue(maoni)
                        + ", "
                        + lastInteractedWith
                        + ")  ON CONFLICT ("
                        + DBConstants.KEY.ID
                        + ") DO UPDATE SET "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = EXCLUDED."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ", "
                        + DBConstants.KEY.SH_TAREHE
                        + " = EXCLUDED."
                        + DBConstants.KEY.SH_TAREHE
                        + ", "
                        + DBConstants.KEY.SH_AINA
                        + " = EXCLUDED."
                        + DBConstants.KEY.SH_AINA
                        + ", "
                        + DBConstants.KEY.SH_AINA_OTHER
                        + " = EXCLUDED."
                        + DBConstants.KEY.SH_AINA_OTHER
                        + ", "
                        + DBConstants.KEY.GPS
                        + " = EXCLUDED."
                        + DBConstants.KEY.GPS
                        + ", "
                        + DBConstants.KEY.SH_MADA
                        + " = EXCLUDED."
                        + DBConstants.KEY.SH_MADA
                        + ", "
                        + DBConstants.KEY.SH_MADA_OTHER
                        + " = EXCLUDED."
                        + DBConstants.KEY.SH_MADA_OTHER
                        + ", "
                        + DBConstants.KEY.ELIM_M
                        + " = EXCLUDED."
                        + DBConstants.KEY.ELIM_M
                        + ", "
                        + DBConstants.KEY.ELIM_F
                        + " = EXCLUDED."
                        + DBConstants.KEY.ELIM_F
                        + ", "
                        + DBConstants.KEY.ELIM_TOTAL
                        + " = EXCLUDED."
                        + DBConstants.KEY.ELIM_TOTAL
                        + ", "
                        + DBConstants.KEY.MAONI
                        + " = EXCLUDED."
                        + DBConstants.KEY.MAONI
                        + ", "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " = EXCLUDED."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + ";";

        updateDB(sql);
    }

    public static List<MotherMentorMobilizationModel> getMobilizationSessions() {
        String sql =
                "SELECT *, CASE WHEN COALESCE(NULLIF("
                        + DBConstants.KEY.SH_TAREHE
                        + ", ''), NULLIF("
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ", '')) LIKE '____-__-__' THEN COALESCE(NULLIF("
                        + DBConstants.KEY.SH_TAREHE
                        + ", ''), NULLIF("
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ", '')) ELSE substr(COALESCE(NULLIF("
                        + DBConstants.KEY.SH_TAREHE
                        + ", ''), NULLIF("
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ", '')), 7, 4)||'-'||substr(COALESCE(NULLIF("
                        + DBConstants.KEY.SH_TAREHE
                        + ", ''), NULLIF("
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ", '')), 4, 2)||'-'||substr(COALESCE(NULLIF("
                        + DBConstants.KEY.SH_TAREHE
                        + ", ''), NULLIF("
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ", '')), 1, 2) END as orderDate FROM "
                        + Constants.TABLES.MOTHERMENTOR_MOBILIZATION
                        + " WHERE COALESCE(NULLIF("
                        + DBConstants.KEY.SH_TAREHE
                        + ", ''), NULLIF("
                        + DBConstants.KEY.MOBILIZATION_DATE
                        + ", '')) IS NOT NULL ORDER BY julianday(orderDate) DESC, "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC";

        @SuppressLint("Range")
        DataMap<MotherMentorMobilizationModel> dataMap =
                cursor -> {
                    MotherMentorMobilizationModel tbMotherMentorMobilizationModel =
                            new MotherMentorMobilizationModel();
                    tbMotherMentorMobilizationModel.setSessionId(
                            cursor.getString(
                                    cursor.getColumnIndex(DBConstants.KEY.BASE_ENTITY_ID)));
                    tbMotherMentorMobilizationModel.setSessionDate(
                            firstNonBlank(
                                    cursorString(cursor, DBConstants.KEY.SH_TAREHE),
                                    cursorString(cursor, DBConstants.KEY.MOBILIZATION_DATE)));
                    tbMotherMentorMobilizationModel.setSessionParticipants(
                            computeSessionParticipants(
                                    firstNonBlank(
                                            cursorString(cursor, DBConstants.KEY.ELIM_F),
                                            cursorString(
                                                    cursor,
                                                    DBConstants.KEY.FEMALE_CLIENTS_REACHED)),
                                    firstNonBlank(
                                            cursorString(cursor, DBConstants.KEY.ELIM_M),
                                            cursorString(
                                                    cursor,
                                                    DBConstants.KEY.MALE_CLIENTS_REACHED))));
                    tbMotherMentorMobilizationModel.setActivityType(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_AINA)));
                    tbMotherMentorMobilizationModel.setEducationTopics(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_MADA)));
                    return tbMotherMentorMobilizationModel;
                };

        List<MotherMentorMobilizationModel> res = readData(sql, dataMap);
        if (res == null || res.isEmpty()) return null;
        return res;
    }

    public static MotherMentorMobilizationModel getMobilizationSession(String sessionId) {
        String sql =
                "SELECT * FROM "
                        + Constants.TABLES.MOTHERMENTOR_MOBILIZATION
                        + " WHERE "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + sessionId.replace("'", "''")
                        + "' OR "
                        + DBConstants.KEY.ID
                        + " = '"
                        + sessionId.replace("'", "''")
                        + "' LIMIT 1";

        DataMap<MotherMentorMobilizationModel> dataMap =
                cursor -> {
                    MotherMentorMobilizationModel model = new MotherMentorMobilizationModel();
                    model.setSessionId(
                            cursor.getString(
                                    cursor.getColumnIndex(DBConstants.KEY.BASE_ENTITY_ID)));
                    model.setSessionDate(
                            firstNonBlank(
                                    cursorString(cursor, DBConstants.KEY.SH_TAREHE),
                                    cursorString(cursor, DBConstants.KEY.MOBILIZATION_DATE)));
                    model.setActivityType(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_AINA)));
                    model.setActivityTypeOther(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_AINA_OTHER)));
                    model.setGps(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.GPS)));
                    model.setEducationTopics(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_MADA)));
                    model.setEducationTopicsOther(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_MADA_OTHER)));
                    model.setMenEducated(
                            firstNonBlank(
                                    cursorString(cursor, DBConstants.KEY.ELIM_M),
                                    cursorString(cursor, DBConstants.KEY.MALE_CLIENTS_REACHED)));
                    model.setWomenEducated(
                            firstNonBlank(
                                    cursorString(cursor, DBConstants.KEY.ELIM_F),
                                    cursorString(cursor, DBConstants.KEY.FEMALE_CLIENTS_REACHED)));
                    model.setTotalEducated(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.ELIM_TOTAL)));
                    model.setComments(
                            cursor.getString(cursor.getColumnIndex(DBConstants.KEY.MAONI)));
                    model.setReferredMen(cursorString(cursor, DBConstants.KEY.REFERRED_MALE));
                    model.setReferredWomen(cursorString(cursor, DBConstants.KEY.REFERRED_FEMALE));
                    model.setReferredTotal(cursorString(cursor, DBConstants.KEY.REFERRED_TOTAL));
                    model.setReferralServices(
                            cursorString(cursor, DBConstants.KEY.FACILITY_REFERRAL_SERVICES));
                    model.setReferralOther(
                            cursorString(cursor, DBConstants.KEY.FACILITY_REFERRAL_OTHER));

                    model.setSessionParticipants(
                            computeSessionParticipants(
                                    model.getWomenEducated(), model.getMenEducated()));
                    return model;
                };

        List<MotherMentorMobilizationModel> res = readData(sql, dataMap);
        if (res == null || res.isEmpty()) {
            return null;
        }
        return res.get(0);
    }

    private static String computeSessionParticipants(
            String femaleParticipants, String maleParticipants) {
        int female = safeParseInt(femaleParticipants);
        int male = safeParseInt(maleParticipants);
        int sum = female + male;
        return String.valueOf(sum);
    }

    private static String computeCondomsIssued(String femaleCondoms, String maleCondoms) {
        int sum = safeParseInt(femaleCondoms) + safeParseInt(maleCondoms);
        return String.valueOf(sum);
    }

    private static int safeParseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String cursorString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index == -1 ? null : cursor.getString(index);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }

    private static String sqlValue(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }
}
