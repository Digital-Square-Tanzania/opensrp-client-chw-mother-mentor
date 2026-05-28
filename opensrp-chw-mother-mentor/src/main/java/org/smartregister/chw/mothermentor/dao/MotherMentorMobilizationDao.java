package org.smartregister.chw.mothermentor.dao;


import android.annotation.SuppressLint;

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

    public static List<MotherMentorMobilizationModel> getMobilizationSessions() {
        String sql = "SELECT *,  substr(mobilization_date, 7, 4)||'-'|| " +
                "                substr(mobilization_date, 4,2)||'-'|| " +
                "                substr(mobilization_date, 1,2) as orderDate FROM " + Constants.TABLES.MOTHERMENTOR_MOBILIZATION + " ORDER BY julianday(orderDate)  DESC";

        @SuppressLint("Range") DataMap<MotherMentorMobilizationModel> dataMap = cursor -> {
            MotherMentorMobilizationModel tbMotherMentorMobilizationModel = new MotherMentorMobilizationModel();
            tbMotherMentorMobilizationModel.setSessionId(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.BASE_ENTITY_ID)));
            tbMotherMentorMobilizationModel.setSessionDate(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.MOBILIZATION_DATE)));
            tbMotherMentorMobilizationModel.setSessionParticipants(computeSessionParticipants(cursor.getString(cursor.getColumnIndex(DBConstants.KEY.FEMALE_CLIENTS_REACHED)), cursor.getString(cursor.getColumnIndex(DBConstants.KEY.MALE_CLIENTS_REACHED))));
            return tbMotherMentorMobilizationModel;
        };

        List<MotherMentorMobilizationModel> res = readData(sql, dataMap);
        if (res == null || res.isEmpty())
            return null;
        return res;
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


}
