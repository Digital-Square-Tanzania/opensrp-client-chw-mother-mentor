package org.smartregister.chw.mothermentor.util;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.content.Context;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.R;

import java.util.LinkedHashMap;
import java.util.Map;

/** Child numbers are stable within a Mama enrollment. Legacy observations belong to child one. */
public final class MotherMentorDeliveryChildren {
    public static final String COUNT = "mama_delivery_child_count";
    public static final String SECTIONS = "mama_child_sections";
    public static final String SEX = "mama_child_sex";
    public static final String LINK = "mama_child_link";
    public static final String NUMBER = "mama_child_number";
    public static final String VISITED = "mama_child_sections_visited";
    public static final String ALL_FINISHED = "mama_children_finished";
    public static final String FOLLOWED_COUNT = "mama_followed_child_count";
    public static final String PENDING_TESTS = "mama_child_pending_tests";
    public static final int MAX_COUNT = 4;

    private MotherMentorDeliveryChildren() {}

    public static int count(Map<String, String> values) {
        String count = values.get(COUNT);
        return count != null && count.matches("[1-4]") ? Integer.parseInt(count) : 0;
    }

    public static boolean recordsDelivery(Map<String, String> values) {
        return MotherMentorMotherPregnancyUtils.isFound(values)
                && (MotherMentorMotherPregnancyUtils.isLiveBirth(values)
                        || MotherMentorMotherPregnancyUtils.isEnrollmentDeliveryAssessment(values))
                && !"true".equals(values.get(MAMA_CARRIED_DELIVERY));
    }

    public static boolean hasSections(Map<String, String> values) {
        return "true".equals(values.get(SECTIONS));
    }

    public static boolean validSex(String value) {
        return "male".equals(value) || "female".equals(value);
    }

    public static String label(Context context, int number, String sex) {
        String child = context.getString(R.string.mothermentor_delivery_child, number);
        String caption = MotherMentorChildEidLabels.sex(context, sex);
        return caption.isEmpty() ? child : child + " (" + caption + ")";
    }

    public static Map<String, String> section(Map<String, String> visit, int number) {
        Map<String, String> answers = new LinkedHashMap<>();
        String prefix = observationKey(number, "");
        for (Map.Entry<String, String> entry : visit.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                answers.put(key.substring(prefix.length()), entry.getValue());
            } else if (!hasSections(visit) && number == 1 && MotherMentorMotherChildUtils.isChildField(key)) {
                answers.put(key, entry.getValue());
            }
        }
        return answers;
    }

    public static Map<String, String> childVisit(Map<String, String> visit, int number) {
        Map<String, String> answers = new LinkedHashMap<>(visit);
        answers.keySet().removeIf(MotherMentorMotherChildUtils::isChildField);
        answers.putAll(section(visit, number));
        return answers;
    }

    public static Map<String, String> actionValues(JSONObject form) {
        Map<String, String> values = MotherMentorMotherVisitUtils.formValues(form);
        int number = form.optInt(NUMBER);
        if (number < 1 || number > MAX_COUNT) return values;
        Map<String, String> section = new LinkedHashMap<>();
        for (Map.Entry<String, String> value : values.entrySet()) {
            String key = value.getKey();
            JSONObject field = MotherMentorMotherChildUtils.field(form, key);
            if (LINK.equals(key) || MotherMentorMotherChildUtils.isChildField(key)
                    && field != null && "concept".equals(field.optString("openmrs_entity"))) {
                section.put(observationKey(number, key), StringUtils.defaultString(value.getValue()));
            }
        }
        return section;
    }

    static boolean sectionsComplete(Map<String, String> values, LocalDate day) {
        if (!hasSections(values) || !MotherMentorMotherPregnancyUtils.isChildPath(values, day)) return true;
        String visited = values.getOrDefault(VISITED, "");
        if (!visited.matches("[1-4](,[1-4])*")) return false;
        String[] sections = visited.split(",");
        if (recordsDelivery(values) && sections.length != count(values)) return false;
        int previous = 0;
        for (String section : sections) {
            int number = Integer.parseInt(section);
            if (number <= previous || count(values) > 0 && number > count(values)) return false;
            Map<String, String> child = childVisit(values, number);
            if (!validSex(child.get(SEX)) || !MotherMentorMotherChildUtils.isComplete(child, day)) return false;
            previous = number;
        }
        return true;
    }

    public static String observationKey(int number, String field) {
        return "mama_child_" + number + "_" + field;
    }
}
