package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FOLLOW_UP_STATUS;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_FACILITY_AGREEMENT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_FOLLOW_UP_PHASE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_HEPATITIS_B_RESULT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_HEPATITIS_B_TESTED;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_HIV_RESULT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_HIV_TESTED;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_NOT_FOUND_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_SYPHILIS_RESULT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.PARTNER_SYPHILIS_TESTED;

import java.util.Map;

/** Closure and consecutive-visit counters for one partner linkage. */
public final class MotherMentorPartnerClosure {
    public static final String ACTIVE = "active";
    public static final String ATTENDED_TESTED = "attended_tested";
    public static final String DECEASED = "deceased";
    public static final String MOVED = "moved";
    public static final String REFUSED_3 = "refused_3";
    public static final String NOT_ATTENDED_3 = "not_attended_3";
    public static final String TRAVELLED_3 = "travelled_3";
    public static final String OTHER_3 = "other_3";
    public static final String LOST_TO_FOLLOW_UP = "lost_to_follow_up";
    public static final String LEGACY_COMPLETED = "completed";

    private final String status;
    private final String closedDate;
    private final int refusedCount;
    private final int nonAttendanceCount;
    private final int travelledCount;
    private final int otherCount;
    private final int notFoundCount;

    private MotherMentorPartnerClosure(String status, String closedDate, int refusedCount,
            int nonAttendanceCount, int travelledCount, int otherCount, int notFoundCount) {
        this.status = status;
        this.closedDate = closedDate;
        this.refusedCount = refusedCount;
        this.nonAttendanceCount = nonAttendanceCount;
        this.travelledCount = travelledCount;
        this.otherCount = otherCount;
        this.notFoundCount = notFoundCount;
    }

    public static MotherMentorPartnerClosure active() {
        return new MotherMentorPartnerClosure(ACTIVE, "", 0, 0, 0, 0, 0);
    }

    /** Preserve a linkage closed by the earlier attended, but not tested, workflow. */
    public static MotherMentorPartnerClosure legacyCompleted(String date) {
        return new MotherMentorPartnerClosure(LEGACY_COMPLETED, date, 0, 0, 0, 0, 0);
    }

    /** Apply a finalized, valid visit; the caller orders and deduplicates the linkage's visits. */
    public MotherMentorPartnerClosure advance(Map<String, String> values, String serviceDateIso) {
        if (isClosed()) return this;
        boolean tracing = "tracing".equals(values.get(PARTNER_FOLLOW_UP_PHASE));
        boolean attendance = "attendance".equals(values.get(PARTNER_FOLLOW_UP_PHASE));
        boolean found = tracing && "found".equals(values.get(FOLLOW_UP_STATUS));
        boolean notFound = tracing && "not_found".equals(values.get(FOLLOW_UP_STATUS));
        String agreement = found ? values.get(PARTNER_FACILITY_AGREEMENT) : "";
        String reason = notFound ? values.get(PARTNER_NOT_FOUND_REASON) : "";
        String attended = attendance ? values.get(PARTNER_ARRIVED_AT_FACILITY) : "";
        if (!(found && matches(agreement, "agreed", "refused"))
                && !(notFound && matches(reason, "deceased", "moved", "travelled", "other"))
                && !(attendance && matches(attended, "yes", "no"))) return this;

        int refused = "refused".equals(agreement) ? refusedCount + 1 : 0;
        int nonAttendance = "no".equals(attended) ? nonAttendanceCount + 1 : 0;
        int travelled = "travelled".equals(reason) ? travelledCount + 1 : 0;
        int other = "other".equals(reason) ? otherCount + 1 : 0;
        int missing = notFound ? notFoundCount + 1 : 0;
        String nextStatus = ACTIVE;
        if ("yes".equals(attended) && hasTestResult(values)) {
            nextStatus = ATTENDED_TESTED;
        } else if ("deceased".equals(reason)) {
            nextStatus = DECEASED;
        } else if ("moved".equals(reason)) {
            nextStatus = MOVED;
        } else if (refused >= 3) {
            nextStatus = REFUSED_3;
        } else if (nonAttendance >= 3) {
            nextStatus = NOT_ATTENDED_3;
        } else if (travelled >= 3) {
            nextStatus = TRAVELLED_3;
        } else if (other >= 3) {
            nextStatus = OTHER_3;
        } else if (missing >= 3) {
            nextStatus = LOST_TO_FOLLOW_UP;
        }
        return new MotherMentorPartnerClosure(nextStatus,
                ACTIVE.equals(nextStatus) ? "" : serviceDateIso,
                refused, nonAttendance, travelled, other, missing);
    }

    private static boolean hasTestResult(Map<String, String> values) {
        return tested(values, PARTNER_HIV_TESTED, PARTNER_HIV_RESULT)
                || tested(values, PARTNER_SYPHILIS_TESTED, PARTNER_SYPHILIS_RESULT)
                || tested(values, PARTNER_HEPATITIS_B_TESTED, PARTNER_HEPATITIS_B_RESULT);
    }

    private static boolean tested(Map<String, String> values, String tested, String result) {
        return "yes".equals(values.get(tested))
                && matches(values.get(result), "positive", "negative");
    }

    private static boolean matches(String value, String... choices) {
        for (String choice : choices) if (choice.equals(value)) return true;
        return false;
    }

    public boolean isClosed() {
        return !ACTIVE.equals(status);
    }

    public String getStatus() {
        return status;
    }

    public String getClosedDate() {
        return closedDate;
    }

    public String getClosureReason() {
        return isClosed() ? status : "";
    }

    public int getRefusedCount() {
        return refusedCount;
    }

    public int getNonAttendanceCount() {
        return nonAttendanceCount;
    }

    public int getTravelledCount() {
        return travelledCount;
    }

    public int getOtherCount() {
        return otherCount;
    }

    public int getNotFoundCount() {
        return notFoundCount;
    }
}
