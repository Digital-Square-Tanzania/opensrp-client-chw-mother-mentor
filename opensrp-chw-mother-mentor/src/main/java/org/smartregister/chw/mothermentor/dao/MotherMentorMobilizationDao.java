package org.smartregister.chw.mothermentor.dao;


import android.annotation.SuppressLint;
import android.database.Cursor;

import org.smartregister.chw.mothermentor.model.MotherMentorMobilizationModel;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.dao.AbstractDao;

import java.util.List;


public class MotherMentorMobilizationDao extends AbstractDao {

    public static void updateData(String baseEntityID, String mobilization_date, String female_clients_reached, String male_clients_reached) {
        String sql = "INSERT INTO ec_mothermentor_mobilization " +
                "           (id,base_entity_id, mobilization_date, female_clients_reached, male_clients_reached) " +
                "           VALUES (" +
                "                   '" + baseEntityID + "', " +
                "                   '" + baseEntityID + "', " +
                "                   '" + mobilization_date + "', " +
                "                   '" + female_clients_reached + "', " +
                "                   '" + male_clients_reached + "') " +
                " ON CONFLICT (id) DO UPDATE SET mobilization_date = EXCLUDED.mobilization_date, " +
                "                               female_clients_reached = EXCLUDED.female_clients_reached, " +
                "                               male_clients_reached = EXCLUDED.male_clients_reached;";

        updateDB(sql);
    }

    public static void saveMobilizationSession(String baseEntityId,
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
        String sql = "INSERT INTO ec_mothermentor_mobilization " +
                "           (id, base_entity_id, sh_tarehe, sh_aina, sh_aina_other, gps, sh_mada, sh_mada_other, elim_m, elim_f, elim_total, maoni, last_interacted_with) " +
                "           VALUES (" +
                sqlValue(baseEntityId) + ", " +
                sqlValue(baseEntityId) + ", " +
                sqlValue(shTarehe) + ", " +
                sqlValue(shAina) + ", " +
                sqlValue(shAinaOther) + ", " +
                sqlValue(gps) + ", " +
                sqlValue(shMada) + ", " +
                sqlValue(shMadaOther) + ", " +
                sqlValue(elimM) + ", " +
                sqlValue(elimF) + ", " +
                sqlValue(elimTotal) + ", " +
                sqlValue(maoni) + ", " +
                lastInteractedWith + ") " +
                " ON CONFLICT (id) DO UPDATE SET " +
                "base_entity_id = EXCLUDED.base_entity_id, " +
                "sh_tarehe = EXCLUDED.sh_tarehe, " +
                "sh_aina = EXCLUDED.sh_aina, " +
                "sh_aina_other = EXCLUDED.sh_aina_other, " +
                "gps = EXCLUDED.gps, " +
                "sh_mada = EXCLUDED.sh_mada, " +
                "sh_mada_other = EXCLUDED.sh_mada_other, " +
                "elim_m = EXCLUDED.elim_m, " +
                "elim_f = EXCLUDED.elim_f, " +
                "elim_total = EXCLUDED.elim_total, " +
                "maoni = EXCLUDED.maoni, " +
                "last_interacted_with = EXCLUDED.last_interacted_with;";

        updateDB(sql);
    }

    public static List<MotherMentorMobilizationModel> getMobilizationSessions() {
        String sql = "SELECT *, " +
                "CASE WHEN COALESCE(NULLIF(sh_tarehe, ''), NULLIF(mobilization_date, '')) LIKE '____-__-__' THEN COALESCE(NULLIF(sh_tarehe, ''), NULLIF(mobilization_date, '')) " +
                "ELSE substr(COALESCE(NULLIF(sh_tarehe, ''), NULLIF(mobilization_date, '')), 7, 4)||'-'||substr(COALESCE(NULLIF(sh_tarehe, ''), NULLIF(mobilization_date, '')), 4, 2)||'-'||substr(COALESCE(NULLIF(sh_tarehe, ''), NULLIF(mobilization_date, '')), 1, 2) END as orderDate " +
                "FROM " + Constants.TABLES.MOTHERMENTOR_MOBILIZATION + " " +
                "WHERE COALESCE(NULLIF(sh_tarehe, ''), NULLIF(mobilization_date, '')) IS NOT NULL " +
                "ORDER BY julianday(orderDate) DESC, last_interacted_with DESC";

        @SuppressLint("Range") DataMap<MotherMentorMobilizationModel> dataMap = cursor -> {
            MotherMentorMobilizationModel tbMotherMentorMobilizationModel = new MotherMentorMobilizationModel();
            tbMotherMentorMobilizationModel.setSessionId(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.BASE_ENTITY_ID)));
            tbMotherMentorMobilizationModel.setSessionDate(firstNonBlank(cursorString(cursor, DBConstants.KEY.SH_TAREHE), cursorString(cursor, DBConstants.KEY.MOBILIZATION_DATE)));
            tbMotherMentorMobilizationModel.setSessionParticipants(computeSessionParticipants(
                    firstNonBlank(cursorString(cursor, DBConstants.KEY.ELIM_F), cursorString(cursor, DBConstants.KEY.FEMALE_CLIENTS_REACHED)),
                    firstNonBlank(cursorString(cursor, DBConstants.KEY.ELIM_M), cursorString(cursor, DBConstants.KEY.MALE_CLIENTS_REACHED))));
            tbMotherMentorMobilizationModel.setActivityType(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_AINA)));
            tbMotherMentorMobilizationModel.setEducationTopics(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_MADA)));
            return tbMotherMentorMobilizationModel;
        };

        List<MotherMentorMobilizationModel> res = readData(sql, dataMap);
        if (res == null || res.isEmpty())
            return null;
        return res;
    }

    public static MotherMentorMobilizationModel getMobilizationSession(String sessionId) {
        String sql = "SELECT * FROM " + Constants.TABLES.MOTHERMENTOR_MOBILIZATION +
                " WHERE base_entity_id = '" + sessionId.replace("'", "''") + "' OR id = '" + sessionId.replace("'", "''") + "' LIMIT 1";

        DataMap<MotherMentorMobilizationModel> dataMap = cursor -> {
            MotherMentorMobilizationModel model = new MotherMentorMobilizationModel();
            model.setSessionId(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.BASE_ENTITY_ID)));
            model.setSessionDate(firstNonBlank(cursorString(cursor, DBConstants.KEY.SH_TAREHE), cursorString(cursor, DBConstants.KEY.MOBILIZATION_DATE)));
            model.setActivityType(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_AINA)));
            model.setActivityTypeOther(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_AINA_OTHER)));
            model.setGps(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.GPS)));
            model.setEducationTopics(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_MADA)));
            model.setEducationTopicsOther(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.SH_MADA_OTHER)));
            model.setMenEducated(firstNonBlank(cursorString(cursor, DBConstants.KEY.ELIM_M), cursorString(cursor, DBConstants.KEY.MALE_CLIENTS_REACHED)));
            model.setWomenEducated(firstNonBlank(cursorString(cursor, DBConstants.KEY.ELIM_F), cursorString(cursor, DBConstants.KEY.FEMALE_CLIENTS_REACHED)));
            model.setTotalEducated(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.ELIM_TOTAL)));
            model.setComments(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.MAONI)));
            model.setSessionParticipants(computeSessionParticipants(model.getWomenEducated(), model.getMenEducated()));
            return model;
        };

        List<MotherMentorMobilizationModel> res = readData(sql, dataMap);
        if (res == null || res.isEmpty()) {
            return null;
        }
        return res.get(0);
    }

    private static String computeSessionParticipants(String femaleParticipants, String maleParticipants) {
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
