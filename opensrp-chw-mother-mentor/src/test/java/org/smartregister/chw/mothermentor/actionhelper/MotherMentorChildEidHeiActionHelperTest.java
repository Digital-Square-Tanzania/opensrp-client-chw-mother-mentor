package org.smartregister.chw.mothermentor.actionhelper;

import android.app.Application;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidHeiActionHelperTest {
    @Test
    public void bothFormsPrefillSharedBreastfeedingWithoutAutomaticallyCompletingTheAction() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            Helper helper = helper(directory, "none", "not_breastfeeding", new LocalDate(2026, 7, 1));
            JSONObject form = new JSONObject(helper.getPreProcessed());
            assertEquals("not_breastfeeding", value(form, MAMA_BREASTFEEDING_STATUS));
            assertEquals("2026-07-01", value(form, MAMA_TAREHE_ACHA_KUNYONYESHA));
            assertEquals("2026-01-01", value(form, CHILD_DOB));
            assertEquals("2026-10-01", value(form, MAMA_HEI_ASSESSMENT_DATE));
            assertEquals("none", value(form, MTOTO_RUFAA_STATE_BEFORE));
            assertEquals("miezi9", value(form, MTOTO_RUFAA_TEST));
            assertEquals("2026-10-01", value(form, MTOTO_RUFAA_DUE));
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "agreed");
            helper.onPayloadReceived(helper.postProcess(form.toString()));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        }
    }

    @Test
    public void firstReferralDiscardsTestingAnswersAndRetainsItsImmutableBranchSnapshot() throws Exception {
        Helper helper = helper("json.form", "none", "breastfeeding", null);
        JSONObject form = new JSONObject(helper.getPreProcessed());
        MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "refused");
        MotherMentorMotherChildUtils.put(form, MTOTO_RUFAA_STATE_BEFORE, "referred");
        for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS) {
            MotherMentorMotherChildUtils.put(form, key, "stale");
        }
        JSONObject prepared = new JSONObject(helper.postProcess(form.toString()));
        assertEquals("none", value(prepared, MTOTO_RUFAA_STATE_BEFORE));
        for (String key : MotherMentorChildEidHeiUtils.RETURN_FIELDS) assertEquals("", value(prepared, key));
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
    }

    @Test
    public void newReturnRequiresAReasonForNonAttendanceWithoutAskingReferralAgain() throws Exception {
        Helper helper = helper("json.form", "referred", "breastfeeding", null);
        JSONObject form = new JSONObject(helper.getPreProcessed());
        assertEquals("referred", value(form, MTOTO_RUFAA_STATE_BEFORE));
        assertEquals("1", value(form, MTOTO_RETURN_FLOW));
        assertEquals("", value(form, CHILD_EID_TEST_TYPE));
        MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "agreed");
        MotherMentorMotherChildUtils.put(form, CHILD_EID_ARRIVED_AT_FACILITY, "no");
        MotherMentorMotherChildUtils.put(form, CHILD_EID_FACILITY_ARRIVAL_DATE, "2026-10-01");
        JSONObject prepared = new JSONObject(helper.postProcess(form.toString()));
        assertEquals("", value(prepared, MTOTO_REFERRAL_AGREEMENT));
        assertEquals("", value(prepared, CHILD_EID_FACILITY_ARRIVAL_DATE));
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
        MotherMentorMotherChildUtils.put(prepared, MTOTO_SABABU_KUTOFIKA, "stigma");
        helper.postProcess(prepared.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
    }

    @Test
    public void unknownBreastfeedingIsAskedRatherThanInventedFromTheChildsAge() throws Exception {
        Helper helper = helper("json.form", "none", "", null);
        JSONObject form = new JSONObject(helper.getPreProcessed());
        assertEquals("", value(form, MAMA_BREASTFEEDING_STATUS));
        MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "agreed");
        helper.onPayloadReceived(helper.postProcess(form.toString()));
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
    }

    @Test
    public void editingPreservesTheRecordedBranchAfterAnEarlierReferralWasCorrected() throws Exception {
        Helper helper = helper("json.form", "none", "breastfeeding", null);
        VisitDetail before = new VisitDetail();
        before.setVisitId("original-visit");
        before.setDetails("referred");
        Map<String, List<VisitDetail>> details = new HashMap<>();
        details.put(MTOTO_RUFAA_STATE_BEFORE, Collections.singletonList(before));
        helper.onJsonFormLoaded(helper.sourceForm, RuntimeEnvironment.getApplication(), details);
        JSONObject form = new JSONObject(helper.getPreProcessed());
        assertEquals("referred", value(form, MTOTO_RUFAA_STATE_BEFORE));
        assertEquals("nine_months", value(form, CHILD_EID_TEST_TYPE));
        MotherMentorMotherChildUtils.put(form, CHILD_EID_ARRIVED_AT_FACILITY, "no");
        helper.onPayloadReceived(helper.postProcess(form.toString()));
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
    }

    private Helper helper(String directory, String state, String breastfeeding, LocalDate weaning) throws Exception {
        MotherMentorMotherChildRepository.State clinical = mock(MotherMentorMotherChildRepository.State.class);
        when(clinical.getChildDob()).thenReturn(new LocalDate(2026, 1, 1));
        when(clinical.getBreastfeedingStatus()).thenReturn(breastfeeding);
        when(clinical.getWeaningDate()).thenReturn(weaning);
        when(clinical.getSamplingHistory()).thenReturn(Collections.emptyMap());
        Helper helper = new Helper(state, clinical);
        try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                .open(directory + "/mothermentor_child_eid_hei.json"), "UTF-8").useDelimiter("\\A")) {
            helper.onJsonFormLoaded(scanner.next(), RuntimeEnvironment.getApplication(), null);
        }
        return helper;
    }

    private String value(JSONObject form, String key) {
        return MotherMentorMotherChildUtils.values(form).get(key);
    }

    private static class Helper extends MotherMentorChildEidHeiActionHelper {
        private final String state;
        private final MotherMentorMotherChildRepository.State clinical;

        Helper(String state, MotherMentorMotherChildRepository.State clinical) {
            super(null, null);
            this.state = state;
            this.clinical = clinical;
        }

        @Override protected String loadReferralState() { return state; }
        @Override protected MotherMentorMotherChildRepository.State loadClinicalState() { return clinical; }
        @Override protected LocalDate getServiceDate() { return new LocalDate(2026, 10, 1); }
    }
}
