package org.smartregister.chw.mothermentor.dao;

import android.database.Cursor;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorEligibility;

import timber.log.Timber;

/** Household enrollment eligibility uses the current status shown in the mother register. */
public final class MotherMentorHouseholdEligibilityDao {
    private MotherMentorHouseholdEligibilityDao() {}

    public static boolean hasEligibleMother(String householdId) {
        if (StringUtils.isBlank(householdId)) return false;
        String enrollment = Constants.TABLES.MOTHERMENTOR_ENROLLMENT;
        String sql = "SELECT " + MotherMentorClientRegisterDao.currentStatus() + " AS "
                + DBConstants.KEY.MOTHERMENTOR_CURRENT_STATUS + ", " + enrollment + "."
                + DBConstants.KEY.CHILD_DOB + " FROM " + enrollment
                + " JOIN ec_family_member m ON m." + DBConstants.KEY.BASE_ENTITY_ID
                + " = " + enrollment + "." + DBConstants.KEY.BASE_ENTITY_ID
                + " JOIN ec_family f ON f." + DBConstants.KEY.BASE_ENTITY_ID
                + " = m." + DBConstants.KEY.RELATIONAL_ID
                + " WHERE m." + DBConstants.KEY.RELATIONAL_ID + " = ? AND "
                + MotherMentorDao.getActiveClientCondition(enrollment)
                + " AND m." + DBConstants.KEY.IS_CLOSED + " = 0 AND f."
                + DBConstants.KEY.IS_CLOSED + " = 0 AND lower(trim(m."
                + DBConstants.KEY.GENDER + ")) = 'female' AND coalesce(" + enrollment + "."
                + DBConstants.KEY.STATUS + ", 'client') = 'client'";
        try (Cursor cursor = MotherMentorLibrary.getInstance().getRepository()
                .getReadableDatabase().rawQuery(sql, new String[] {householdId})) {
            while (cursor.moveToNext()) {
                if (MotherMentorEligibility.isEligibleMother(
                        cursor.getString(0), cursor.getString(1), LocalDate.now())) return true;
            }
        } catch (Exception e) {
            Timber.w(e, "Unable to determine Mother Mentor household eligibility");
        }
        return false;
    }

    public static boolean isEnrolled(String householdId) {
        if (StringUtils.isBlank(householdId)) return false;
        String sql = "SELECT 1 FROM " + Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT
                + " WHERE " + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND "
                + DBConstants.KEY.IS_CLOSED + " = 0 AND " + DBConstants.KEY.CONSENT + " = ? LIMIT 1";
        try (Cursor cursor = MotherMentorLibrary.getInstance().getRepository()
                .getReadableDatabase().rawQuery(sql, new String[] {householdId, "ndiyo"})) {
            return cursor.moveToFirst();
        } catch (Exception e) {
            Timber.w(e, "Unable to read Mother Mentor household enrollment");
            return false;
        }
    }
}
