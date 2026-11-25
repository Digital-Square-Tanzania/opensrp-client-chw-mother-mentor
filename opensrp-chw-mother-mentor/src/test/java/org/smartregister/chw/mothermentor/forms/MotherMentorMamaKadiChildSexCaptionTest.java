package org.smartregister.chw.mothermentor.forms;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MotherMentorMamaKadiChildSexCaptionTest {
    @Test
    public void englishCaptionsKeepTheStoredKeysAndConcepts() throws IOException {
        assertChildSexOptions("json.form", "Male", "Female");
    }

    @Test
    public void kiswahiliCaptionsKeepTheStoredKeysAndConcepts() throws IOException {
        assertChildSexOptions("json.form-sw", "Wa kiume", "Wa kike");
    }

    private static void assertChildSexOptions(String directory, String maleCaption, String femaleCaption)
            throws IOException {
        try (Reader reader = Files.newBufferedReader(
                Paths.get("src/main/assets", directory, "mothermentor_visit_child.json"),
                StandardCharsets.UTF_8)) {
            JsonObject form = new Gson().fromJson(reader, JsonObject.class);
            JsonArray fields = form.getAsJsonObject("step1").getAsJsonArray("fields");
            for (JsonElement element : fields) {
                JsonObject field = element.getAsJsonObject();
                if ("mama_child_sex".equals(field.get("key").getAsString())) {
                    assertEquals("native_radio", field.get("type").getAsString());
                    assertEquals("concept", field.get("openmrs_entity").getAsString());
                    assertEquals("mama_child_sex", field.get("openmrs_entity_id").getAsString());
                    assertEquals("", field.get("openmrs_entity_parent").getAsString());
                    JsonArray options = field.getAsJsonArray("options");
                    assertEquals(2, options.size());
                    assertOption(options.get(0).getAsJsonObject(), "male", maleCaption);
                    assertOption(options.get(1).getAsJsonObject(), "female", femaleCaption);
                    return;
                }
            }
            throw new AssertionError("Missing mama_child_sex in " + directory);
        }
    }

    private static void assertOption(JsonObject option, String key, String caption) {
        assertEquals(key, option.get("key").getAsString());
        assertEquals("concept", option.get("openmrs_entity").getAsString());
        assertEquals(key, option.get("openmrs_entity_id").getAsString());
        assertEquals("", option.get("openmrs_entity_parent").getAsString());
        assertEquals(caption, option.get("text").getAsString());
    }
}
