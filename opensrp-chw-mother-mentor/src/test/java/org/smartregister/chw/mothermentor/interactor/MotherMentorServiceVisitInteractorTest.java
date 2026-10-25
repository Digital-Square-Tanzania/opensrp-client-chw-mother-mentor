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

import org.json.JSONArray;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorServiceVisitInteractorTest {
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
    public void englishActionsPreserveVisitFlow() throws Exception {
        assertLocalizedVisitFlow(
                "Visit Type",
                "Group Linkage",
                "Education Provided",
                "Referral Provided",
                "Next Visit Date",
                "Comment");
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliActionsPreserveVisitFlow() throws Exception {
        assertLocalizedVisitFlow(
                "Aina ya hudhurio",
                "Uunganishaji wa vikundi",
                "Elimu iliyotolewa",
                "Rufaa iliyotolewa",
                "Tarehe ya tembeleo lijalo",
                "Maoni");
    }

    private void assertLocalizedVisitFlow(String... titles) throws Exception {
        MotherMentorServiceVisitInteractor interactor =
                new MotherMentorServiceVisitInteractor(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
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
        assertEquals(Arrays.asList(titles), new ArrayList<>(actions.keySet()));
        List<BaseMotherMentorVisitAction> orderedActions = new ArrayList<>(actions.values());
        for (int i = 0; i < titles.length; i++) {
            assertEquals(titles[i], orderedActions.get(i).getTitle());
        }
        BaseMotherMentorVisitAction visitType = orderedActions.get(0);
        assertEquals("mothermentor_visit_type", visitType.getFormName());
        assertVisibility(orderedActions, false, false);

        setVisitType(visitType, "new", "");
        assertVisibility(orderedActions, true, true);
        assertBlankCommentDoesNotBlockSubmission(actions);

        setVisitType(visitType, "repeat", "deceased");
        assertVisibility(orderedActions, false, true);

        setVisitType(visitType, "repeat", "continuing_services");
        assertVisibility(orderedActions, true, true);

        setVisitType(visitType, "", "");
        assertVisibility(orderedActions, false, false);
    }

    private void setVisitType(BaseMotherMentorVisitAction action, String attendance, String status)
            throws Exception {
        JSONObject form = new JSONObject(action.getJsonPayload());
        JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (DBConstants.KEY.ATTENDANCE_TYPE.equals(field.optString("key"))) {
                field.put("value", attendance);
            } else if (DBConstants.KEY.FOLLOW_UP_STATUS.equals(field.optString("key"))) {
                field.put("value", status);
            }
        }
        action.setJsonPayload(form.toString());
    }

    private void assertBlankCommentDoesNotBlockSubmission(
            LinkedHashMap<String, BaseMotherMentorVisitAction> actions) throws Exception {
        BaseMotherMentorVisitAction comment = null;
        for (BaseMotherMentorVisitAction action : actions.values()) {
            if ("mothermentor_visit_comment".equals(action.getFormName())) {
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

    private void assertVisibility(
            List<BaseMotherMentorVisitAction> actions, boolean fullVisit, boolean comment) {
        assertTrue(actions.get(0).isValid());
        assertTrue(actions.get(0).isEnabled());
        for (int i = 1; i < actions.size(); i++) {
            BaseMotherMentorVisitAction action = actions.get(i);
            boolean expected = i == actions.size() - 1 ? comment : fullVisit;
            assertEquals(action.getTitle(), expected, action.isValid());
            assertEquals(action.getTitle(), expected, action.isEnabled());
        }
    }
}
