package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherClosureRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherClosure.VisitOutcome;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherExitCompletionTest {
    private static final String DAY = "2026-11-04";

    @Test
    public void allTenExitPathsCompleteWithoutHiddenServiceSections() throws Exception {
        List<Map<String, String>> exits = new ArrayList<>();
        exits.add(notFound("deceased"));
        exits.add(notFound("moved_area"));
        Map<String, String> transfer = notFound("moved_facility");
        transfer.put(MAMA_KITUO_KIPYA, "Hospital A"); exits.add(transfer);
        Map<String, String> thirdOther = notFound("other");
        thirdOther.put(MAMA_SABABU_NYINGINE_TEXT, "Client is away");
        thirdOther.put(MAMA_COUNTER_NYINGINE, "3"); exits.add(thirdOther);
        Map<String, String> loss = ended("pregnancy_loss");
        loss.put(MAMA_PREGNANCY_LOSS_DATE, "2026-11-03"); exits.add(loss);
        Map<String, String> stillbirth = ended("stillbirth");
        stillbirth.put(MAMA_TAREHE_KUJIFUNGUA, "2026-11-03"); exits.add(stillbirth);
        Map<String, String> other = ended("other");
        other.put(MAMA_PREGNANCY_OUTCOME_OTHER, "Another recorded outcome"); exits.add(other);
        Map<String, String> childDeath = aliveChild("2026-05-01");
        childDeath.put(MAMA_CHILD_STATUS, "deceased");
        childDeath.put(MAMA_TAREHE_MTOTO_ALIFARIKI, "2026-11-03"); exits.add(childDeath);
        Map<String, String> positive = testedChild("2026-05-01", "six_weeks", "positive");
        positive.put(HEI_CHANYA_IMETHIBITISHWA, "yes"); exits.add(positive);
        exits.add(testedChild("2025-05-01", "eighteen_months", "negative"));
        VisitOutcome[] outcomes = {VisitOutcome.DECEASED, VisitOutcome.MOVED_AREA,
                VisitOutcome.MOVED_FACILITY, VisitOutcome.NOT_FOUND_OTHER, VisitOutcome.PREGNANCY_LOSS,
                VisitOutcome.STILLBIRTH, VisitOutcome.PREGNANCY_ENDED_OTHER, VisitOutcome.CHILD_DECEASED,
                VisitOutcome.CHILD_CONFIRMED_POSITIVE, VisitOutcome.GRADUATED};
        for (int index = 0; index < exits.size(); index++) {
            Map<String, String> values = exits.get(index);
            assertEquals("A" + (index + 1), outcomes[index], MotherMentorMotherClosureRepository.outcome(values));
            assertTrue("A" + (index + 1) + " assessment", MotherMentorVisitCompletion.isVisitTypeComplete(values));
            assertTrue("A" + (index + 1) + " manual completion", complete(values));
        }
    }

    @Test
    public void newAttendanceStillRequiresArtAndAllServicesAndIgnoresStaleClinicalExitAnswers() throws Exception {
        Map<String, String> values = ended("pregnancy_loss");
        values.put(ATTENDANCE_TYPE, "new");
        values.put(MAMA_PREGNANCY_LOSS_DATE, "2099-01-01");
        assertEquals(VisitOutcome.CONTINUE, MotherMentorMotherClosureRepository.outcome(values));
        assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
        assertFalse(complete(values));
        services(values);
        assertFalse("New attendance still needs the New-only ART answer", complete(values));
        values.put(NEW_PMTCT_ART_INITIATION, "no");
        assertTrue(complete(values));
    }

    @Test
    public void foundPregnantAndLivingChildVisitsContinueToRequireAllServiceSections() throws Exception {
        List<Map<String, String>> continuing = new ArrayList<>();
        Map<String, String> pregnant = base(); pregnant.put(VISIT_PREGNANCY_STATUS, "pregnant");
        continuing.add(pregnant);
        Map<String, String> living = aliveChild("2026-05-01"); living.put(MAMA_HEI_TEST_DONE, "no");
        continuing.add(living);
        Map<String, String> pending = testedChild("2026-05-01", "six_weeks", "pending");
        continuing.add(pending);
        Map<String, String> unconfirmed = testedChild("2026-05-01", "six_weeks", "positive");
        unconfirmed.put(HEI_CHANYA_IMETHIBITISHWA, "no"); continuing.add(unconfirmed);
        continuing.add(testedChild("2026-05-01", "six_weeks", "negative"));
        for (Map<String, String> values : continuing) {
            assertEquals(VisitOutcome.CONTINUE, MotherMentorMotherClosureRepository.outcome(values));
            assertTrue(MotherMentorVisitCompletion.isVisitTypeComplete(values));
            assertFalse("Continuing visits need their service actions", complete(values));
            services(values); assertTrue(complete(values));
            values.remove(NEXT_APPOINTMENT_DATE);
            assertFalse("A partially completed checklist cannot be submitted", complete(values));
        }
    }

    @Test
    public void invalidEntryOrClinicalAnswersCannotBeCompletedEvenWithEveryServiceAnswered() throws Exception {
        List<Map<String, String>> invalid = new ArrayList<>();
        invalid.add(notFound("unknown"));
        invalid.add(notFound("moved_facility"));
        invalid.add(notFound("other"));
        invalid.add(ended("pregnancy_loss"));
        invalid.add(ended("stillbirth"));
        invalid.add(ended("other"));
        invalid.add(ended("delivered"));
        Map<String, String> future = ended("delivered");
        future.put(MAMA_TAREHE_KUJIFUNGUA, LocalDate.now().plusDays(1).toString()); invalid.add(future);
        Map<String, String> absentChild = aliveChild("2026-05-01");
        absentChild.remove(MAMA_CHILD_STATUS); invalid.add(absentChild);
        Map<String, String> childDeath = aliveChild("2026-05-01");
        childDeath.put(MAMA_CHILD_STATUS, "deceased"); invalid.add(childDeath);
        Map<String, String> weaning = aliveChild("2026-05-01");
        weaning.put(MAMA_BREASTFEEDING_STATUS, "not_breastfeeding"); invalid.add(weaning);
        invalid.add(testedChild("2026-05-01", "six_weeks", "positive"));
        Map<String, String> wrongStage = testedChild("2026-05-01", "eighteen_months", "negative");
        invalid.add(wrongStage);
        Map<String, String> impossibleServiceDate = notFound("deceased");
        impossibleServiceDate.put(MOTHERMENTOR_VISIT_DATE, "31-02-2026"); invalid.add(impossibleServiceDate);
        Map<String, String> futureServiceDate = notFound("deceased");
        futureServiceDate.put(MOTHERMENTOR_VISIT_DATE, LocalDate.now().plusDays(1).toString()); invalid.add(futureServiceDate);
        for (Map<String, String> values : invalid) {
            services(values);
            assertNull(values.toString(), MotherMentorMotherClosureRepository.outcome(values));
            assertFalse(values.toString(), MotherMentorVisitCompletion.isVisitTypeComplete(values));
            assertFalse(values.toString(), complete(values));
        }
    }

    @Test
    public void legacyCompletionKeepsItsOriginalContinuingAndNonContinuingRules() throws Exception {
        Map<String, String> legacy = new HashMap<>();
        legacy.put(ATTENDANCE_TYPE, "repeat"); legacy.put(FOLLOW_UP_STATUS, "deceased");
        assertTrue(complete(legacy));
        legacy.put(FOLLOW_UP_STATUS, "continuing_services");
        assertFalse(complete(legacy));
        services(legacy); assertTrue(complete(legacy));
    }

    private Map<String, String> base() {
        Map<String, String> values = new HashMap<>();
        values.put(MAMA_VISIT_SCHEMA, "2"); values.put(ATTENDANCE_TYPE, "repeat");
        values.put(FOLLOW_UP_STATUS, "found"); values.put(MAMA_HEI_ASSESSMENT_DATE, DAY);
        values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "pregnant");
        return values;
    }

    private Map<String, String> notFound(String reason) {
        Map<String, String> values = base(); values.put(FOLLOW_UP_STATUS, "not_found");
        values.put(MAMA_NOT_FOUND_REASON, reason); return values;
    }

    private Map<String, String> ended(String outcome) {
        Map<String, String> values = base(); values.put(VISIT_PREGNANCY_STATUS, "not_pregnant");
        values.put(MAMA_PREGNANCY_OUTCOME, outcome); return values;
    }

    private Map<String, String> aliveChild(String dob) {
        Map<String, String> values = base(); values.put(MAMA_ENROLLMENT_PREGNANCY_STATUS, "breast_feeding");
        values.put(MAMA_ENROLLMENT_CHILD_DOB, dob); values.put(MAMA_TAREHE_KUJIFUNGUA, dob);
        values.put(MAMA_CHILD_STATUS, "alive"); values.put(MAMA_BREASTFEEDING_STATUS, "breastfeeding");
        return values;
    }

    private Map<String, String> testedChild(String dob, String type, String result) {
        Map<String, String> values = aliveChild(dob); values.put(MAMA_HEI_TEST_DONE, "yes");
        values.put(MAMA_HEI_TEST_TYPE, type); values.put(MAMA_HEI_TEST_DATE, "2026-11-03");
        values.put(MAMA_HEI_TEST_RESULT, result); return values;
    }

    private void services(Map<String, String> values) {
        values.put(HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP, "already_in_group");
        values.put(HAS_BEEN_LINKED_TO_IGA_GROUP, "already_in_group"); values.put(EDUCATION_PROVIDED, "no");
        values.put(REFERRAL_GIVEN, "no"); values.put(NEXT_APPOINTMENT_DATE, "2026-11-20");
    }

    private boolean complete(Map<String, String> values) throws Exception {
        JSONArray observations = new JSONArray();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            observations.put(new JSONObject().put("fieldCode", entry.getKey())
                    .put("values", new JSONArray().put(entry.getValue())));
        }
        return MotherMentorVisitCompletion.isComplete(observations);
    }
}
