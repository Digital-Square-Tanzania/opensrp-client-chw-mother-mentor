package org.smartregister.chw.mothermentor.actionhelper;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.*;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;

import com.vijay.jsonwizard.utils.FormUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.repository.Repository;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherEntryFormTest {
    @Test
    public void bothLanguagesHaveOnlyFoundStatusAndTheFourNestedReasons() throws Exception {
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            JSONObject form = form(language, "mothermentor_visit_type");
            assertEquals(
                    Arrays.asList("found", "not_found"), options(field(form, FOLLOW_UP_STATUS)));
            assertEquals(Arrays.asList("deceased", "moved_area", "moved_facility", "other"),
                    options(field(form, MAMA_NOT_FOUND_REASON)));
            assertEquals(0, FormUtils.getFieldFromForm(form, NEW_PMTCT_ART_INITIATION).length());
            assertFalse(field(form, MAMA_KITUO_ANACHOHAMIA).has("v_required"));
            assertTrue(
                    field(form, MAMA_KITUO_KIPYA).getJSONObject("v_required").getBoolean("value"));
            assertEquals("2", field(form, MAMA_VISIT_SCHEMA).getString("value"));
            JSONObject group = form(language, "mothermentor_visit_group_linkage");
            assertNotNull(field(group, NEW_PMTCT_ART_INITIATION));
            assertEquals("today", field(group, VISIT_ART_START_DATE).getString("max_date"));
        }
    }

    @Test
    public void eachNotFoundReasonRequiresOnlyItsApplicableDetails() throws Exception {
        for (String language : new String[] {"json.form", "json.form-sw"}) {
            MotherMentorVisitTypeActionHelper helper = new MotherMentorVisitTypeActionHelper(null);
            JSONObject form = form(language, "mothermentor_visit_type");
            set(form, ATTENDANCE_TYPE, "repeat");
            set(form, FOLLOW_UP_STATUS, "not_found");
            helper.onPayloadReceived(form.toString());
            assertEquals(
                    BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            set(form, MAMA_NOT_FOUND_REASON, "other");
            helper.onPayloadReceived(form.toString());
            assertEquals(
                    BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            set(form, MAMA_SABABU_NYINGINE_TEXT, "Return tomorrow");
            helper.onPayloadReceived(form.toString());
            assertEquals(
                    BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            set(form, MAMA_NOT_FOUND_REASON, "moved_facility");
            helper.onPayloadReceived(form.toString());
            assertEquals(
                    BaseMotherMentorVisitAction.Status.PENDING, helper.evaluateStatusOnPayload());
            set(form, MAMA_KITUO_KIPYA, "Hospital A");
            helper.onPayloadReceived(form.toString());
            assertEquals(
                    BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            assertEquals("",
                    value(new JSONObject(helper.postProcess(form.toString())),
                            MAMA_SABABU_NYINGINE_TEXT));
            set(form, MAMA_NOT_FOUND_REASON, "moved_area");
            set(form, MAMA_KITUO_KIPYA, "");
            helper.onPayloadReceived(form.toString());
            assertEquals(
                    BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
            set(form, MAMA_KITUO_ANACHOHAMIA, "Optional hospital");
            helper.onPayloadReceived(form.toString());
            assertEquals("Optional hospital",
                    value(new JSONObject(helper.postProcess(form.toString())), MAMA_KITUO_KIPYA));
            set(form, FOLLOW_UP_STATUS, "found");
            helper.onPayloadReceived(form.toString());
            JSONObject found = new JSONObject(helper.postProcess(form.toString()));
            for (String cleared : new String[] {MAMA_NOT_FOUND_REASON, MAMA_SABABU_NYINGINE_TEXT,
                         MAMA_KITUO_KIPYA, MAMA_KITUO_ANACHOHAMIA})
                assertEquals("", value(found, cleared));
            assertEquals("2", value(found, MAMA_VISIT_SCHEMA));
        }
    }

    @Test
    public void newEntryClearsFollowUpAnswersButRetainsEnrollmentMetadata() throws Exception {
        JSONObject form = form("json.form", "mothermentor_visit_type");
        set(form, ATTENDANCE_TYPE, "new");
        set(form, FOLLOW_UP_STATUS, "not_found");
        set(form, MAMA_NOT_FOUND_REASON, "deceased");
        set(form, MAMA_ENROLLMENT_ID, "enrollment-one");
        MotherMentorVisitTypeActionHelper helper = new MotherMentorVisitTypeActionHelper(null);
        helper.onPayloadReceived(form.toString());
        JSONObject saved = new JSONObject(helper.postProcess(form.toString()));
        assertEquals("", value(saved, FOLLOW_UP_STATUS));
        assertEquals("", value(saved, MAMA_NOT_FOUND_REASON));
        assertEquals("enrollment-one", value(saved, MAMA_ENROLLMENT_ID));
        assertEquals(
                BaseMotherMentorVisitAction.Status.COMPLETED, helper.evaluateStatusOnPayload());
    }

    @Test
    public void groupArtQuestionIsNewOnlyAndSkipsPreviouslyInitiatedClients() throws Exception {
        MotherMentorLibrary previous =
                ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        SQLiteDatabase data = SQLiteDatabase.create(null);
        data.execSQL("CREATE TABLE visits (visit_id TEXT, base_entity_id TEXT, visit_type TEXT, "
                     + "visit_date TEXT)");
        data.execSQL("CREATE TABLE visit_details (visit_id TEXT, visit_key TEXT, details TEXT)");
        data.execSQL("CREATE TABLE ec_mothermentor_enrollment (base_entity_id TEXT, art_status "
                     + "TEXT, art_start_date TEXT)");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(library.getRepository()).thenReturn(repository);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        try {
            Map<String, String> entry = new HashMap<>();
            entry.put(ATTENDANCE_TYPE, "new");
            MemberObject member = new MemberObject();
            member.setBaseEntityId("mother");
            for (String language : new String[] {"json.form", "json.form-sw"}) {
                MotherMentorGroupLinkageActionHelper helper =
                        new MotherMentorGroupLinkageActionHelper(member, () -> entry);
                JSONObject form = form(language, "mothermentor_visit_group_linkage");
                helper.onJsonFormLoaded(
                        form.toString(), RuntimeEnvironment.application, Collections.emptyMap());
                JSONObject prepared = new JSONObject(helper.getPreProcessed());
                assertEquals("new", value(prepared, ATTENDANCE_TYPE));
                assertEquals("false", value(prepared, ART_INITIATED_BEFORE_VISIT));
                set(prepared, HAS_BEEN_LINKED_TO_IGA_GROUP, "no");
                set(prepared, HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP, "no");
                helper.onPayloadReceived(prepared.toString());
                assertEquals(BaseMotherMentorVisitAction.Status.PENDING,
                        helper.evaluateStatusOnPayload());
                set(prepared, NEW_PMTCT_ART_INITIATION, "no");
                helper.onPayloadReceived(prepared.toString());
                assertEquals(BaseMotherMentorVisitAction.Status.COMPLETED,
                        helper.evaluateStatusOnPayload());
                set(prepared, ATTENDANCE_TYPE, "repeat");
                set(prepared, NEW_PMTCT_ART_INITIATION, "yes");
                set(prepared, VISIT_ART_START_DATE, "01-11-2026");
                entry.put(ATTENDANCE_TYPE, "repeat");
                helper.onJsonFormLoaded(prepared.toString(), RuntimeEnvironment.application,
                        Collections.emptyMap());
                JSONObject repeated = new JSONObject(helper.getPreProcessed());
                assertEquals("", value(repeated, NEW_PMTCT_ART_INITIATION));
                assertEquals("", value(repeated, VISIT_ART_START_DATE));
                assertTrue(MotherMentorMotherVisitUtils.isArtComplete(
                        MotherMentorMotherVisitUtils.formValues(repeated)));
                entry.put(ATTENDANCE_TYPE, "new");
            }
            data.execSQL("INSERT INTO ec_mothermentor_enrollment VALUES ('mother','yes','')");
            MotherMentorGroupLinkageActionHelper prior =
                    new MotherMentorGroupLinkageActionHelper(member, () -> entry);
            prior.onJsonFormLoaded(form("json.form", "mothermentor_visit_group_linkage").toString(),
                    RuntimeEnvironment.application, Collections.emptyMap());
            assertEquals("true",
                    value(new JSONObject(prior.getPreProcessed()), ART_INITIATED_BEFORE_VISIT));
        } finally {
            data.close();
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }

    private JSONObject form(String directory, String name) throws Exception {
        try (Scanner input = new Scanner(RuntimeEnvironment.application.getAssets().open(
                                                 directory + "/" + name + ".json"),
                     "UTF-8")
                        .useDelimiter("\\A")) {
            return new JSONObject(input.next());
        }
    }

    private List<String> options(JSONObject field) throws Exception {
        List<String> values = new ArrayList<>();
        JSONArray options = field.getJSONArray("options");
        for (int i = 0; i < options.length(); i++)
            values.add(options.getJSONObject(i).getString("key"));
        return values;
    }

    private JSONObject field(JSONObject form, String key) throws Exception {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        assertNotNull(key, field);
        return field;
    }
    private void set(JSONObject form, String key, String value) throws Exception {
        field(form, key).put("value", value);
    }
    private String value(JSONObject form, String key) throws Exception {
        return field(form, key).optString("value");
    }
}
