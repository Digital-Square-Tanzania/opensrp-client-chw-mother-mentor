package org.smartregister.chw.mothermentor.repository;

import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Additive projection upgrade. A missing historical value stays NULL, never a fabricated zero. */
public final class MotherMentorReportingRepository {
    public static final List<String> REFERRAL_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            "referred_male", "referred_female", "referred_total", "facility_referral_services", "facility_referral_other"));
    private MotherMentorReportingRepository() { }

    public static synchronized void ensureMobilizationColumns(SQLiteDatabase db) {
        Set<String> columns = new HashSet<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(ec_mothermentor_mobilization)", null)) {
            while (cursor.moveToNext()) columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        }
        if (columns.isEmpty()) return;
        for (String column : REFERRAL_COLUMNS) {
            if (!columns.contains(column)) db.execSQL("ALTER TABLE ec_mothermentor_mobilization ADD COLUMN " + column + " VARCHAR");
        }
    }
}
