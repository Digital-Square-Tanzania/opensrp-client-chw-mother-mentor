package org.smartregister.chw.mothermentor.dao;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;

public final class MotherMentorIitFollowUpDao {
    private MotherMentorIitFollowUpDao() {}

    public static String registerCondition(boolean closed) {
        String table = Constants.TABLES.MOTHERMENTOR_ENROLL_IIT;
        return closed ? table + "." + DBConstants.KEY.IS_CLOSED + " = 1"
                : MotherMentorDao.getActiveClientCondition(table);
    }

    /** Prefer the current Mama Kinara CTC number and fall back to the client's HIV-care records. */
    public static String ctcNumber(SQLiteDatabase db, String clientId) {
        for (String table : new String[] {Constants.TABLES.MOTHERMENTOR_ENROLLMENT,
                "ec_hiv_register", "ec_cbhs_register"}) {
            if (!MotherMentorChildEidDao.columns(db, table).contains(DBConstants.KEY.CTC_NUMBER)) {
                continue;
            }
            try (Cursor rows = db.rawQuery("SELECT " + DBConstants.KEY.CTC_NUMBER + " FROM "
                    + table + " WHERE " + DBConstants.KEY.BASE_ENTITY_ID + " = ?",
                    new String[] {clientId})) {
                while (rows.moveToNext()) {
                    String ctc = StringUtils.trimToEmpty(rows.getString(0));
                    if (!ctc.isEmpty() && !"null".equalsIgnoreCase(ctc)) return ctc;
                }
            }
        }
        return "";
    }
}
