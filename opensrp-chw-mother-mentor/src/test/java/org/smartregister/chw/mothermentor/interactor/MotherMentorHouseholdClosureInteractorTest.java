package org.smartregister.chw.mothermentor.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdClosure;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdClosureInteractorTest {
    private MotherMentorLibrary previousLibrary;
    private FixtureInteractor interactor;

    @Before
    public void setUp() {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        when(library.context()).thenReturn(context);
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.application);
        when(library.visitRepository()).thenReturn(mock(VisitRepository.class));
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        interactor = new FixtureInteractor();
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
    }

    @Test
    public void finalizedNotFoundSnapshotsAreBoundToCurrentEnrollmentAndReplaceOldValues()
            throws Exception {
        initialize(false);
        status("repeat", "not_found", "moved");
        Event event = new Event();
        event.addObs(new Obs("concept", "text", KAYA_STATUS, "",
                Collections.singletonList("active"), Collections.emptyList(), null, KAYA_STATUS));
        interactor.prepareEvent(event);
        Map<String, String> values = values(event);
        assertEquals("enrollment-1", values.get(HOUSEHOLD_ENROLLMENT_ID));
        assertEquals("moved", values.get(KAYA_STATUS));
        assertEquals("moved", values.get(KAYA_SABABU_YA_KUFUNGWA));
        assertEquals("1", values.get(KAYA_COUNTER_HAIJAPATIKANA));
        assertEquals(1, event.getObs().stream()
                .filter(obs -> KAYA_STATUS.equals(obs.getFieldCode())).count());
    }

    @Test
    public void editPreservesClinicalDateAndExcludesItsOwnOutcomeFromSnapshot() throws Exception {
        interactor.editingVisit = new Visit();
        interactor.editingVisit.setVisitId("edited-visit");
        JSONArray obs = new JSONArray().put(observation(MOTHERMENTOR_VISIT_DATE, "02-10-2026"))
                .put(observation(HOUSEHOLD_ENROLLMENT_ID, "enrollment-1"));
        interactor.editingVisit.setJson(new JSONObject().put("obs", obs).toString());
        initialize(true);
        status("repeat", "not_found", "house_closed");
        interactor.state = MotherMentorHouseholdClosure.active()
                .advance("repeat", "not_found", "house_closed", "2026-10-01");
        Event event = new Event();
        interactor.prepareEvent(event);
        Map<String, String> values = values(event);
        assertEquals("02-10-2026", values.get(MOTHERMENTOR_VISIT_DATE));
        assertEquals("2", values.get(KAYA_COUNTER_NYUMBA_IMEFUNGWA));
        assertEquals("edited-visit", interactor.excludedVisitId);
    }

    @Test
    public void legacyAnswersDoNotInventPartTwoClosureMetadata() throws Exception {
        initialize(false);
        Event event = new Event();
        interactor.prepareEvent(event);
        assertFalse(values(event).containsKey(HOUSEHOLD_ENROLLMENT_ID));
        assertFalse(values(event).containsKey(KAYA_STATUS));
    }

    @Test
    public void closedHouseholdBlocksStaleSubmissionBeforeSavingEvenForEdit() throws Exception {
        initialize(false);
        interactor.state = MotherMentorHouseholdClosure.active()
                .advance("repeat", "not_found", "moved", "2026-10-02");
        assertNull(interactor.submitVisit(true, "household", Collections.emptyMap(), ""));
        assertNull(interactor.excludedVisitId);
    }

    @Test
    public void reenrollmentAndHistoricalEditsBlockStaleSubmissionBeforeSaving() throws Exception {
        initialize(false);
        interactor.enrollmentId = "enrollment-2";
        assertNull(interactor.submitVisit(false, "household", Collections.emptyMap(), ""));
        interactor.enrollmentId = "enrollment-1";
        interactor.currentEpisode = false;
        assertNull(interactor.submitVisit(true, "household", Collections.emptyMap(), ""));
    }

    private void initialize(boolean editMode) {
        BaseMotherMentorVisitContract.View view = mock(BaseMotherMentorVisitContract.View.class);
        when(view.getEditMode()).thenReturn(editMode);
        MemberObject member = new MemberObject();
        member.setBaseEntityId("household");
        interactor.getDetailsOnEdit(view, member);
    }

    private void status(String type, String status, String reason) throws Exception {
        JSONArray fields = new JSONArray().put(field(HOUSEHOLD_FOLLOW_UP_SCHEMA, "2"))
                .put(field(HOUSEHOLD_VISIT_TYPE, type))
                .put(field(HOUSEHOLD_FOLLOW_UP_STATUS, status))
                .put(field(HOUSEHOLD_NOT_FOUND_REASON, reason));
        BaseMotherMentorVisitAction action = mock(BaseMotherMentorVisitAction.class);
        when(action.isValid()).thenReturn(true);
        when(action.getJsonPayload()).thenReturn(new JSONObject().put("count", 1)
                .put("step1", new JSONObject().put("fields", fields)).toString());
        LinkedHashMap<String, BaseMotherMentorVisitAction> actions =
                ReflectionHelpers.getField(interactor, "householdVisitActions");
        actions.put("status", action);
    }

    private JSONObject field(String key, String value) throws Exception {
        return new JSONObject().put("key", key).put("value", value);
    }

    private JSONObject observation(String key, String value) throws Exception {
        return new JSONObject().put("fieldCode", key).put("values", new JSONArray().put(value));
    }

    private Map<String, String> values(Event event) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Obs obs : event.getObs()) {
            result.put(obs.getFieldCode(), obs.getValues().get(0).toString());
        }
        return result;
    }

    private static class FixtureInteractor extends MotherMentorHouseholdVisitInteractor {
        String enrollmentId = "enrollment-1";
        String excludedVisitId;
        boolean currentEpisode = true;
        MotherMentorHouseholdClosure state = MotherMentorHouseholdClosure.active();
        Visit editingVisit;

        FixtureInteractor() {
            super(Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,
                    Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT);
        }

        @Override protected String loadHouseholdEnrollmentId(String memberId) { return enrollmentId; }
        @Override protected Visit loadClosureEditingVisit(String memberId) { return editingVisit; }
        @Override protected boolean isClosureVisitCurrentEnrollment(String memberId, String rawEvent) {
            return currentEpisode;
        }
        @Override protected MotherMentorHouseholdClosure loadClosureState(
                String memberId, String excludedId) {
            excludedVisitId = excludedId;
            return state;
        }
    }
}
