package org.smartregister.chw.mothermentor.interactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;

import com.google.gson.Gson;

import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.CoreLibrary;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorFollowUpType;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.clientandeventmodel.Obs;
import org.smartregister.domain.db.EventClient;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.sync.ClientProcessorForJava;
import org.smartregister.sync.helper.ECSyncHelper;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorIitVisitFinalizationTest {
    private MotherMentorLibrary previousLibrary;
    private CoreLibrary previousCore;
    private boolean previousAutoSubmit;
    private ECSyncHelper sync;
    private VisitRepository repository;

    @Before
    public void setUp() {
        previousLibrary = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        previousCore = ReflectionHelpers.getStaticField(CoreLibrary.class, "instance");
        previousAutoSubmit = MotherMentorLibrary.isSubmitOnSave();
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "submitOnSave", false);
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        CoreLibrary core = mock(CoreLibrary.class);
        org.smartregister.Context context = mock(org.smartregister.Context.class);
        sync = mock(ECSyncHelper.class);
        repository = mock(VisitRepository.class);
        when(library.context()).thenReturn(context);
        when(core.context()).thenReturn(context);
        when(context.applicationContext()).thenReturn(RuntimeEnvironment.getApplication());
        when(context.allSharedPreferences()).thenReturn(mock(AllSharedPreferences.class));
        when(library.getEcSyncHelper()).thenReturn(sync);
        when(library.getClientProcessorForJava()).thenReturn(mock(ClientProcessorForJava.class));
        when(library.visitRepository()).thenReturn(repository);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        ReflectionHelpers.setStaticField(CoreLibrary.class, "instance", core);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previousLibrary);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "submitOnSave", previousAutoSubmit);
        ReflectionHelpers.setStaticField(CoreLibrary.class, "instance", previousCore);
    }

    @Test
    public void topLevelSaveFinalizesDedicatedIitAndKeepsLegacyIitStaged() throws Exception {
        AtomicReference<EventClient> stored = new AtomicReference<>();
        doAnswer(call -> {
            JSONObject json = call.getArgument(1);
            stored.set(new EventClient(JsonFormUtils.gson.fromJson(json.toString(),
                    org.smartregister.domain.Event.class), null));
            return null;
        }).when(sync).addEvent(eq("current"), any(JSONObject.class));
        when(sync.getEvents(Collections.singletonList("current"))).thenAnswer(
                call -> Collections.singletonList(stored.get()));
        String eventType = MotherMentorFollowUpType.MOTHER_IIT.getEventType();
        MotherMentorFollowUpVisitInteractor legacy = new MotherMentorFollowUpVisitInteractor(eventType) {
            @Override
            protected Visit saveVisit(boolean edit, String member, String type,
                    Map<String, String> forms, String parent) {
                return pendingVisit("legacy");
            }
        };
        Map<String, BaseMotherMentorVisitAction> actions = Collections.emptyMap();
        assertEquals("current", iitSaving("current").submitVisit(false, "current", actions, ""));
        assertEquals("legacy", legacy.submitVisit(false, "legacy", actions, ""));
        verify(sync).addEvent(eq("current"), any(JSONObject.class));
        verify(repository).completeProcessing("current");
        verify(sync, never()).addEvent(eq("legacy"), any(JSONObject.class));
        verify(repository, never()).completeProcessing("legacy");
    }

    @Test
    public void failedSourceWriteCannotFinalizeIitDraft() {
        assertThrows(IllegalStateException.class, () -> iitSaving("failed").submitVisit(
                false, "failed", Collections.emptyMap(), ""));
        verify(sync).addEvent(eq("failed"), any(JSONObject.class));
        verify(repository, never()).completeProcessing("failed");
    }

    @Test
    public void newIitEventKeepsRecordedTimeAfterSameDayEnrollment() {
        MotherMentorIitVisitInteractor iit = new MotherMentorIitVisitInteractor();
        Event event = new Event();
        long beforeSubmit = System.currentTimeMillis();
        iit.prepareEvent(event);
        iit.assignVisitSource(event, null);
        assertTrue(event.getEventDate().getTime() >= beforeSubmit);
        assertEquals(LocalDate.now(), new LocalDate(event.getEventDate()));
        assertTrue(event.getFormSubmissionId().length() > 0);
    }

    @Test
    public void editedIitEventUsesSameSourceAndTimeWithANewerVersion() {
        MotherMentorIitVisitInteractor iit = new MotherMentorIitVisitInteractor();
        Event previous = new Event();
        previous.setEventDate(new Date(1730456554000L));
        previous.setEventId("server-id");
        previous.setVersion(12);
        Visit saved = new Visit();
        saved.setFormSubmissionId("saved-fsid");
        saved.setJson(JsonFormUtils.gson.toJson(previous));

        Event edit = new Event();
        edit.setVersion(11);
        iit.prepareEvent(edit);
        iit.assignVisitSource(edit, saved);
        assertEquals("saved-fsid", edit.getFormSubmissionId());
        assertEquals("server-id", edit.getEventId());
        assertEquals(previous.getEventDate(), edit.getEventDate());
        assertEquals(13L, edit.getVersion());

        Event legacy = new Event();
        new MotherMentorFollowUpVisitInteractor(MotherMentorFollowUpType.MOTHER_IIT.getEventType())
                .assignVisitSource(legacy, saved);
        assertNotEquals("saved-fsid", legacy.getFormSubmissionId());
    }

    private static MotherMentorIitVisitInteractor iitSaving(String id) {
        return new MotherMentorIitVisitInteractor() {
            @Override
            protected Visit saveVisit(boolean edit, String member, String type,
                    Map<String, String> forms, String parent) {
                return pendingVisit(id);
            }
        };
    }

    private static Visit pendingVisit(String id) {
        Event event = new Event();
        event.setBaseEntityId(id);
        event.setFormSubmissionId(id);
        event.setEventType(MotherMentorFollowUpType.MOTHER_IIT.getEventType());
        if (!"legacy".equals(id)) {
            event.addObs(new Obs("concept", "text", DBConstants.KEY.IIT_FOLLOW_UP_SCHEMA, "",
                    Collections.singletonList("2"), Collections.emptyList(), null,
                    DBConstants.KEY.IIT_FOLLOW_UP_SCHEMA));
        }
        Visit visit = new Visit();
        visit.setVisitId(id);
        visit.setProcessed(false);
        visit.setJson(id);
        visit.setPreProcessedJson(new Gson().toJson(event));
        return visit;
    }
}
