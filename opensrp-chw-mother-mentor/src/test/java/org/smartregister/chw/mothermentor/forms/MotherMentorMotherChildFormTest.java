package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;

import android.app.Application;

import com.vijay.jsonwizard.utils.FormUtils;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherChildFormTest {
    @Test
    public void childStatusAndSerialTestQuestionsExistInBothLanguages() throws Exception {
        for (String directory : new String[] {"json.form", "json.form-sw"}) {
            try (Scanner scanner = new Scanner(RuntimeEnvironment.getApplication().getAssets()
                    .open(directory + "/mothermentor_visit_type.json"), "UTF-8")) {
                JSONObject form = new JSONObject(scanner.useDelimiter("\\A").next());
                for (String key : new String[] {"mama_child_status", "mama_breastfeeding_status",
                        "mama_hei_test_done", "mama_hei_test_type", "mama_hei_test_result",
                        "hei_chanya_imethibitishwa"}) {
                    assertEquals(directory + ":" + key, key, FormUtils.getFieldFromForm(form, key).optString("key"));
                }
            }
        }
    }
}
