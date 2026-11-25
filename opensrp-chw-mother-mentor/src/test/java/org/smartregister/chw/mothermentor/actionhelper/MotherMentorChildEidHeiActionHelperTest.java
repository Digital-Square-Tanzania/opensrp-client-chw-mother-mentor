package org.smartregister.chw.mothermentor.actionhelper;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;
import org.smartregister.chw.mothermentor.util.MotherMentorChildEidHeiUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.Stage;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule.TestRecord;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherChildUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidHeiActionHelperTest {
    @Test
    public void bothFormsCarryTheChildEidDateAndCalculateTheDueTestOnOpen() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            LocalDate weaning = new LocalDate(2026, 7, 1);
            Helper helper = helper(directory, "none", "breastfeeding", null);
            when(helper.clinical.getChildEidWeaningDate()).thenReturn(weaning);
            Map<Stage, TestRecord> history = new HashMap<>();
            history.put(Stage.AT_BIRTH, new TestRecord("negative", new LocalDate(2026, 1, 1), false, null));
            history.put(Stage.SIX_WEEKS, new TestRecord("negative", new LocalDate(2026, 2, 12), false, null));
            history.put(Stage.NINE_MONTHS, new TestRecord("negative", new LocalDate(2026, 10, 1), false, null));
            when(helper.clinical.getSamplingHistory()).thenReturn(history);
            JSONObject form = new JSONObject(helper.getPreProcessed());
            assertEquals("not_breastfeeding", value(form, MAMA_BREASTFEEDING_STATUS));
            assertEquals("01-07-2026", value(form, MAMA_TAREHE_ACHA_KUNYONYESHA));
            assertTrue(MotherMentorMotherChildUtils.field(form, MAMA_TAREHE_ACHA_KUNYONYESHA)
                    .getBoolean("read_only"));
            assertTrue(MotherMentorMotherChildUtils.field(form, MAMA_BREASTFEEDING_STATUS)
                    .getBoolean("read_only"));
            assertEquals("1", value(form, MTOTO_WEANING_CARRIED));
            assertEquals("2026-01-01", value(form, CHILD_DOB));
            assertEquals("2026-10-01", value(form, MAMA_HEI_ASSESSMENT_DATE));
            assertEquals("none", value(form, MTOTO_RUFAA_STATE_BEFORE));
            assertEquals("miezi15", value(form, MTOTO_RUFAA_TEST));
            assertEquals("2026-10-01", value(form, MTOTO_RUFAA_DUE));
            assertTrue(value(form, MAMA_HEI_DUE_TESTS).contains("three_months_after_weaning"));
            assertEquals("json.form-sw".equals(directory) ? "Miezi 15" : "15 months",
                    value(form, MAMA_HEI_NEXT_TEST_NOTE));
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            MotherMentorMotherChildUtils.put(form, MAMA_TAREHE_ACHA_KUNYONYESHA, "15-07-2026");
            MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "agreed");
            JSONObject saved = new JSONObject(helper.postProcess(form.toString()));
            assertEquals("01-07-2026", value(saved, MAMA_TAREHE_ACHA_KUNYONYESHA));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        }
    }

    @Test
    public void bothFormsKeepTheRequiredDateEmptyUntilMtotoEidRecordsIt() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            Helper helper = helper(directory, "none", "not_breastfeeding", new LocalDate(2026, 7, 1));
            JSONObject form = new JSONObject(helper.getPreProcessed());
            JSONObject field = MotherMentorMotherChildUtils.field(form, MAMA_TAREHE_ACHA_KUNYONYESHA);
            assertEquals("not_breastfeeding", value(form, MAMA_BREASTFEEDING_STATUS));
            assertEquals("", field.getString("value"));
            assertEquals("", value(form, MAMA_HEI_NEXT_TEST_NOTE));
            assertFalse(field.getBoolean("read_only"));
            assertTrue(field.getJSONObject("v_required").getBoolean("value"));
            MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "agreed");
            helper.postProcess(form.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            field.put("value", "01-07-2026");
            JSONObject saved = new JSONObject(helper.postProcess(form.toString()));
            assertEquals("01-07-2026", value(saved, MAMA_TAREHE_ACHA_KUNYONYESHA));
            assertEquals("", value(saved, MTOTO_WEANING_CARRIED));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        }
    }

    @Test
    public void editingALaterVisitUsesTheCorrectedSourceInsteadOfItsSavedCopy() throws Exception {
        Helper helper = helper("json.form", "none", "not_breastfeeding", new LocalDate(2026, 7, 1));
        VisitDetail detail = new VisitDetail();
        detail.setVisitId("later-visit");
        detail.setDetails("01-07-2026");
        Map<String, List<VisitDetail>> details = Collections.singletonMap(
                MAMA_TAREHE_ACHA_KUNYONYESHA, Collections.singletonList(detail));
        when(helper.clinical.getChildEidWeaningDate()).thenReturn(new LocalDate(2026, 7, 5));
        helper.onJsonFormLoaded(helper.sourceForm, RuntimeEnvironment.getApplication(), details);
        JSONObject form = new JSONObject(helper.getPreProcessed());
        // The action builder restores saved values after preprocessing.
        MotherMentorMotherChildUtils.put(form, MAMA_TAREHE_ACHA_KUNYONYESHA, detail.getDetails());
        JSONObject saved = new JSONObject(helper.postProcess(form.toString()));
        assertEquals("05-07-2026", value(saved, MAMA_TAREHE_ACHA_KUNYONYESHA));
        assertEquals("1", value(saved, MTOTO_WEANING_CARRIED));
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
    public void bothFormsOfferThreeAnswersWithoutAffirmativeHistoryAndSaveNeverBreastfed() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            Helper helper = helper(directory, "none", "not_breastfeeding", null);
            JSONObject form = new JSONObject(helper.getPreProcessed());
            assertEquals("", value(form, MAMA_BREASTFEEDING_STATUS));
            JSONArray options = MotherMentorMotherChildUtils.field(form, MAMA_BREASTFEEDING_STATUS)
                    .getJSONArray("options");
            assertEquals(3, options.length());
            List<String> labels = "json.form".equals(directory)
                    ? Arrays.asList("Breastfeeding", "Never breastfed", "Stopped breastfeeding")
                    : Arrays.asList("Ananyonyeshwa", "Hajawahi kunyonyeshwa", "Ameacha kunyonyeshwa");
            for (int i = 0; i < labels.size(); i++) {
                assertEquals(labels.get(i), options.getJSONObject(i).getString("text"));
            }
            MotherMentorMotherChildUtils.put(form, MAMA_BREASTFEEDING_STATUS, "never_breastfed");
            MotherMentorMotherChildUtils.put(form, MAMA_TAREHE_ACHA_KUNYONYESHA, "2026-07-01");
            MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "agreed");
            JSONObject saved = new JSONObject(helper.postProcess(form.toString()));
            assertEquals("never_breastfed", value(saved, MAMA_BREASTFEEDING_STATUS));
            assertEquals("", value(saved, MAMA_TAREHE_ACHA_KUNYONYESHA));
            assertEquals("miezi9", value(saved, MTOTO_RUFAA_TEST));
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        }
    }

    @Test
    public void bothFormsRemoveAndRejectNeverBreastfedAfterBreastfeedingWasRecorded() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            for (String recorded : new String[] {"breastfeeding", "not_breastfeeding"}) {
                LocalDate weaning = "not_breastfeeding".equals(recorded) ? new LocalDate(2026, 7, 1) : null;
                Helper helper = helper(directory, "none", recorded, weaning);
                when(helper.clinical.hasBreastfeedingHistory()).thenReturn(true);
                JSONObject form = new JSONObject(helper.getPreProcessed());
                JSONArray options = MotherMentorMotherChildUtils.field(form, MAMA_BREASTFEEDING_STATUS)
                        .getJSONArray("options");
                assertEquals(2, options.length());
                assertEquals("breastfeeding", options.getJSONObject(0).getString("key"));
                assertEquals("not_breastfeeding", options.getJSONObject(1).getString("key"));
                assertEquals(recorded, value(form, MAMA_BREASTFEEDING_STATUS));
                MotherMentorMotherChildUtils.put(form, MAMA_BREASTFEEDING_STATUS, "never_breastfed");
                MotherMentorMotherChildUtils.put(form, MTOTO_REFERRAL_AGREEMENT, "agreed");
                helper.postProcess(form.toString());
                assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            }
        }
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
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.setLocale(new Locale("json.form-sw".equals(directory) ? "sw" : "en"));
        Context context = app.createConfigurationContext(configuration);
        try (Scanner scanner = new Scanner(context.getAssets()
                .open(directory + "/mothermentor_child_eid_hei.json"), "UTF-8").useDelimiter("\\A")) {
            helper.onJsonFormLoaded(scanner.next(), context, null);
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
