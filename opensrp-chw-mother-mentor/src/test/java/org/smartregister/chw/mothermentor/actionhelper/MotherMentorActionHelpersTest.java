package org.smartregister.chw.mothermentor.actionhelper;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorActionHelpersTest {
    @Test
    public void serviceAnswersCompleteOnlyTheirOwnActionAndResetOnEmptyOrInvalidPayload()
            throws Exception {
        assertSingleAnswer(
                new MotherMentorEducationProvidedActionHelper(),
                DBConstants.KEY.EDUCATION_PROVIDED,
                "no");
        assertSingleAnswer(
                new MotherMentorReferralProvidedActionHelper(),
                DBConstants.KEY.REFERRAL_GIVEN,
                "no");
        assertSingleAnswer(
                new MotherMentorNextVisitDateActionHelper(),
                DBConstants.KEY.NEXT_APPOINTMENT_DATE,
                "19-10-2026");
        assertSingleAnswer(
                new MotherMentorCommentActionHelper(),
                DBConstants.KEY.COMMENTS,
                "Service completed");
    }

    @Test
    public void groupLinkageNeedsBothAnswersAndAcceptsNo() throws Exception {
        BaseMotherMentorActionHelper helper = new MotherMentorGroupLinkageActionHelper();
        assertStatus(helper, form(DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP, "no"), false);
        assertStatus(
                helper,
                form(
                        DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP,
                        "no",
                        DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP,
                        "yes"),
                true);
        assertStatus(helper, (String) null, false);
    }

    @Test
    public void householdActionsKeepNativeCheckboxAndLegacyAnswerSupport() throws Exception {
        assertSingleAnswer(
                new MotherMentorHouseholdVisitPurposeActionHelper(),
                DBConstants.KEY.PURPOSE_OF_VISIT,
                "counselling");
        assertSingleAnswer(
                new MotherMentorHouseholdCommentActionHelper(),
                DBConstants.KEY.COMMENTS,
                "Follow up");
        for (BaseMotherMentorActionHelper helper :
                Arrays.asList(
                        new MotherMentorHouseholdCounsellingActionHelper(),
                        new MotherMentorHouseholdEducationActionHelper())) {
            String key =
                    helper instanceof MotherMentorHouseholdCounsellingActionHelper
                            ? DBConstants.KEY.PARTICIPANTS
                            : DBConstants.KEY.TOPICS_TAUGHT;
            JSONObject form = form(key, "");
            JSONObject field = FormUtils.getFieldFromForm(form, key);
            JSONObject option =
                    new JSONObject().put("key", "topic").put("text", "Topic").put("value", false);
            field.put("options", new JSONArray().put(option));
            assertStatus(helper, form, false);
            option.put("value", true);
            assertStatus(helper, form, true);
            assertStatus(helper, (String) null, false);
        }
        assertStatus(
                new MotherMentorHouseholdVisitPurposeActionHelper(),
                new JSONObject()
                        .put(
                                "legacy",
                                new JSONObject()
                                        .put(DBConstants.KEY.PURPOSE_OF_VISIT, "counselling")),
                true);
    }

    @Test
    public void visitTypePrefillsBlankAnswersRefreshesFlagsAndPreservesSavedAnswers()
            throws Exception {
        Map<String, String> defaults = new HashMap<>();
        defaults.put(DBConstants.KEY.VISIT_PREGNANCY_STATUS, "pregnant");
        defaults.put(DBConstants.KEY.IIT_ENROLLED, "true");
        MotherMentorVisitTypeActionHelper helper =
                new MotherMentorVisitTypeActionHelper(new MemberObject()) {
                    @Override
                    protected Map<String, String> loadEnrollmentValues() {
                        return defaults;
                    }
                };
        JSONObject source =
                form(
                        DBConstants.KEY.VISIT_PREGNANCY_STATUS,
                        "postnatal",
                        DBConstants.KEY.IIT_ENROLLED,
                        "false");
        helper.onJsonFormLoaded(
                source.toString(), RuntimeEnvironment.application, Collections.emptyMap());
        JSONObject prepared = new JSONObject(helper.getPreProcessed());
        assertEquals(
                "postnatal",
                JsonFormUtils.getValue(prepared, DBConstants.KEY.VISIT_PREGNANCY_STATUS));
        assertEquals("true", JsonFormUtils.getValue(prepared, DBConstants.KEY.IIT_ENROLLED));
        FormUtils.getFieldFromForm(source, DBConstants.KEY.VISIT_PREGNANCY_STATUS).put("value", "");
        helper.onJsonFormLoaded(
                source.toString(), RuntimeEnvironment.application, Collections.emptyMap());
        assertEquals(
                "pregnant",
                JsonFormUtils.getValue(
                        new JSONObject(helper.getPreProcessed()),
                        DBConstants.KEY.VISIT_PREGNANCY_STATUS));
        assertStatus(helper, form(DBConstants.KEY.ATTENDANCE_TYPE, "repeat"), false);
        assertStatus(
                helper,
                form(
                        DBConstants.KEY.ATTENDANCE_TYPE,
                        "repeat",
                        DBConstants.KEY.FOLLOW_UP_STATUS,
                        "not_available"),
                true);
        assertStatus(helper, "invalid-json", false);
    }

    @Test
    public void eachCategoryRequiresOtherReasonDetailsAndClearsThemWhenReasonChanges()
            throws Exception {
        checkReason(
                new MotherMentorInterruptionReasonActionHelper(),
                DBConstants.KEY.SERVICE_INTERRUPTION_REASON,
                DBConstants.KEY.SERVICE_INTERRUPTION_REASON_OTHER,
                DBConstants.KEY.EXPECTED_FACILITY_VISIT_DATE);
        checkReason(
                new MotherMentorPartnerNonAttendanceReasonActionHelper(),
                DBConstants.KEY.PARTNER_NON_ATTENDANCE_REASON,
                DBConstants.KEY.PARTNER_NON_ATTENDANCE_REASON_OTHER,
                DBConstants.KEY.PARTNER_EXPECTED_FACILITY_VISIT_DATE);
        checkReason(
                new MotherMentorChildEidNonAttendanceReasonActionHelper(),
                DBConstants.KEY.CHILD_EID_NON_ATTENDANCE_REASON,
                DBConstants.KEY.CHILD_EID_NON_ATTENDANCE_REASON_OTHER,
                DBConstants.KEY.CHILD_EID_EXPECTED_FACILITY_VISIT_DATE);
    }

    @Test
    public void eachCategoryRequiresAttendanceDateOnlyForYesAndClearsHiddenDate() throws Exception {
        checkArrival(
                new MotherMentorReturnToFacilityActionHelper(),
                DBConstants.KEY.RETURNED_TO_FACILITY,
                DBConstants.KEY.FACILITY_RETURN_DATE);
        checkArrival(
                new MotherMentorPartnerArrivalActionHelper(),
                DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY,
                DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE);
        checkArrival(
                new MotherMentorChildEidArrivalActionHelper(),
                DBConstants.KEY.CHILD_EID_ARRIVED_AT_FACILITY,
                DBConstants.KEY.CHILD_EID_FACILITY_ARRIVAL_DATE);
    }

    @Test
    public void categoryCommentsUseIndependentHelpers() throws Exception {
        for (BaseMotherMentorActionHelper helper :
                Arrays.asList(
                        new MotherMentorFollowUpCommentActionHelper(),
                        new MotherMentorPartnerFollowUpCommentActionHelper(),
                        new MotherMentorChildEidFollowUpCommentActionHelper())) {
            assertSingleAnswer(helper, DBConstants.KEY.COMMENTS, "Follow up completed");
            helper.onPayloadReceived(
                    form(DBConstants.KEY.COMMENTS, "Follow up completed").toString());
            assertEquals("Follow up completed", helper.evaluateSubTitle());
        }
    }

    @Test
    public void allStatusHelpersNotifyWhenAnswersChangeOrAreCleared() throws Exception {
        int[] callbacks = {0};
        Runnable callback = () -> callbacks[0]++;
        for (BaseMotherMentorActionHelper helper :
                Arrays.asList(
                        new MotherMentorFollowUpStatusActionHelper(callback),
                        new MotherMentorPartnerFollowUpStatusActionHelper(callback),
                        new MotherMentorChildEidFollowUpStatusActionHelper(callback))) {
            assertStatus(helper, form(DBConstants.KEY.FOLLOW_UP_STATUS, "found"), true);
            assertStatus(helper, (String) null, false);
        }
        assertEquals(6, callbacks[0]);
    }

    @Test
    public void summariesResolveLibraryTranslationsInEnglishAndSwahili() throws Exception {
        for (String language : Arrays.asList("en", "sw")) {
            Configuration config =
                    new Configuration(
                            RuntimeEnvironment.application.getResources().getConfiguration());
            config.setLocale(new Locale(language));
            Context context = RuntimeEnvironment.application.createConfigurationContext(config);
            BaseMotherMentorActionHelper helper = new MotherMentorPartnerArrivalActionHelper();
            helper.onJsonFormLoaded("{}", context, Collections.emptyMap());
            helper.onPayloadReceived(
                    form(
                                    DBConstants.KEY.PARTNER_ARRIVED_AT_FACILITY,
                                    "yes",
                                    DBConstants.KEY.PARTNER_FACILITY_ARRIVAL_DATE,
                                    "18-10-2026")
                            .toString());
            assertEquals(
                    ("sw".equals(language) ? "Ndiyo" : "Yes") + " · 18-10-2026",
                    helper.evaluateSubTitle());
        }
    }

    @Test
    public void contactInvestigationsUseOnlyTheirOwnFindingsAndSampleEligibilityTracksTbStatus()
            throws Exception {
        MotherMentorTbInvestigationActionHelper tb =
                new MotherMentorTbInvestigationActionHelper(
                        RuntimeEnvironment.application, new MemberObject());
        MotherMentorInvestigationActionHelper mentor =
                new MotherMentorInvestigationActionHelper(
                        RuntimeEnvironment.application, new MemberObject());
        JSONObject tbAnswer =
                form(
                        DBConstants.KEY.TB_SCREENING_FINDINGS,
                        "cough",
                        DBConstants.KEY.SCREENING_STATUS,
                        "tb_presumptive");
        tb.onJsonFormLoaded(
                tbAnswer.toString(), RuntimeEnvironment.application, Collections.emptyMap());
        MotherMentorSampleActionHelper sample =
                new MotherMentorSampleActionHelper(
                        RuntimeEnvironment.application, new MemberObject(), tb);
        assertTrue(sample.isEligibleForSampleCollection());
        assertStatus(tb, tbAnswer, true);
        assertStatus(mentor, tbAnswer, false);
        assertStatus(
                mentor,
                form(DBConstants.KEY.LEGACY_MOTHER_MENTOR_INVESTIGATION_FINDINGS, "finding"),
                true);
        assertStatus(tb, form(DBConstants.KEY.MOTHER_MENTOR_INTERVIEW_FINDINGS, "finding"), false);
        assertFalse(sample.isEligibleForSampleCollection());
        assertStatus(tb, form(DBConstants.KEY.LEGACY_TB_INVESTIGATION_FINDINGS, "finding"), true);
        assertStatus(tb, (String) null, false);
        assertStatus(mentor, (String) null, false);
    }

    private void checkReason(
            BaseMotherMentorActionHelper helper, String reason, String other, String date)
            throws Exception {
        assertStatus(helper, form(reason, "other", date, "18-10-2026"), false);
        JSONObject answered = form(reason, "other", other, "Explanation", date, "18-10-2026");
        assertStatus(helper, answered, true);
        assertNull(helper.postProcess(answered.toString()));
        FormUtils.getFieldFromForm(answered, reason).put("value", "transport_cost");
        assertStatus(helper, answered, true);
        assertEquals(
                "",
                JsonFormUtils.getValue(
                        new JSONObject(helper.postProcess(answered.toString())), other));
        assertStatus(helper, (String) null, false);
    }

    private void checkArrival(BaseMotherMentorActionHelper helper, String answer, String date)
            throws Exception {
        assertStatus(helper, form(answer, "yes"), false);
        JSONObject answered = form(answer, "yes", date, "18-10-2026");
        assertStatus(helper, answered, true);
        assertNull(helper.postProcess(answered.toString()));
        FormUtils.getFieldFromForm(answered, answer).put("value", "no");
        assertStatus(helper, answered, true);
        assertEquals(
                "",
                JsonFormUtils.getValue(
                        new JSONObject(helper.postProcess(answered.toString())), date));
        assertStatus(helper, (String) null, false);
    }

    private void assertSingleAnswer(BaseMotherMentorActionHelper helper, String key, String answer)
            throws Exception {
        assertStatus(helper, form(DBConstants.KEY.FIRST_NAME, "Unrelated"), false);
        assertStatus(helper, form(key, ""), false);
        assertStatus(helper, form(key, answer), true);
        assertStatus(helper, "invalid-json", false);
        assertStatus(helper, form(key, answer), true);
        assertStatus(helper, (String) null, false);
    }

    static JSONObject form(String... values) throws Exception {
        JSONArray fields = new JSONArray();
        for (int i = 0; i < values.length; i += 2) {
            fields.put(
                    new JSONObject()
                            .put(JsonFormConstants.KEY, values[i])
                            .put(JsonFormConstants.VALUE, values[i + 1]));
        }
        return new JSONObject()
                .put("count", 1)
                .put(
                        JsonFormConstants.STEP1,
                        new JSONObject().put(JsonFormConstants.FIELDS, fields));
    }

    private void assertStatus(
            BaseMotherMentorActionHelper helper, JSONObject payload, boolean complete) {
        assertStatus(helper, payload.toString(), complete);
    }

    private void assertStatus(
            BaseMotherMentorActionHelper helper, String payload, boolean complete) {
        helper.onPayloadReceived(payload);
        assertEquals(
                helper.getClass().getSimpleName(),
                complete
                        ? BaseMotherMentorVisitAction.Status.COMPLETED
                        : BaseMotherMentorVisitAction.Status.PENDING,
                helper.evaluateStatusOnPayload());
    }
}
