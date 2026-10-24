package org.smartregister.chw.mothermentor.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowActivity;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.view.activity.BaseRegisterActivity;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE,
        shadows = MotherMentorEnrollmentLaunchReplayTest.RegisterInitializationShadow.class)
public class MotherMentorEnrollmentLaunchReplayTest {
    private static final String CLIENT_ID = "mother-mentor-client";
    private static final String FAMILY_ID = "mother-mentor-family";

    @Test
    @Config(qualifiers = "en")
    public void freshPrimaryEnrollmentOpensOnceAndKeepsItsPayload() {
        TestRegisterActivity activity = create(request(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT), null);

        assertLaunch(activity, Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
        assertPayload(activity, Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
    }

    @Test
    @Config(qualifiers = "sw")
    public void freshIitEnrollmentOpensOnceAndKeepsItsPayload() {
        TestRegisterActivity activity = create(request(Constants.FORMS.MOTHERMENTOR_ENROLL_IIT), null);

        assertLaunch(activity, Constants.FORMS.MOTHERMENTOR_ENROLL_IIT);
        assertPayload(activity, Constants.FORMS.MOTHERMENTOR_ENROLL_IIT);
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliLanguageRestartDoesNotReplayAnEnrollmentRequest() {
        assertLanguageRestartDoesNotReplay(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
        assertLanguageRestartDoesNotReplay(Constants.FORMS.MOTHERMENTOR_ENROLL_IIT);
    }

    @Test
    @Config(qualifiers = "en")
    public void englishLanguageRestartDoesNotReplayAnEnrollmentRequest() {
        assertLanguageRestartDoesNotReplay(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
        assertLanguageRestartDoesNotReplay(Constants.FORMS.MOTHERMENTOR_ENROLL_IIT);
    }

    @Test
    public void repeatedInitialActionDoesNotOpenAnotherForm() {
        TestRegisterActivity activity = create(request(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT), null);

        activity.onStartActivityWithAction();

        assertLaunch(activity, Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
    }

    @Test
    public void savedStateRestorationDoesNotReplayTheOriginalRequest() {
        TestRegisterActivity restored = create(
                request(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT), new Bundle());

        assertEquals(0, restored.launches);
        assertPayload(restored, Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
    }

    @Test
    public void languageRestartAfterStateRestorationDoesNotReplayTheRequest() {
        TestRegisterActivity restored = create(
                request(Constants.FORMS.MOTHERMENTOR_ENROLL_IIT), new Bundle());

        TestRegisterActivity languageRestart = create(new Intent(restored.getIntent()), null);

        assertEquals(0, languageRestart.launches);
        assertPayload(languageRestart, Constants.FORMS.MOTHERMENTOR_ENROLL_IIT);
    }

    @Test
    public void cancelledFormDoesNotReopenOnTheNextLanguageRestart() {
        TestRegisterActivity initial = create(request(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT), null);
        initial.onActivityResult(Constants.REQUEST_CODE_GET_JSON, Activity.RESULT_CANCELED, null);

        TestRegisterActivity restarted = create(new Intent(initial.getIntent()), null);

        assertEquals(0, restarted.launches);
        assertPayload(restarted, Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
    }

    @Test
    public void aGenuinelyNewRequestForTheSameClientStillOpensTheForm() {
        TestRegisterActivity initial = create(request(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT), null);
        TestRegisterActivity languageRestart = create(new Intent(initial.getIntent()), null);

        TestRegisterActivity newRequest = create(request(Constants.FORMS.MOTHER_MENTOR_ENROLLMENT), null);

        assertEquals(0, languageRestart.launches);
        assertLaunch(newRequest, Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
        assertPayload(newRequest, Constants.FORMS.MOTHER_MENTOR_ENROLLMENT);
    }

    @Test
    public void openingTheRegisterWithoutAFormDoesNotLaunchAForm() {
        TestRegisterActivity activity = create(new Intent(), null);

        assertEquals(0, activity.launches);
        assertNull(activity.FORM_NAME);
    }

    private void assertLanguageRestartDoesNotReplay(String form) {
        TestRegisterActivity initial = create(request(form), null);
        assertLaunch(initial, form);

        // The drawer finishes the activity and starts the same Intent with no saved Bundle.
        TestRegisterActivity restarted = create(new Intent(initial.getIntent()), null);

        assertEquals(0, restarted.launches);
        assertPayload(restarted, form);
    }

    private TestRegisterActivity create(Intent intent, Bundle savedState) {
        TestRegisterActivity activity = Robolectric.buildActivity(TestRegisterActivity.class).get();
        activity.setIntent(intent);
        activity.onCreate(savedState);
        return activity;
    }

    private Intent request(String form) {
        return new Intent()
                .putExtra(Constants.ACTIVITY_PAYLOAD.BASE_ENTITY_ID, CLIENT_ID)
                .putExtra(Constants.ACTIVITY_PAYLOAD.FAMILY_BASE_ENTITY_ID, FAMILY_ID)
                .putExtra(Constants.ACTIVITY_PAYLOAD.MOTHER_MENTOR_FORM_NAME, form)
                .putExtra(DBConstants.KEY.GENDER, "Female")
                .putExtra(DBConstants.KEY.AGE, 32);
    }

    private void assertLaunch(TestRegisterActivity activity, String form) {
        assertEquals(1, activity.launches);
        assertEquals(form, activity.launchedForm);
        assertEquals(CLIENT_ID, activity.launchedClient);
        assertNull(activity.launchedMetadata);
    }

    private void assertPayload(TestRegisterActivity activity, String form) {
        assertEquals(CLIENT_ID, activity.BASE_ENTITY_ID);
        assertEquals(FAMILY_ID, activity.FAMILY_BASE_ENTITY_ID);
        assertEquals(form, activity.FORM_NAME);
        Intent intent = activity.getIntent();
        assertEquals(CLIENT_ID, intent.getStringExtra(Constants.ACTIVITY_PAYLOAD.BASE_ENTITY_ID));
        assertEquals(FAMILY_ID, intent.getStringExtra(Constants.ACTIVITY_PAYLOAD.FAMILY_BASE_ENTITY_ID));
        assertEquals(form, intent.getStringExtra(Constants.ACTIVITY_PAYLOAD.MOTHER_MENTOR_FORM_NAME));
        assertEquals("Female", intent.getStringExtra(DBConstants.KEY.GENDER));
        assertEquals(32, intent.getIntExtra(DBConstants.KEY.AGE, -1));
    }

    public static class TestRegisterActivity extends BaseMotherMentorRegisterActivity {
        int launches;
        String launchedForm;
        String launchedClient;
        String launchedMetadata;

        @Override
        public void startFormActivity(String formName, String entityId, String metaData) {
            launches++;
            launchedForm = formName;
            launchedClient = entityId;
            launchedMetadata = metaData;
        }
    }

    @Implements(BaseRegisterActivity.class)
    public static class RegisterInitializationShadow extends ShadowActivity {
        @Implementation
        protected void onCreate(Bundle savedInstanceState) {
            // Isolate unrelated register/fragment/DB initialization; the library onCreate stays real.
        }
    }
}
