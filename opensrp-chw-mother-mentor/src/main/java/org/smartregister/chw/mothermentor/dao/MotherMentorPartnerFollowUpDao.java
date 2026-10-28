package org.smartregister.chw.mothermentor.dao;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.chw.mothermentor.util.MotherMentorPartnerFollowUpVisitUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorVisitCompletion;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import timber.log.Timber;

/** Partner linkage state and effective closure are shared by register, routing and visit UI. */
public final class MotherMentorPartnerFollowUpDao {
    private MotherMentorPartnerFollowUpDao() {}

    public static boolean isPermanentlyClosed(String partnerId, SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM (SELECT ? AS " + DBConstants.KEY.BASE_ENTITY_ID + ") partner WHERE "
                        + recordedDeathCondition("partner." + DBConstants.KEY.BASE_ENTITY_ID)
                        + " OR " + deceasedPartnerCondition("partner." + DBConstants.KEY.BASE_ENTITY_ID),
                new String[] {partnerId})) {
            return cursor.moveToFirst();
        }
    }

    public static Context getContext(String partnerId, String excludedVisitId, Date serviceDate) {
        if (StringUtils.isBlank(partnerId)) return Context.empty();
        Date date = serviceDate == null ? new Date() : serviceDate;
        try {
            if (MotherMentorDao.hasRecordedDeath(partnerId)) return Context.empty();
            Map<String, String> enrollment = enrollment(partnerId);
            if (enrollment.isEmpty()) return Context.empty();
            String motherId = value(enrollment, DBConstants.KEY.MOTHER_BASE_ENTITY_ID);
            Map<String, String> maternal = mother(motherId);
            List<Visit> history = finalizedVisits(partnerId, excludedVisitId);
            return context(partnerId, enrollment, maternal, history, date);
        } catch (Exception e) {
            Timber.e(e);
            return Context.empty();
        }
    }

    /** A completed routine linkage can be reopened later; a recorded death cannot. */
    public static boolean canLinkPartner(String partnerId, Date now) {
        if (StringUtils.isBlank(partnerId)) return false;
        try {
            if (MotherMentorDao.hasRecordedDeath(partnerId)) return false;
            List<Visit> history = finalizedVisits(partnerId, null);
            if (hasDeath(history)) return false;
            Map<String, String> enrollment = enrollment(partnerId);
            if (enrollment.isEmpty()) return true;
            Context state = context(partnerId, enrollment, Collections.emptyMap(), history,
                    now == null ? new Date() : now);
            // Earlier unlinked partner records can acquire their first explicit mother linkage.
            return !state.isActive()
                    || StringUtils.isBlank(enrollment.get(DBConstants.KEY.MOTHER_BASE_ENTITY_ID));
        } catch (Exception e) {
            Timber.e(e);
            return false;
        }
    }

    /** The clock is evaluated by SQLite for each read, including after midnight while offline. */
    public static String activeEnrollmentCondition(String alias) {
        return activeEnrollmentCondition(alias, "date('now', 'localtime')");
    }

    /** Uses the caller's local day for deterministic eligibility and boundary checks. */
    public static String activeEnrollmentCondition(String alias, Date now) {
        return activeEnrollmentCondition(alias,
                "date('" + new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(now == null ? new Date() : now) + "')");
    }

    private static String activeEnrollmentCondition(String alias, String today) {
        if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid enrollment table alias");
        }
        String f = "partner_end";
        String v = "partner_end_visit";
        String schema = f + "." + DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA;
        String death = deathCondition(f);
        String linked = "coalesce(nullif(" + f + "." + DBConstants.KEY.PARTNER_LINKAGE_ID
                + ", ''), 'legacy:' || " + f + "." + DBConstants.KEY.ENTITY_ID + ") = "
                + "coalesce(nullif(" + alias + "." + DBConstants.KEY.PARTNER_LINKAGE_ID
                + ", ''), 'legacy:' || " + alias + "." + DBConstants.KEY.BASE_ENTITY_ID + ")";
        String serviceDate = normalizedDate(f + "." + DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
        String complete = schema + " = '2' AND " + linked + " AND "
                + f + "." + DBConstants.KEY.PARTNER_FOLLOW_UP_END + " = 'completed' AND "
                + attendanceCompleteCondition(f) + " AND "
                + serviceDate + " < " + today;
        return "(" + MotherMentorDao.getActiveClientCondition(alias) + " AND NOT EXISTS (SELECT 1 FROM "
                + Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT + " " + f + " JOIN "
                + VisitRepository.VISIT_TABLE + " " + v + " ON "
                + v + "." + DBConstants.KEY.FORM_SUBMISSION_ID + " = "
                + f + "." + DBConstants.KEY.BASE_ENTITY_ID + " AND "
                + v + "." + DBConstants.KEY.BASE_ENTITY_ID + " = "
                + f + "." + DBConstants.KEY.ENTITY_ID + " WHERE "
                + f + "." + DBConstants.KEY.ENTITY_ID + " = "
                + alias + "." + DBConstants.KEY.BASE_ENTITY_ID + " AND "
                + f + "." + DBConstants.KEY.FOLLOW_UP_CATEGORY + " = 'PARTNER' AND "
                + v + "." + DBConstants.KEY.PROCESSED + " = 1 AND "
                + v + "." + DBConstants.KEY.VISIT_TYPE + " IN ('"
                + MotherMentorFollowUpType.PARTNER.getEventType() + "', '"
                + Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES + "') AND (" + death
                + " OR (" + complete + "))))";
    }

    private static String recordedDeathCondition(String partnerIdExpression) {
        return "EXISTS (SELECT 1 FROM ec_family_member partner_deceased_member WHERE "
                + "partner_deceased_member." + DBConstants.KEY.BASE_ENTITY_ID + " = "
                + partnerIdExpression + " AND lower(trim(ifnull(partner_deceased_member."
                + DBConstants.KEY.DOD + ", ''))) NOT IN ('', 'null'))";
    }

    /** Only finalized outcomes explicitly recorded for the partner category block re-linking. */
    public static String deceasedPartnerCondition(String partnerIdExpression) {
        if (partnerIdExpression == null
                || !partnerIdExpression.matches("(?:[A-Za-z_][A-Za-z0-9_]*\\.)?[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid partner ID column");
        }
        String f = "partner_death";
        String v = "partner_death_visit";
        return "EXISTS (SELECT 1 FROM " + Constants.TABLES.MOTHERMENTOR_FOLLOWUP_VISIT
                + " " + f + " JOIN " + VisitRepository.VISIT_TABLE + " " + v + " ON "
                + v + "." + DBConstants.KEY.FORM_SUBMISSION_ID + " = "
                + f + "." + DBConstants.KEY.BASE_ENTITY_ID + " AND "
                + v + "." + DBConstants.KEY.BASE_ENTITY_ID + " = "
                + f + "." + DBConstants.KEY.ENTITY_ID + " WHERE "
                + f + "." + DBConstants.KEY.ENTITY_ID + " = " + partnerIdExpression + " AND "
                + f + "." + DBConstants.KEY.FOLLOW_UP_CATEGORY + " = 'PARTNER' AND "
                + v + "." + DBConstants.KEY.PROCESSED + " = 1 AND "
                + v + "." + DBConstants.KEY.VISIT_TYPE + " IN ('"
                + MotherMentorFollowUpType.PARTNER.getEventType() + "', '"
                + Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES + "') AND "
                + deathCondition(f) + ")";
    }

    private static String deathCondition(String f) {
        String schema = f + "." + DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA;
        return "((" + schema + " = '2' AND "
                + f + "." + DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE + " = 'tracing' AND "
                + f + "." + DBConstants.KEY.FOLLOW_UP_STATUS + " = 'not_found' AND "
                + f + "." + DBConstants.KEY.PARTNER_NOT_FOUND_REASON + " = 'deceased' AND "
                + f + "." + DBConstants.KEY.PARTNER_FOLLOW_UP_END + " = 'deceased') OR ("
                + "coalesce(" + schema + ", '') <> '2' AND "
                + f + "." + DBConstants.KEY.FOLLOW_UP_STATUS + " = 'deceased'))";
    }

    private static String attendanceCompleteCondition(String alias) {
        return alias + "." + DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE + " = 'attendance' AND "
                + alias + "." + DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY + " = 'yes' AND "
                + normalizedDate(alias + "." + DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE)
                + " IS NOT NULL AND " + educationCondition(alias) + " AND "
                + testCondition(alias, DBConstants.KEY.PARTNER_HIV_TESTED,
                        DBConstants.KEY.PARTNER_HIV_RESULT,
                        DBConstants.KEY.PARTNER_HIV_NOT_TESTED_REASON,
                        DBConstants.KEY.PARTNER_HIV_NOT_TESTED_REASON_OTHER, true) + " AND "
                + testCondition(alias, DBConstants.KEY.PARTNER_SYPHILIS_TESTED,
                        DBConstants.KEY.PARTNER_SYPHILIS_RESULT,
                        DBConstants.KEY.PARTNER_SYPHILIS_NOT_TESTED_REASON,
                        DBConstants.KEY.PARTNER_SYPHILIS_NOT_TESTED_REASON_OTHER, false) + " AND "
                + testCondition(alias, DBConstants.KEY.PARTNER_HEPATITIS_B_TESTED,
                        DBConstants.KEY.PARTNER_HEPATITIS_B_RESULT,
                        DBConstants.KEY.PARTNER_HEPATITIS_B_NOT_TESTED_REASON,
                        DBConstants.KEY.PARTNER_HEPATITIS_B_NOT_TESTED_REASON_OTHER, false);
    }

    private static String educationCondition(String alias) {
        String topics = "replace(replace(replace(replace(coalesce(" + alias + "."
                + DBConstants.KEY.PARTNER_EDUCATION_TOPICS
                + ", ''), '[', ''), ']', ''), '\"', ''), ' ', '')";
        String unknown = "replace(replace(replace(',' || " + topics
                + " || ',', ',hiv,', ','), ',syphilis,', ','), ',hepatitis_b,', ',')";
        return "(" + alias + "." + DBConstants.KEY.PARTNER_EDUCATION_PROVIDED + " = 'no' OR ("
                + alias + "." + DBConstants.KEY.PARTNER_EDUCATION_PROVIDED + " = 'yes' AND "
                + topics + " <> '' AND " + unknown + " = ','))";
    }

    private static String testCondition(String alias, String testedKey, String resultKey,
            String reasonKey, String otherKey, boolean hiv) {
        String tested = alias + "." + testedKey;
        String result = alias + "." + resultKey;
        String reason = alias + "." + reasonKey;
        String other = alias + "." + otherKey;
        return "((" + tested + " = 'yes' AND " + result + " IN ('positive', 'negative')) OR ("
                + tested + " = 'no' AND " + reason + " IN ('stock_out', 'refused', 'other'"
                + (hiv ? ", 'already_infected'" : "") + ") AND (" + reason
                + " <> 'other' OR trim(coalesce(" + other + ", '')) <> '')))";
    }

    private static String normalizedDate(String column) {
        String iso = "CASE WHEN " + column
                + " GLOB '[0-9][0-9]-[0-9][0-9]-[0-9][0-9][0-9][0-9]' THEN substr("
                + column + ", 7, 4) || '-' || substr(" + column
                + ", 4, 2) || '-' || substr(" + column + ", 1, 2) WHEN " + column
                + " GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' THEN "
                + column + " END";
        return "CASE WHEN date(" + iso + ", '+0 days') = (" + iso + ") THEN (" + iso + ") END";
    }

    private static Map<String, String> enrollment(String partnerId) {
        String sql = "SELECT * FROM " + Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER
                + " WHERE " + DBConstants.KEY.BASE_ENTITY_ID + " = ? ORDER BY CAST("
                + DBConstants.KEY.LAST_INTERACTED_WITH + " AS INTEGER) DESC LIMIT 1";
        return first(sql, partnerId);
    }

    private static Map<String, String> mother(String motherId) {
        if (StringUtils.isBlank(motherId)) return Collections.emptyMap();
        return first("SELECT " + DBConstants.KEY.PREGNANCY_STATUS + ", "
                + DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID + " FROM "
                + Constants.TABLES.MOTHERMENTOR_ENROLLMENT + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.IS_CLOSED
                + " = 0 ORDER BY CAST(" + DBConstants.KEY.LAST_INTERACTED_WITH
                + " AS INTEGER) DESC LIMIT 1", motherId);
    }

    private static Map<String, String> first(String sql, String id) {
        try (Cursor cursor = MotherMentorLibrary.getInstance().getRepository()
                .getReadableDatabase().rawQuery(sql, new String[] {id})) {
            if (!cursor.moveToFirst()) return Collections.emptyMap();
            Map<String, String> row = new HashMap<>();
            for (String column : cursor.getColumnNames()) {
                row.put(column, cursor.getString(cursor.getColumnIndexOrThrow(column)));
            }
            return row;
        }
    }

    private static List<Visit> finalizedVisits(String partnerId, String excludedVisitId) {
        String sql = "SELECT * FROM " + VisitRepository.VISIT_TABLE + " WHERE "
                + DBConstants.KEY.BASE_ENTITY_ID + " = ? AND " + DBConstants.KEY.PROCESSED
                + " = 1 AND " + DBConstants.KEY.VISIT_TYPE + " IN (?, ?) AND "
                + DBConstants.KEY.VISIT_ID + " <> ?";
        List<Visit> visits = new ArrayList<>();
        try (Cursor cursor = MotherMentorLibrary.getInstance().getRepository()
                .getReadableDatabase().rawQuery(sql, new String[] {partnerId,
                    MotherMentorFollowUpType.PARTNER.getEventType(),
                    Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,
                    StringUtils.defaultString(excludedVisitId)})) {
            while (cursor.moveToNext()) {
                Visit visit = new Visit();
                visit.setVisitId(cursor.getString(cursor.getColumnIndexOrThrow(DBConstants.KEY.VISIT_ID)));
                visit.setVisitType(cursor.getString(cursor.getColumnIndexOrThrow(DBConstants.KEY.VISIT_TYPE)));
                visit.setJson(cursor.getString(cursor.getColumnIndexOrThrow(DBConstants.KEY.VISIT_JSON)));
                visit.setProcessed(true);
                String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(DBConstants.KEY.VISIT_DATE));
                try {
                    visit.setDate(new Date(Long.parseLong(timestamp)));
                } catch (Exception ignored) {
                    // A recorded service-date observation remains authoritative.
                }
                visits.add(visit);
            }
        }
        return visits;
    }

    private static Context context(String partnerId, Map<String, String> enrollment,
            Map<String, String> maternal, List<Visit> history, Date day) {
        String linkageId = value(enrollment, DBConstants.KEY.PARTNER_LINKAGE_ID);
        if (linkageId.isEmpty()) linkageId = "legacy:" + partnerId;
        boolean active = "0".equals(enrollment.get(DBConstants.KEY.IS_CLOSED));
        Map<String, String> latest = Collections.emptyMap();
        Date latestDate = null;
        String latestId = "";
        for (Visit visit : history) {
            Map<String, String> values = observations(visit);
            if (!partnerVisit(visit, values)) continue;
            boolean partnerSchema = MotherMentorFollowUpType.PARTNER.getEventType()
                    .equals(visit.getVisitType());
            if (!MotherMentorPartnerFollowUpVisitUtils.isComplete(values, partnerSchema)) continue;
            if (deceased(values)) active = false;
            String visitLinkage = value(values, DBConstants.KEY.PARTNER_LINKAGE_ID);
            if (visitLinkage.isEmpty()) visitLinkage = "legacy:" + partnerId;
            if (!linkageId.equals(visitLinkage)) continue;
            Date date = date(visit, values);
            if (date == null || !beforeDay(date, day)) continue;
            if ("2".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))
                    && "completed".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_END))
                    && "attendance".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE))
                    && "yes".equals(values.get(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY))) {
                active = false;
            }
            String id = StringUtils.defaultString(visit.getVisitId());
            if (latestDate == null || date.after(latestDate)
                    || (date.equals(latestDate) && id.compareTo(latestId) > 0)) {
                latest = values;
                latestDate = date;
                latestId = id;
            }
        }
        boolean pending = "2".equals(latest.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))
                && (("tracing".equals(latest.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE))
                        && "found".equals(latest.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                        && "agreed".equals(latest.get(DBConstants.KEY.PARTNER_FACILITY_AGREEMENT)))
                    || ("attendance".equals(latest.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE))
                        && "no".equals(latest.get(DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY))));
        String pregnant = value(maternal, DBConstants.KEY.PREGNANCY_STATUS);
        pregnant = "pregnant".equals(pregnant) ? "yes"
                : ("not_pregnant".equals(pregnant) || "breast_feeding".equals(pregnant)
                    ? "no" : "unknown");
        return new Context(linkageId, value(enrollment, DBConstants.KEY.MOTHER_BASE_ENTITY_ID),
                value(maternal, DBConstants.KEY.MOTHER_PREGNANCY_EPISODE_ID), pregnant,
                active && pending, active);
    }

    private static boolean hasDeath(List<Visit> history) {
        for (Visit visit : history) {
            Map<String, String> values = observations(visit);
            if (partnerVisit(visit, values) && deceased(values)
                    && MotherMentorPartnerFollowUpVisitUtils.isComplete(values,
                        MotherMentorFollowUpType.PARTNER.getEventType().equals(visit.getVisitType()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean deceased(Map<String, String> values) {
        if ("2".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))) {
            return "deceased".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_END))
                    && "tracing".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE))
                    && "not_found".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS))
                    && "deceased".equals(values.get(DBConstants.KEY.PARTNER_NOT_FOUND_REASON));
        }
        return "deceased".equals(values.get(DBConstants.KEY.FOLLOW_UP_STATUS));
    }

    private static boolean partnerVisit(Visit visit, Map<String, String> values) {
        String category = values.get(DBConstants.KEY.FOLLOW_UP_CATEGORY);
        return "PARTNER".equals(category)
                || (StringUtils.isBlank(category)
                    && MotherMentorFollowUpType.PARTNER.getEventType().equals(visit.getVisitType())
                    && !"2".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))
                    && !deceased(values));
    }

    private static Map<String, String> observations(Visit visit) {
        try {
            return MotherMentorVisitCompletion.observationValues(
                    new JSONObject(visit.getJson()).optJSONArray("obs"));
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static Date date(Visit visit, Map<String, String> values) {
        if ("2".equals(values.get(DBConstants.KEY.PARTNER_FOLLOW_UP_SCHEMA))) {
            String recorded = value(values, DBConstants.KEY.MOTHERMENTOR_VISIT_DATE);
            if (!(recorded.matches("[0-9]{2}-[0-9]{2}-[0-9]{4}")
                    || recorded.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return null;
        }
        try {
            return MotherMentorPartnerFollowUpVisitUtils.getVisitDate(
                    visit, new JSONObject(visit.getJson()), values);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean beforeDay(Date first, Date second) {
        Calendar a = Calendar.getInstance();
        a.setTime(first);
        a.set(Calendar.HOUR_OF_DAY, 0);
        a.set(Calendar.MINUTE, 0);
        a.set(Calendar.SECOND, 0);
        a.set(Calendar.MILLISECOND, 0);
        Calendar b = Calendar.getInstance();
        b.setTime(second);
        b.set(Calendar.HOUR_OF_DAY, 0);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 0);
        b.set(Calendar.MILLISECOND, 0);
        return a.before(b);
    }

    private static String value(Map<String, String> values, String key) {
        return StringUtils.trimToEmpty(values.get(key));
    }

    public static class Context {
        private final String linkageId;
        private final String motherId;
        private final String pregnancyEpisodeId;
        private final String motherPregnant;
        private final boolean attendancePending;
        private final boolean active;

        public Context(String linkageId, String motherId, String pregnancyEpisodeId,
                String motherPregnant, boolean attendancePending, boolean active) {
            this.linkageId = linkageId;
            this.motherId = motherId;
            this.pregnancyEpisodeId = pregnancyEpisodeId;
            this.motherPregnant = motherPregnant;
            this.attendancePending = attendancePending;
            this.active = active;
        }

        private static Context empty() {
            return new Context("", "", "", "unknown", false, false);
        }

        public String getLinkageId() { return linkageId; }
        public String getMotherId() { return motherId; }
        public String getPregnancyEpisodeId() { return pregnancyEpisodeId; }
        public String getMotherPregnant() { return motherPregnant; }
        public boolean isAttendancePending() { return attendancePending; }
        public boolean isActive() { return active; }
    }
}
