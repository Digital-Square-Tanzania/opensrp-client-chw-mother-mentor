package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.clientandeventmodel.Event;
import org.smartregister.repository.AllSharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class MotherMentorAttendanceEventTest {
    @Test
    public void attendanceContextFromGroupLinkageIsNotPersistedAsASecondAnswer() throws Exception {
        MotherMentorLibrary previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", mock(MotherMentorLibrary.class));
        try {
            for (String directory : new String[] {"json.form", "json.form-sw"}) {
                Map<String, String> forms = new LinkedHashMap<>();
                forms.put("visit", answeredForm(directory, "mothermentor_visit_type"));
                forms.put("groups", answeredForm(directory, "mothermentor_visit_group_linkage"));
                Event event = MotherMentorJsonFormUtils.processVisitJsonForm(mock(AllSharedPreferences.class),
                        "mother", Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, forms,
                        Constants.TABLES.MOTHERMENTOR_ENROLLMENT);
                assertNotNull(event);
                assertEquals(1, event.getObs().stream()
                        .filter(obs -> DBConstants.KEY.ATTENDANCE_TYPE.equals(obs.getFieldCode())).count());
            }
        } finally {
            ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
        }
    }

    private static String answeredForm(String directory, String name) throws Exception {
        try (Scanner scanner = new Scanner(RuntimeEnvironment.application.getAssets()
                .open(directory + "/" + name + ".json"), "UTF-8").useDelimiter("\\A")) {
            JSONObject form = new JSONObject(scanner.next());
            JSONArray fields = form.getJSONObject("step1").getJSONArray("fields");
            for (int i = 0; i < fields.length(); i++) {
                JSONObject field = fields.getJSONObject(i);
                if (DBConstants.KEY.ATTENDANCE_TYPE.equals(field.optString("key"))) field.put("value", "repeat");
            }
            return form.toString();
        }
    }
}
