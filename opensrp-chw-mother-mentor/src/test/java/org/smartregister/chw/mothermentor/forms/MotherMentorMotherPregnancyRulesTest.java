package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mvel2.MVEL;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherPregnancyRulesTest {
    @Test
    public void switchingDeliveredAndPretermDoesNotReuseThePreviousNativeDate() throws Exception {
        Map<String, Object> facts = foundPregnant();
        facts.put("step1_" + VISIT_PREGNANCY_STATUS, "not_pregnant");
        facts.put("step1_" + MAMA_PREGNANCY_OUTCOME, "delivered");
        facts.put("step1_" + MAMA_DELIVERY_DATE_INPUT, "19-10-2026");
        assertEquals("19-10-2026", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
        assertTrue(relevant(MAMA_DELIVERY_DATE_INPUT, facts));
        facts.put("step1_" + MAMA_PREGNANCY_OUTCOME, "preterm");
        assertFalse(relevant(MAMA_DELIVERY_DATE_INPUT, facts));
        assertTrue(relevant(MAMA_PRETERM_DELIVERY_DATE_INPUT, facts));
        // NativeForm clears the old alias as soon as its relevance becomes false.
        facts.put("step1_" + MAMA_DELIVERY_DATE_INPUT, "");
        assertEquals("", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
        facts.put("step1_" + MAMA_PRETERM_DELIVERY_DATE_INPUT, "18-10-2026");
        assertEquals("18-10-2026", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
        facts.put("step1_" + MAMA_PREGNANCY_OUTCOME, "delivered");
        assertFalse(relevant(MAMA_PRETERM_DELIVERY_DATE_INPUT, facts));
        assertEquals("", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
    }

    @Test
    public void readonlyEnrollmentDateRestoresAfterNotFoundWithoutBecomingEditable() throws Exception {
        Map<String, Object> facts = foundPregnant();
        for (String original : new String[] {"breast_feeding", "not_pregnant"}) {
            facts.put("step1_" + MAMA_ENROLLMENT_PREGNANCY_STATUS, original);
            facts.put("step1_" + MAMA_ENROLLMENT_CHILD_DOB, "01-08-2026");
            assertFalse(relevant(VISIT_PREGNANCY_STATUS, facts));
            assertTrue(relevant(MAMA_ENROLLMENT_DELIVERY_DATE, facts));
            assertEquals("01-08-2026", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
            facts.put("step1_" + FOLLOW_UP_STATUS, "not_found");
            assertFalse(relevant(MAMA_ENROLLMENT_DELIVERY_DATE, facts));
            assertEquals("", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
            facts.put("step1_" + FOLLOW_UP_STATUS, "found");
            assertEquals("01-08-2026", calculation(MAMA_ENROLLMENT_DELIVERY_DATE, facts));
            assertEquals("01-08-2026", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
        }
    }

    @Test
    public void newAttendanceHidesEveryPregnancyQuestionAndClearsCanonicalDates() throws Exception {
        Map<String, Object> facts = foundPregnant();
        facts.put("step1_" + ATTENDANCE_TYPE, "new");
        facts.put("step1_" + VISIT_PREGNANCY_STATUS, "not_pregnant");
        facts.put("step1_" + MAMA_PREGNANCY_OUTCOME, "delivered");
        facts.put("step1_" + MAMA_DELIVERY_DATE_INPUT, "01-11-2026");
        for (String key : new String[] {VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME,
                MAMA_DELIVERY_DATE_INPUT, MAMA_PRETERM_DELIVERY_DATE_INPUT,
                MAMA_STILLBIRTH_DATE_INPUT, MAMA_PREGNANCY_LOSS_DATE,
                MAMA_PREGNANCY_OUTCOME_OTHER, MAMA_ENROLLMENT_DELIVERY_DATE})
            assertFalse(key, relevant(key, facts));
        assertEquals("", calculation(MAMA_TAREHE_KUJIFUNGUA, facts));
        assertEquals("", calculation(MAMA_ENROLLMENT_DELIVERY_DATE, facts));
    }

    private Map<String, Object> foundPregnant() {
        Map<String, Object> facts = new HashMap<>();
        for (String key : new String[] {ATTENDANCE_TYPE, FOLLOW_UP_STATUS, MAMA_ENROLLMENT_PREGNANCY_STATUS,
                VISIT_PREGNANCY_STATUS, MAMA_PREGNANCY_OUTCOME, MAMA_DELIVERY_DATE_INPUT,
                MAMA_PRETERM_DELIVERY_DATE_INPUT, MAMA_STILLBIRTH_DATE_INPUT, MAMA_ENROLLMENT_CHILD_DOB}) {
            facts.put("step1_" + key, "");
        }
        facts.put("step1_" + ATTENDANCE_TYPE, "repeat");
        facts.put("step1_" + FOLLOW_UP_STATUS, "found");
        facts.put("step1_" + MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant");
        return facts;
    }

    private boolean relevant(String key, Map<String, Object> facts) throws Exception {
        return (Boolean) evaluate("relevance", key, "isRelevant", facts);
    }

    private Object calculation(String key, Map<String, Object> facts) throws Exception {
        return evaluate("calculation", key, "calculation", facts);
    }

    @SuppressWarnings("unchecked")
    private Object evaluate(String type, String key, String result, Map<String, Object> facts) throws Exception {
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open(
                "rule/mothermentor_mother_pregnancy_" + type + ".yml")) {
            for (Object raw : new Yaml().loadAll(input)) {
                Map<String, Object> rule = (Map<String, Object>) raw;
                if (!("step1_" + key).equals(rule.get("name"))) continue;
                for (String action : (List<String>) rule.get("actions")) MVEL.eval(action, facts);
                return facts.get(result);
            }
        }
        throw new AssertionError("Missing rule: " + key);
    }
}
