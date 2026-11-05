package org.smartregister.chw.mothermentor.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.DialogInterface;
import android.os.Looper;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.repository.AllSharedPreferences;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorPartnerClosureConfirmationTest {
    @Test
    public void confirmingDeathAndRelocationSubmitsOnlyAfterConsent() throws Exception {
        for (String reason : new String[] {"deceased", "moved"}) {
            TestActivity activity = activity(reason);
            activity.submitVisit();
            verify(activity.presenter, never()).submitVisit();
            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
            assertNotNull(dialog);
            assertEquals(activity.getString(R.string.mothermentor_partner_close_confirm),
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            verify(activity.presenter).submitVisit();
        }
    }

    @Test
    @Config(qualifiers = "sw")
    public void cancellingReturnsToTheFormWithTheSelectionIntact() throws Exception {
        TestActivity activity = activity("moved", "sw");
        activity.submitVisit();
        AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
        assertEquals("Ghairi", dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getText().toString());
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        verify(activity.presenter, never()).submitVisit();
        assertTrue(activity.cancelled);
        assertEquals("moved", activity.reopened.getJSONObject("step1").getJSONArray("fields")
                .getJSONObject(1).getString("value"));
    }

    @Test
    public void nonImmediateOutcomesSubmitWithoutConfirmation() throws Exception {
        TestActivity activity = activity("travelled");
        activity.submitVisit();
        verify(activity.presenter).submitVisit();
    }

    private TestActivity activity(String reason) throws Exception {
        return activity(reason, "en");
    }

    private TestActivity activity(String reason, String language) throws Exception {
        RuntimeEnvironment.setQualifiers(language);
        new AllSharedPreferences(PreferenceManager.getDefaultSharedPreferences(
                RuntimeEnvironment.getApplication())).saveLanguagePreference(language);
        TestActivity activity = Robolectric.buildActivity(TestActivity.class).get();
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        activity.presenter = mock(BaseMotherMentorVisitContract.Presenter.class);
        BaseMotherMentorVisitAction action = mock(BaseMotherMentorVisitAction.class);
        when(action.getFormName()).thenReturn("mothermentor_partner_follow_up_status");
        when(action.getTitle()).thenReturn("Status");
        when(action.isValid()).thenReturn(true);
        when(action.getActionStatus()).thenReturn(BaseMotherMentorVisitAction.Status.COMPLETED);
        JSONArray fields = new JSONArray()
                .put(new JSONObject().put("key", DBConstants.KEY.FOLLOW_UP_STATUS).put("value", "not_found"))
                .put(new JSONObject().put("key", DBConstants.KEY.PARTNER_NOT_FOUND_REASON).put("value", reason));
        when(action.getJsonPayload()).thenReturn(new JSONObject().put("count", "1")
                .put("step1", new JSONObject().put("fields", fields)).toString());
        activity.actionList.put("Status", action);
        return activity;
    }

    public static class TestActivity extends BaseMotherMentorVisitActivity {
        boolean cancelled;
        JSONObject reopened;

        @Override protected void onSubmissionCancelled() { cancelled = true; }
        @Override public void startFormActivity(JSONObject form) { reopened = form; }
    }
}
