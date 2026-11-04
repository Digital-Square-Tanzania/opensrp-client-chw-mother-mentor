package org.smartregister.chw.mothermentor.util;

/** Closure and consecutive not-found counters for one household enrolment. */
public final class MotherMentorHouseholdClosure {
    public static final String ACTIVE = "active";
    public static final String MOVED = "moved";
    public static final String HOUSE_CLOSED_3 = "house_closed_3";
    public static final String OTHER_3 = "other_3";
    public static final String LOST_TO_FOLLOW_UP = "lost_to_follow_up";
    public static final String MOTHER_DECEASED = "mother_deceased";
    public static final String CHILD_DECEASED = "child_deceased";

    private final String status;
    private final String closedDate;
    private final int houseClosedCount;
    private final int otherCount;
    private final int notFoundCount;

    private MotherMentorHouseholdClosure(String status, String closedDate,
            int houseClosedCount, int otherCount, int notFoundCount) {
        this.status = status;
        this.closedDate = closedDate;
        this.houseClosedCount = houseClosedCount;
        this.otherCount = otherCount;
        this.notFoundCount = notFoundCount;
    }

    public static MotherMentorHouseholdClosure active() {
        return new MotherMentorHouseholdClosure(ACTIVE, "", 0, 0, 0);
    }

    /** Member exit projections use the same closed state without fabricating household visits. */
    public MotherMentorHouseholdClosure withMemberClosure(String reason, String date) {
        if (!MOTHER_DECEASED.equals(reason) && !CHILD_DECEASED.equals(reason)) {
            throw new IllegalArgumentException("Unsupported member closure");
        }
        return new MotherMentorHouseholdClosure(reason, date,
                houseClosedCount, otherCount, notFoundCount);
    }

    /** Apply valid finalized evidence; the caller orders and deduplicates this enrolment's visits. */
    public MotherMentorHouseholdClosure advance(String visitType, String visitStatus,
            String reason, String serviceDateIso) {
        if (isClosed()) return this;
        if ("new".equals(visitType)
                || ("repeat".equals(visitType) && "found".equals(visitStatus))) return active();
        if (!"repeat".equals(visitType) || !"not_found".equals(visitStatus)
                || !(MOVED.equals(reason) || "house_closed".equals(reason)
                        || "other".equals(reason))) return this;

        int houseClosed = "house_closed".equals(reason) ? houseClosedCount + 1 : 0;
        int other = "other".equals(reason) ? otherCount + 1 : 0;
        int missing = notFoundCount + 1;
        String nextStatus = ACTIVE;
        if (MOVED.equals(reason)) {
            nextStatus = MOVED;
        } else if (houseClosed >= 3) {
            nextStatus = HOUSE_CLOSED_3;
        } else if (other >= 3) {
            nextStatus = OTHER_3;
        } else if (missing >= 3) {
            nextStatus = LOST_TO_FOLLOW_UP;
        }
        return new MotherMentorHouseholdClosure(nextStatus,
                ACTIVE.equals(nextStatus) ? "" : serviceDateIso, houseClosed, other, missing);
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

    public int getHouseClosedCount() {
        return houseClosedCount;
    }

    public int getOtherCount() {
        return otherCount;
    }

    public int getNotFoundCount() {
        return notFoundCount;
    }
}
