package org.smartregister.chw.mothermentor.util;

/** Closure and the consecutive not-found/Other counter for one mother enrollment. */
public final class MotherMentorMotherClosure {
    public static final String ACTIVE = "active";
    public static final String DECEASED = "deceased";
    public static final String MOVED_AREA = "moved_area";
    public static final String MOVED_FACILITY = "moved_facility";
    public static final String NOT_FOUND_OTHER_3 = "not_found_other_3";
    public static final String PREGNANCY_LOSS = "pregnancy_loss";
    public static final String STILLBIRTH = "stillbirth";
    public static final String PREGNANCY_ENDED_OTHER = "pregnancy_ended_other";
    public static final String CHILD_DECEASED = "child_deceased";
    public static final String CHILD_CONFIRMED_POSITIVE = "child_confirmed_positive";
    public static final String GRADUATED = "graduated";

    /** Validated visit outcomes; raw form values and clinical checks belong to the caller. */
    public enum VisitOutcome {
        CONTINUE(ACTIVE),
        NOT_FOUND_OTHER(ACTIVE),
        DECEASED(MotherMentorMotherClosure.DECEASED),
        MOVED_AREA(MotherMentorMotherClosure.MOVED_AREA),
        MOVED_FACILITY(MotherMentorMotherClosure.MOVED_FACILITY),
        PREGNANCY_LOSS(MotherMentorMotherClosure.PREGNANCY_LOSS),
        STILLBIRTH(MotherMentorMotherClosure.STILLBIRTH),
        PREGNANCY_ENDED_OTHER(MotherMentorMotherClosure.PREGNANCY_ENDED_OTHER),
        CHILD_DECEASED(MotherMentorMotherClosure.CHILD_DECEASED),
        CHILD_CONFIRMED_POSITIVE(MotherMentorMotherClosure.CHILD_CONFIRMED_POSITIVE),
        GRADUATED(MotherMentorMotherClosure.GRADUATED);

        private final String status;

        VisitOutcome(String status) {
            this.status = status;
        }
    }

    private final String status;
    private final String closedDate;
    private final int otherCount;
    private final String otherReason;
    private final String newFacility;

    private MotherMentorMotherClosure(String status, String closedDate, int otherCount,
            String otherReason, String newFacility) {
        this.status = status;
        this.closedDate = closedDate;
        this.otherCount = otherCount;
        this.otherReason = otherReason == null ? "" : otherReason;
        this.newFacility = newFacility == null ? "" : newFacility;
    }

    public static MotherMentorMotherClosure active() {
        return new MotherMentorMotherClosure(ACTIVE, "", 0, "", "");
    }

    /** Apply valid finalized evidence, ordered and deduplicated within the current enrollment. */
    public MotherMentorMotherClosure advance(VisitOutcome outcome, String serviceDateIso) {
        return advance(outcome, serviceDateIso, "", "");
    }

    public MotherMentorMotherClosure advance(VisitOutcome outcome, String serviceDateIso,
            String otherReason, String newFacility) {
        if (isClosed() || outcome == null) return this;
        int nextCount = outcome == VisitOutcome.NOT_FOUND_OTHER ? otherCount + 1 : 0;
        String nextStatus = outcome == VisitOutcome.NOT_FOUND_OTHER && nextCount >= 3
                ? NOT_FOUND_OTHER_3 : outcome.status;
        return new MotherMentorMotherClosure(nextStatus,
                ACTIVE.equals(nextStatus) ? "" : serviceDateIso, nextCount,
                outcome == VisitOutcome.NOT_FOUND_OTHER || outcome == VisitOutcome.PREGNANCY_ENDED_OTHER
                        ? otherReason : "",
                outcome == VisitOutcome.MOVED_AREA || outcome == VisitOutcome.MOVED_FACILITY
                        ? newFacility : "");
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

    public int getOtherCount() {
        return otherCount;
    }

    public String getOtherReason() {
        return otherReason;
    }

    public String getNewFacility() {
        return newFacility;
    }
}
