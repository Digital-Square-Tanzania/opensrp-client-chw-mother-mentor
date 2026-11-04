package org.smartregister.chw.mothermentor.actionhelper;

import android.app.Application;

import com.vijay.jsonwizard.utils.FormUtils;

import org.joda.time.LocalDate;
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

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidStatusWorkflowTest {
    @Test
    public void foundRequiresAnExplicitChildStatus() throws Exception {
        assertPending("found");
    }

    @Test
    public void notFoundRequiresAReason() throws Exception {
        assertPending("not_found");
    }

    @Test
    public void legacyFlatDeceasedIsNotAValidNewSchemaAnswer() throws Exception {
        assertPending("deceased");
    }

    @Test
    public void legacyFlatAnswersKeepTheirOriginalCompletionRules() throws Exception {
        MotherMentorChildEidFollowUpStatusActionHelper helper =
                new MotherMentorChildEidFollowUpStatusActionHelper(null);
        for (String status : new String[] {"found", "not_found", "deceased", "transferred_facility"}) {
            helper.onPayloadReceived(MotherMentorActionHelpersTest.form("follow_up_status", status).toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
        }
    }

    @Test
    public void preprocessingUsesTheRealChildDobAndOriginalServiceDay() throws Exception {
        MemberObject member = new MemberObject(); member.setDob("2026-01-20");
        MotherMentorChildEidFollowUpStatusActionHelper helper =
                new MotherMentorChildEidFollowUpStatusActionHelper(member,
                        new LocalDate(2026, 10, 20), null);
        JSONObject form = MotherMentorActionHelpersTest.form(
                "child_eid_follow_up_schema", "3", "child_dob", "tampered birth date",
                "mama_tarehe_mtoto_alifariki", "");
        helper.onJsonFormLoaded(form.toString(), RuntimeEnvironment.getApplication(), Collections.emptyMap());
        JSONObject prepared = new JSONObject(helper.getPreProcessed());
        assertEquals("2026-01-20", JsonFormUtils.getValue(prepared, "child_dob"));
        JSONObject date = FormUtils.getFieldFromForm(prepared, "mama_tarehe_mtoto_alifariki");
        assertEquals("20-01-2026", date.getString("min_date"));
        assertEquals("20-10-2026", date.getString("max_date"));
        FormUtils.getFieldFromForm(prepared, "mama_tarehe_mtoto_alifariki").put("value", "21-10-2026");
        prepared.getJSONObject("step1").getJSONArray("fields")
                .put(new JSONObject().put("key", "follow_up_status").put("value", "found"))
                .put(new JSONObject().put("key", "mama_child_status").put("value", "deceased"));
        helper.onPayloadReceived(prepared.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
    }

    @Test
    public void storedBirthTimestampsKeepTheirOwnCalendarDayAtOffsetBoundaries() throws Exception {
        for (String birth : new String[] {
                "2026-01-20T23:30:00.000-03:00",
                "2026-01-20T00:30:00.000+14:00",
                "2026-01-20T00:00:00"}) {
            MemberObject member = new MemberObject(); member.setDob(birth);
            MotherMentorChildEidFollowUpStatusActionHelper helper =
                    new MotherMentorChildEidFollowUpStatusActionHelper(member,
                            new LocalDate(2026, 10, 20), null);
            JSONObject form = MotherMentorActionHelpersTest.form(
                    "child_eid_follow_up_schema", "3", "child_dob", "",
                    "mama_tarehe_mtoto_alifariki", "20-01-2026",
                    "follow_up_status", "found", "mama_child_status", "deceased");
            helper.onJsonFormLoaded(form.toString(), RuntimeEnvironment.getApplication(),
                    Collections.emptyMap());
            JSONObject prepared = new JSONObject(helper.getPreProcessed());
            assertEquals(birth, "2026-01-20", JsonFormUtils.getValue(prepared, "child_dob"));
            assertEquals("20-01-2026", FormUtils.getFieldFromForm(prepared,
                    "mama_tarehe_mtoto_alifariki").getString("min_date"));
            helper.onPayloadReceived(prepared.toString());
            assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED,
                    helper.evaluateStatusOnPayload());
        }
    }

    @Test
    public void changeCallbackObservesTheNormalizedActionPayloadAndRunsOnlyAfterAssignment() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        MotherMentorChildEidFollowUpStatusActionHelper helper =
                new MotherMentorChildEidFollowUpStatusActionHelper(callbacks::incrementAndGet);
        JSONObject form = MotherMentorActionHelpersTest.form(
                "child_eid_follow_up_schema", "3", "follow_up_status", "not_found",
                "mtoto_not_found_reason", "moved_area", "mtoto_moved_area_facility", "New optional facility",
                "mtoto_new_facility", "Old facility");
        helper.onPayloadReceived(form.toString());
        assertEquals(0, callbacks.get());
        BaseMotherMentorVisitAction action = new BaseMotherMentorVisitAction.Builder(
                RuntimeEnvironment.getApplication(), "Status")
                .withFormName("mothermentor_child_eid_follow_up_status")
                .withJsonPayload(form.toString()).withHelper(helper).build();
        assertEquals(0, callbacks.get());
        action.setJsonPayload(form.toString());
        assertEquals(1, callbacks.get());
        assertEquals("New optional facility", JsonFormUtils.getValue(
                new JSONObject(action.getJsonPayload()), DBConstants.KEY.MTOTO_NEW_FACILITY));
    }

    private void assertPending(String status) throws Exception {
        MotherMentorChildEidFollowUpStatusActionHelper helper =
                new MotherMentorChildEidFollowUpStatusActionHelper(null);
        JSONObject form = MotherMentorActionHelpersTest.form(
                "child_eid_follow_up_schema", "3", "follow_up_status", status);
        helper.onPayloadReceived(form.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
    }
}
