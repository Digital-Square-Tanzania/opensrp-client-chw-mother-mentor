package org.smartregister.chw.mothermentor.util;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calendar-based HEI schedule. A result awaiting confirmation remains actionable. */
public final class MotherMentorHeiSchedule {
    public enum Stage {
        SIX_WEEKS("six_weeks", DBConstants.KEY.HEI_TEST_WIKI6_RESULT,
                DBConstants.KEY.HEI_TEST_WIKI6_DATE),
        NINE_MONTHS("nine_months", DBConstants.KEY.HEI_TEST_MIEZI9_RESULT,
                DBConstants.KEY.HEI_TEST_MIEZI9_DATE),
        AFTER_WEANING("three_months_after_weaning", DBConstants.KEY.HEI_TEST_MIEZI15_RESULT,
                DBConstants.KEY.HEI_TEST_MIEZI15_DATE),
        EIGHTEEN_MONTHS("eighteen_months", DBConstants.KEY.HEI_TEST_MIEZI18_RESULT,
                DBConstants.KEY.HEI_TEST_MIEZI18_DATE);

        private final String code;
        private final String resultKey;
        private final String dateKey;

        Stage(String code, String resultKey, String dateKey) {
            this.code = code;
            this.resultKey = resultKey;
            this.dateKey = dateKey;
        }

        public String getCode() { return code; }
        public String getResultKey() { return resultKey; }
        public String getDateKey() { return dateKey; }

        public static Stage fromCode(String code) {
            for (Stage stage : values()) if (stage.code.equals(code)) return stage;
            return null;
        }
    }

    public static final class TestRecord {
        private final String result;
        private final LocalDate date;
        private final boolean confirmed;
        private final LocalDate serviceDate;

        public TestRecord(String result, LocalDate date, boolean confirmed, LocalDate serviceDate) {
            this.result = result;
            this.date = date;
            this.confirmed = confirmed;
            this.serviceDate = serviceDate;
        }

        public String getResult() { return result; }
        public LocalDate getDate() { return date; }
        public boolean isConfirmed() { return confirmed; }
        public LocalDate getServiceDate() { return serviceDate; }
        public boolean requiresFollowUp() {
            return "pending".equals(result) || ("positive".equals(result) && !confirmed);
        }
    }

    private MotherMentorHeiSchedule() {}

    public static List<Stage> due(LocalDate dob, LocalDate cessation,
            Map<Stage, TestRecord> tests, LocalDate assessment) {
        if (dob == null || assessment == null || dob.isAfter(assessment)) {
            return Collections.emptyList();
        }
        Map<Stage, TestRecord> history = tests == null ? Collections.emptyMap() : tests;
        List<Stage> sampling = eligibleForSampling(dob, cessation, history, assessment);
        List<Stage> due = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            TestRecord result = history.get(stage);
            // Result review is different from taking another sample for the same stage.
            if (sampling.contains(stage) || result != null && result.requiresFollowUp()) due.add(stage);
        }
        return Collections.unmodifiableList(due);
    }

    public static List<Stage> eligibleForSampling(LocalDate dob, LocalDate cessation,
            Map<Stage, TestRecord> tests, LocalDate assessment) {
        if (dob == null || assessment == null || dob.isAfter(assessment)) {
            return Collections.emptyList();
        }
        Map<Stage, TestRecord> history = tests == null ? Collections.emptyMap() : tests;
        List<Stage> eligible = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            if (history.containsKey(stage) || !inAgeWindow(stage, dob, assessment)) continue;
            LocalDate date = dueDate(stage, dob, cessation);
            if (date != null && !date.isAfter(assessment)) eligible.add(stage);
        }
        return Collections.unmodifiableList(eligible);
    }

    public static Stage next(LocalDate dob, LocalDate cessation,
            Map<Stage, TestRecord> tests, LocalDate assessment) {
        if (dob == null || assessment == null || dob.isAfter(assessment)) return null;
        Map<Stage, TestRecord> history = tests == null ? Collections.emptyMap() : tests;
        Stage next = null;
        LocalDate earliest = null;
        for (Stage stage : Stage.values()) {
            if (history.containsKey(stage)) continue;
            LocalDate date = dueDate(stage, dob, cessation);
            if (date == null || !date.isAfter(assessment) || !inAgeWindow(stage, dob, date)) continue;
            if (earliest == null || date.isBefore(earliest)) {
                next = stage;
                earliest = date;
            }
        }
        return next;
    }

    private static boolean inAgeWindow(Stage stage, LocalDate dob, LocalDate assessment) {
        if (stage == Stage.SIX_WEEKS) return assessment.isBefore(dob.plusMonths(9));
        return stage != Stage.NINE_MONTHS && stage != Stage.AFTER_WEANING
                || assessment.isBefore(dob.plusMonths(18));
    }

    public static LocalDate dueDate(Stage stage, LocalDate dob, LocalDate cessation) {
        if (stage == null || dob == null) return null;
        switch (stage) {
            case SIX_WEEKS: return dob.plusDays(42);
            case NINE_MONTHS: return dob.plusMonths(9);
            case AFTER_WEANING:
                return cessation == null || cessation.isBefore(dob)
                        ? null : cessation.plusMonths(3);
            case EIGHTEEN_MONTHS: return dob.plusMonths(18);
            default: return null;
        }
    }

    /** Exact supported form/ISO dates; invalid dates are never normalized into eligibility. */
    public static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        for (String pattern : new String[] {"yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy"}) {
            try {
                LocalDate parsed = LocalDate.parse(value, DateTimeFormat.forPattern(pattern));
                if (parsed.toString(pattern).equals(value)) return parsed;
            } catch (IllegalArgumentException ignored) {
                // Try the next supported stored form format.
            }
        }
        return null;
    }

    public static boolean validDate(LocalDate date, LocalDate dob, LocalDate assessment) {
        return date != null && dob != null && assessment != null
                && !date.isBefore(dob) && !date.isAfter(assessment);
    }

    public static Map<Stage, TestRecord> immutableTests(Map<Stage, TestRecord> tests) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(tests));
    }
}
