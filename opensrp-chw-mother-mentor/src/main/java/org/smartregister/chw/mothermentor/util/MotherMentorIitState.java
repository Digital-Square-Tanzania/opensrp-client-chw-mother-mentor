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

    public enum Outcome {
        AGREED, RETURNED, REFUSED, NOT_RETURNED, TRAVELLED, OTHER,
        DECEASED, MOVED_AREA, MOVED_FACILITY
    }

    private final String enrollmentId;
    private final String missedAppointmentDate;
    private final String linkedMotherId;
    private final String status;
    private final LocalDate closedDate;
    private final long closureTime;
    private final String cascadeSourceEpisode;
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
            long closureTime, String cascadeSourceEpisode, String returnState, int visitCount,
            int refusedCount, int notReturnedCount,
            int travelledCount, int otherCount, int notFoundCount) {
        this.enrollmentId = enrollmentId;
        this.missedAppointmentDate = missedAppointmentDate;
        this.linkedMotherId = linkedMotherId;
        this.status = status;
        this.closedDate = closedDate;
        this.closureTime = closureTime;
        this.cascadeSourceEpisode = cascadeSourceEpisode;
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
                null, "", 0, "", NONE, visitCount, 0, 0, 0, 0, 0);
    }

    public MotherMentorIitState close(String outcome, LocalDate date, String facility) {
        return close(outcome, date, facility, 0);
    }

    public MotherMentorIitState close(String outcome, LocalDate date, String facility, long time) {
        if (isClosed()) return this;
        return new MotherMentorIitState(enrollmentId, missedAppointmentDate, linkedMotherId,
                outcome, date, facility, time, "", returnState, visitCount, refusedCount, notReturnedCount,
                travelledCount, otherCount, notFoundCount);
    }

    public MotherMentorIitState advance(Outcome outcome, LocalDate date, String facility, long time) {
        if (isClosed() || outcome == null) return this;
        int refused = outcome == Outcome.REFUSED ? refusedCount + 1 : 0;
        int notReturned = outcome == Outcome.NOT_RETURNED ? notReturnedCount + 1 : 0;
        int travelled = outcome == Outcome.TRAVELLED ? travelledCount + 1 : 0;
        int other = outcome == Outcome.OTHER ? otherCount + 1 : 0;
        boolean immediateNotFound = outcome == Outcome.DECEASED || outcome == Outcome.MOVED_AREA
                || outcome == Outcome.MOVED_FACILITY;
        int notFound = outcome == Outcome.TRAVELLED || outcome == Outcome.OTHER ? notFoundCount + 1
                : immediateNotFound ? notFoundCount : 0;
        String next = exit(outcome, refused, notReturned, travelled, other, notFound);
        boolean closed = !ACTIVE.equals(next);
        return new MotherMentorIitState(enrollmentId, missedAppointmentDate, linkedMotherId,
                next, closed ? date : null, facility, closed ? time : 0, "",
                outcome == Outcome.AGREED ? REFERRED : returnState, visitCount, refused, notReturned,
                travelled, other, notFound);
    }

    public MotherMentorIitState withCascadeSourceEpisode(String episode) {
        return new MotherMentorIitState(enrollmentId, missedAppointmentDate, linkedMotherId, status,
                closedDate, newFacility, closureTime, episode, returnState, visitCount, refusedCount,
                notReturnedCount, travelledCount, otherCount, notFoundCount);
    }

    private static String exit(Outcome outcome, int refused, int notReturned, int travelled,
            int other, int notFound) {
        switch (outcome) {
            case RETURNED: return RETURNED_TO_CARE;
            case DECEASED: return DECEASED;
            case MOVED_AREA: return MOVED_AREA;
            case MOVED_FACILITY: return MOVED_FACILITY;
            case REFUSED: return refused >= 3 ? REFUSED_THREE_TIMES : ACTIVE;
            case NOT_RETURNED: return notReturned >= 3 ? NOT_RETURNED_THREE_TIMES : ACTIVE;
            case TRAVELLED:
                if (travelled >= 3) return TRAVELLED_THREE_TIMES;
                break;
            case OTHER:
                if (other >= 3) return OTHER_THREE_TIMES;
                break;
            default: break;
        }
        return notFound >= 3 ? LOST_TO_FOLLOW_UP : ACTIVE;
    }

    public String getEnrollmentId() { return enrollmentId; }
    public String getMissedAppointmentDate() { return missedAppointmentDate; }
    public String getLinkedMotherId() { return linkedMotherId; }
    public boolean isMamaKinaraLinked() { return !linkedMotherId.isEmpty(); }
    public String getStatus() { return status; }
    public boolean isClosed() { return !ACTIVE.equals(status); }
    public String getClosedDate() { return closedDate == null ? "" : closedDate.toString(); }
    public long getClosureTime() { return closureTime; }
    public String getCascadeSourceEpisode() { return cascadeSourceEpisode; }
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
