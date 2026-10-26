package org.smartregister.chw.mothermentor.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.activity.BaseMotherMentorVisitActivity;
import org.smartregister.chw.mothermentor.adapter.BaseMotherMentorVisitAdapter;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.util.FormUtils;

import java.util.LinkedHashMap;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdVisitInteractorTest {
    private MotherMentorLibrary previousLibrary;
    private FormUtils previousFormUtils;

    @Before
    public void setUp() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.application);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        previousFormUtils = ReflectionHelpers.getStaticField(FormUtils.class, "instance");
        FormUtils formUtils = mock(FormUtils.class);
        when(formUtils.getFormJson(anyString()))
                .thenAnswer(
                        invocation -> {
                            Application application = RuntimeEnvironment.application;
                            String language =
                                    application.getResources().getConfiguration().locale.getLanguage();
                            String directory = "sw".equals(language) ? "json.form-sw" : "json.form";
                            String formName = invocation.getArgument(0);
                            try (Scanner scanner =
                                    new Scanner(
                                            application
                                                    .getAssets()
                                                    .open(directory + "/" + formName + ".json"),
                                            "UTF-8")) {
                                return new JSONObject(scanner.useDelimiter("\\A").next());
                            }
                        });
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", formUtils);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", previousFormUtils);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
    }

    @Test
    @Config(qualifiers = "en")
    public void englishHouseholdCanSubmitWithoutComment() throws Exception {
        assertHouseholdCanSubmitWithoutComment();
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliHouseholdCanSubmitWithoutComment() throws Exception {
        assertHouseholdCanSubmitWithoutComment();
    }

    private void assertHouseholdCanSubmitWithoutComment() throws Exception {
        MotherMentorHouseholdVisitInteractor interactor =
                new MotherMentorHouseholdVisitInteractor(
                        Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,
                        Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT);
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        interactor.appExecutors = executors;
        ((BaseMotherMentorVisitInteractor) interactor).appExecutors = executors;
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.application);
        when(view.getEditMode()).thenReturn(false);
        BaseMotherMentorVisitContract.InteractorCallBack callback =
                mock(BaseMotherMentorVisitContract.InteractorCallBack.class);
        interactor.calculateActions(view, new MemberObject(), callback);
        ArgumentCaptor<LinkedHashMap> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(callback).preloadActions(captor.capture());
        LinkedHashMap<String, BaseMotherMentorVisitAction> actions = captor.getValue();
        assertEquals(4, actions.size());

        BaseMotherMentorVisitAction comment = null;
        for (BaseMotherMentorVisitAction action : actions.values()) {
            if ("mothermentor_household_visit_comment".equals(action.getFormName())) {
                comment = action;
                assertTrue(action.isOptional());
                JSONObject field =
                        com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(
                                new JSONObject(action.getJsonPayload()), DBConstants.KEY.COMMENTS);
                assertFalse(
                        field.optJSONObject("v_required") != null
                                && field.getJSONObject("v_required").optBoolean("value"));
                assertEquals("100", field.getJSONObject("v_max_length").getString("value"));
                action.setActionStatus(BaseMotherMentorVisitAction.Status.PENDING);
            } else {
                assertFalse(action.isOptional());
                action.setActionStatus(BaseMotherMentorVisitAction.Status.COMPLETED);
            }
        }
        assertNotNull(comment);
        BaseMotherMentorVisitActivity activity =
                Robolectric.buildActivity(BaseMotherMentorVisitActivity.class).get();
        TextView submit = new TextView(activity);
        ReflectionHelpers.setField(activity, "tvSubmit", submit);
        ReflectionHelpers.setField(activity, "actionList", actions);
        ReflectionHelpers.setField(
                activity, "mAdapter", new BaseMotherMentorVisitAdapter(activity, activity, actions));

        activity.redrawVisitUI();
        assertTrue(submit.hasOnClickListeners());
        for (BaseMotherMentorVisitAction required : actions.values()) {
            if (required.isOptional()) continue;
            required.setActionStatus(BaseMotherMentorVisitAction.Status.PENDING);
            activity.redrawVisitUI();
            assertFalse(required.getTitle(), submit.hasOnClickListeners());
            required.setActionStatus(BaseMotherMentorVisitAction.Status.COMPLETED);
        }
    }
}
