package org.smartregister.chw.mothermentor.repository;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Additive projection upgrade. Missing historical answers remain null. */
public final class MotherMentorReportingRepository {
    public static final List<String> REFERRAL_COLUMNS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            DBConstants.KEY.REFERRED_MALE,
                            DBConstants.KEY.REFERRED_FEMALE,
                            DBConstants.KEY.REFERRED_TOTAL,
                            DBConstants.KEY.FACILITY_REFERRAL_SERVICES,
                            DBConstants.KEY.FACILITY_REFERRAL_OTHER));

    public static final List<String> MOBILIZATION_COLUMNS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            DBConstants.KEY.SH_TAREHE,
                            DBConstants.KEY.SH_AINA,
                            DBConstants.KEY.SH_AINA_OTHER,
                            DBConstants.KEY.SH_MADA,
                            DBConstants.KEY.SH_MADA_OTHER,
                            DBConstants.KEY.ELIM_M,
                            DBConstants.KEY.ELIM_F,
                            DBConstants.KEY.ELIM_TOTAL,
                            DBConstants.KEY.MAONI,
                            DBConstants.KEY.REFERRED_MALE,
                            DBConstants.KEY.REFERRED_FEMALE,
                            DBConstants.KEY.REFERRED_TOTAL,
                            DBConstants.KEY.FACILITY_REFERRAL_SERVICES,
                            DBConstants.KEY.FACILITY_REFERRAL_OTHER));

    private MotherMentorReportingRepository() {}

    public static synchronized void ensureMobilizationColumns(SQLiteDatabase database) {
        String table = Constants.TABLES.MOTHERMENTOR_MOBILIZATION;
        Set<String> columns = new HashSet<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        // Other product flavors may not install the Mother Mentor module.
        if (columns.isEmpty()) {
            return;
        }
        for (String column : MOBILIZATION_COLUMNS) {
            if (!columns.contains(column)) {
                database.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " VARCHAR");
            }
        }
    }
}
