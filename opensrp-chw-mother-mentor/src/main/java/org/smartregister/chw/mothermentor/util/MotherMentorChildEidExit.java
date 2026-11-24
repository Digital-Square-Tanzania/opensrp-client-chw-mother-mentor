package org.smartregister.chw.mothermentor.util;

import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;

/** Canonical Child EID outcomes. Codes are stable across translations and sync. */
public enum MotherMentorChildEidExit {
    E1("tested_negative", false, true, null),
    E2("confirmatory_negative", false, true, null),
    E3("graduated", true, false, VisitOutcome.GRADUATED),
    E4("confirmed_positive", true, false, VisitOutcome.CHILD_CONFIRMED_POSITIVE),
    E5("child_deceased", true, false, VisitOutcome.CHILD_DECEASED),
    E6("moved_area", true, false, VisitOutcome.MOVED_AREA),
    E7("moved_facility", true, false, VisitOutcome.MOVED_FACILITY),
    E8("refused_3", false, false, null),
    E9("nonattendance_3", false, false, null),
    E10("not_found_other_3", false, false, null);

    public static final String CHILD_VISIT = "child_visit";
    public static final String MOTHER_VISIT = "mother_visit";

    private final String status;
    private final boolean confirm;
    private final boolean success;
    private final VisitOutcome motherOutcome;

    MotherMentorChildEidExit(String status, boolean confirm, boolean success, VisitOutcome motherOutcome) {
        this.status = status;
        this.confirm = confirm;
        this.success = success;
        this.motherOutcome = motherOutcome;
    }

    public String getStatus() { return status; }
    public boolean needsConfirmation() { return confirm; }
    public boolean isSuccess() { return success; }
    public VisitOutcome getMotherOutcome() { return motherOutcome; }
    public boolean isStrikeExit() { return this == E8 || this == E9 || this == E10; }

    public static MotherMentorChildEidExit fromStatus(String status) {
        for (MotherMentorChildEidExit exit : values()) if (exit.status.equals(status)) return exit;
        return null;
    }

    public static MotherMentorChildEidExit fromMotherStatus(String status) {
        if (MotherMentorMotherClosure.CHILD_DECEASED.equals(status)) return E5;
        if (MotherMentorMotherClosure.CHILD_CONFIRMED_POSITIVE.equals(status)) return E4;
        if (MotherMentorMotherClosure.GRADUATED.equals(status)) return E3;
        if (MotherMentorMotherClosure.MOVED_AREA.equals(status)) return E6;
        if (MotherMentorMotherClosure.MOVED_FACILITY.equals(status)) return E7;
        return null;
    }

    public static MotherMentorChildEidExit fromReturn(MotherMentorChildEidReturnVisit.Outcome result) {
        switch (result) {
            case TESTED_NEGATIVE: return E1;
            case CONFIRMATORY_NEGATIVE: return E2;
            case GRADUATED: return E3;
            case CONFIRMED_POSITIVE: return E4;
            default: return null;
        }
    }
}
