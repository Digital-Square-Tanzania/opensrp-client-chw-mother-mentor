package org.smartregister.chw.mothermentor.actionhelper;

import android.app.Application;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;

import static org.junit.Assert.assertEquals;

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

    private void assertPending(String status) throws Exception {
        MotherMentorChildEidFollowUpStatusActionHelper helper =
                new MotherMentorChildEidFollowUpStatusActionHelper(null);
        JSONObject form = MotherMentorActionHelpersTest.form(
                "child_eid_follow_up_schema", "3", "follow_up_status", status);
        helper.onPayloadReceived(form.toString());
        assertEquals(BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
    }
}
