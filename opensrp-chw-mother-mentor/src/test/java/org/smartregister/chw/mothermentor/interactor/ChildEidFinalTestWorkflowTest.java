package org.smartregister.chw.mothermentor.interactor;

import android.app.Application;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.util.FormUtils;

import java.util.LinkedHashMap;
import java.util.Scanner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class ChildEidFinalTestWorkflowTest {
    private MotherMentorLibrary previousLibrary;
    private FormUtils previousForms;
    private FormUtils forms;
    private LinkedHashMap<String, BaseMotherMentorVisitAction> actions;

    @Before
    public void setup() throws Exception {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.application);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        previousForms = ReflectionHelpers.getStaticField(FormUtils.class, "instance");
        forms = mock(FormUtils.class);
        when(forms.getFormJson(anyString())).thenAnswer(call -> {
            String folder = RuntimeEnvironment.application.getResources().getConfiguration().locale.getLanguage().equals("sw")
                    ? "json.form-sw" : "json.form";
            try (Scanner s = new Scanner(RuntimeEnvironment.application.getAssets().open(folder + "/" + call.getArgument(0) + ".json"), "UTF-8")) {
                return new JSONObject(s.useDelimiter("\\A").next());
            }
        });
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", forms);
    }

    @After
    public void cleanup() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
        ReflectionHelpers.setStaticField(FormUtils.class, "instance", previousForms);
    }

    @Test @Config(qualifiers = "en")
    public void englishFinalTestIsOnlyShownAtEighteenMonthsAndClearsWhenBranchChanges() throws Exception { branches(); }

    @Test @Config(qualifiers = "sw")
    public void swahiliFinalTestIsOnlyShownAtEighteenMonthsAndClearsWhenBranchChanges() throws Exception { branches(); }

    private void branches() throws Exception {
        ChildEidFollowUpVisitInteractor i = new ChildEidFollowUpVisitInteractor("Mother Mentor Child EID Follow Up");
        AppExecutors executors = new AppExecutors(Runnable::run, Runnable::run, Runnable::run);
        i.appExecutors = executors;
        ((BaseMotherMentorVisitInteractor) i).appExecutors = executors;
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getContext()).thenReturn(RuntimeEnvironment.application);
        MemberObject member = new MemberObject(); member.setBaseEntityId("child");
        BaseMotherMentorVisitContract.InteractorCallBack callback = mock(BaseMotherMentorVisitContract.InteractorCallBack.class);
        i.calculateActions(view, member, callback);
        ArgumentCaptor<LinkedHashMap> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(callback).preloadActions(captor.capture()); actions = captor.getValue();
        BaseMotherMentorVisitAction finalTest = action("mothermentor_child_eid_final_test");
        assertFalse(finalTest.isValid());
        answer("mothermentor_child_eid_follow_up_status", "follow_up_status", "found");
        answer("mothermentor_child_eid_test_type", "child_eid_test_type", "nine_months");
        answer("mothermentor_child_eid_arrival", "child_eid_arrived_at_facility", "yes", "child_eid_facility_arrival_date", "28-10-2026");
        assertFalse(finalTest.isValid());
        answer("mothermentor_child_eid_test_type", "child_eid_test_type", "eighteen_months");
        assertTrue(finalTest.isValid()); assertTrue(finalTest.isEnabled());
        answer("mothermentor_child_eid_final_test", "child_eid_final_hiv_test_done", "yes", "child_eid_final_hiv_result", "positive", "child_eid_final_hiv_test_date", "28-10-2026");
        assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED, finalTest.getActionStatus());
        answer("mothermentor_child_eid_arrival", "child_eid_arrived_at_facility", "no");
        assertFalse(finalTest.isValid()); assertNull(finalTest.getJsonPayload());
        answer("mothermentor_child_eid_arrival", "child_eid_arrived_at_facility", "yes", "child_eid_facility_arrival_date", "28-10-2026");
        answer("mothermentor_child_eid_final_test", "child_eid_final_hiv_test_done", "no");
        answer("mothermentor_child_eid_test_type", "child_eid_test_type", "six_weeks");
        assertFalse(finalTest.isValid()); assertNull(finalTest.getJsonPayload());
    }

    private BaseMotherMentorVisitAction action(String name) {
        for (BaseMotherMentorVisitAction a : actions.values()) if (name.equals(a.getFormName())) return a;
        throw new AssertionError("Missing action " + name);
    }
    private void answer(String name, String... pairs) throws Exception {
        BaseMotherMentorVisitAction a = action(name);
        JSONObject form = a.getJsonPayload() == null ? forms.getFormJson(name) : new JSONObject(a.getJsonPayload());
        for (int j = 0; j < pairs.length; j += 2) com.vijay.jsonwizard.utils.FormUtils.getFieldFromForm(form, pairs[j]).put("value", pairs[j+1]);
        a.setJsonPayload(form.toString());
    }
}
