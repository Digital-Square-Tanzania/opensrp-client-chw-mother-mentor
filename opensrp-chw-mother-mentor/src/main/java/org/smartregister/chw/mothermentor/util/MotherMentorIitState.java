package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;

/** Read state for one IIT enrollment episode. Status codes match the workflow's exit criteria. */
public final class MotherMentorIitState {
    public static final String ACTIVE = "active";
    public static final String NONE = "none";
    public static final String REFERRED = "referred";
    public static final String RETURNED_TO_CARE = "I1";
    public static final String DECEASED = "I2";
    public static final String MOVED_AREA = "I3";
    public static final String MOVED_FACILITY = "I4";
    public static final String REFUSED_THREE_TIMES = "I5";
    public static final String NOT_RETURNED_THREE_TIMES = "I6";
    public static final String TRAVELLED_THREE_TIMES = "I7";
    public static final String OTHER_THREE_TIMES = "I8";
    public static final String LOST_TO_FOLLOW_UP = "I9";
    public static final String LEGACY_CLOSED = "closed";

    private final String enrollmentId;
    private final String missedAppointmentDate;
    private final String linkedMotherId;
    private final String status;
    private final LocalDate closedDate;
    private final String newFacility;
    private final String returnState;
    private final int visitCount;
    private final int refusedCount;
    private final int notReturnedCount;
    private final int travelledCount;
    private final int otherCount;
    private final int notFoundCount;

    private MotherMentorIitState(String enrollmentId, String missedAppointmentDate,
            String linkedMotherId, String status, LocalDate closedDate, String newFacility,
            String returnState, int visitCount, int refusedCount, int notReturnedCount,
            int travelledCount, int otherCount, int notFoundCount) {
        this.enrollmentId = enrollmentId;
        this.missedAppointmentDate = missedAppointmentDate;
        this.linkedMotherId = linkedMotherId;
        this.status = status;
        this.closedDate = closedDate;
        this.newFacility = newFacility;
        this.returnState = returnState;
        this.visitCount = visitCount;
        this.refusedCount = refusedCount;
        this.notReturnedCount = notReturnedCount;
        this.travelledCount = travelledCount;
        this.otherCount = otherCount;
        this.notFoundCount = notFoundCount;
    }

    public static MotherMentorIitState active() {
        return active("", "", "", 0);
    }

    public static MotherMentorIitState active(String enrollmentId, String missedAppointmentDate,
            String linkedMotherId, int visitCount) {
        return new MotherMentorIitState(enrollmentId, missedAppointmentDate, linkedMotherId, ACTIVE,
                null, "", NONE, visitCount, 0, 0, 0, 0, 0);
    }

    public MotherMentorIitState close(String outcome, LocalDate date, String facility) {
        if (isClosed()) return this;
        return new MotherMentorIitState(enrollmentId, missedAppointmentDate, linkedMotherId,
                outcome, date, facility, returnState, visitCount, refusedCount, notReturnedCount,
                travelledCount, otherCount, notFoundCount);
    }

    public String getEnrollmentId() { return enrollmentId; }
    public String getMissedAppointmentDate() { return missedAppointmentDate; }
    public String getLinkedMotherId() { return linkedMotherId; }
    public boolean isMamaKinaraLinked() { return !linkedMotherId.isEmpty(); }
    public String getStatus() { return status; }
    public boolean isClosed() { return !ACTIVE.equals(status); }
    public String getClosedDate() { return closedDate == null ? "" : closedDate.toString(); }
    public String getNewFacility() { return newFacility; }
    public String getReturnState() { return returnState; }
    public int getVisitCount() { return visitCount; }
    public int getRefusedCount() { return refusedCount; }
    public int getNotReturnedCount() { return notReturnedCount; }
    public int getTravelledCount() { return travelledCount; }
    public int getOtherCount() { return otherCount; }
    public int getNotFoundCount() { return notFoundCount; }

    public boolean requiresConfirmation() {
        return DECEASED.equals(status) || MOVED_AREA.equals(status) || MOVED_FACILITY.equals(status);
    }
}
