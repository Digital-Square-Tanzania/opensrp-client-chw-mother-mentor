package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;

import android.app.Application;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;

import com.vijay.jsonwizard.R;
import com.vijay.jsonwizard.activities.JsonFormActivity;
import com.vijay.jsonwizard.widgets.NativeRadioButtonFactory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorPartnerHivHistoryTest {
    @Test
    public void nativeRelevanceHidesUnneededResultsAndClearsChangedAnswersInBothLocales()
            throws Exception {
        for (String locale : new String[] {"json.form", "json.form-sw"}) {
            HistoryForm fixture = new HistoryForm(form(locale));
            fixture.refresh();
            fixture.assertHidden(DBConstants.KEY.TEST_RESULT);
            assertTrue(fixture.resultValid());

            fixture.answer(DBConstants.KEY.HIV_TESTED, "no");
            fixture.refresh();
            fixture.assertHidden(DBConstants.KEY.TEST_RESULT);
            assertTrue(fixture.resultValid());

            fixture.answer(DBConstants.KEY.HIV_TESTED, "yes");
            fixture.refresh();
            fixture.assertShown(DBConstants.KEY.TEST_RESULT);
            assertFalse("A displayed required result must be answered", fixture.resultValid());

            fixture.answer(DBConstants.KEY.TEST_RESULT, "positive");
            fixture.answer(DBConstants.KEY.ON_CTC, "yes");
            fixture.answer(DBConstants.KEY.PARTNER_CTC, "12-34-5678-123456");
            fixture.refresh();
            fixture.assertShown(DBConstants.KEY.TEST_RESULT);
            fixture.assertShown(DBConstants.KEY.ON_CTC);
            fixture.assertShown(DBConstants.KEY.PARTNER_CTC);
            assertTrue(fixture.resultValid());

            fixture.answer(DBConstants.KEY.HIV_TESTED, "no");
            fixture.refresh();
            for (String key : fixture.views.keySet()) {
                fixture.assertHidden(key);
                assertFalse(
                        "Hidden answer must not survive submission",
                        fixture.fields.get(key).has("value"));
            }
            assertFalse(
                    ((RadioButton) fixture.views.get(DBConstants.KEY.TEST_RESULT)
                                    .findViewWithTag("positive"))
                            .isChecked());
            assertEquals(
                    "",
                    ((EditText) fixture.views.get(DBConstants.KEY.PARTNER_CTC))
                            .getText()
                            .toString());
            assertTrue("Hidden required result must not block validation", fixture.resultValid());

            fixture.answer(DBConstants.KEY.HIV_TESTED, "yes");
            fixture.refresh();
            fixture.assertShown(DBConstants.KEY.TEST_RESULT);
            fixture.assertHidden(DBConstants.KEY.ON_CTC);
            fixture.assertHidden(DBConstants.KEY.PARTNER_CTC);
            assertFalse("Returning to Yes must require a fresh result", fixture.resultValid());
        }
    }

    private JSONObject form(String locale) throws Exception {
        try (Scanner scanner =
                new Scanner(
                        RuntimeEnvironment.application
                                .getAssets()
                                .open(locale + "/mothermentor_enroll_partner.json"),
                        "UTF-8")) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }

    private static class HistoryForm {
        private final TestFormActivity activity =
                Robolectric.buildActivity(TestFormActivity.class).get();
        private final Map<String, JSONObject> fields = new LinkedHashMap<>();
        private final Map<String, View> views = new LinkedHashMap<>();

        private HistoryForm(JSONObject form) throws Exception {
            activity.load(form);
            JSONArray history = form.getJSONObject("step3").getJSONArray("fields");
            LinearLayout root = new LinearLayout(activity);
            for (int i = 0; i < history.length(); i++) {
                JSONObject field = history.getJSONObject(i);
                String key = field.getString("key");
                fields.put(key, field);
                activity.getFormFieldsMap().put("step3_" + key, field);
                if (!field.has("relevance")) continue;

                View view;
                if (field.has("options")) {
                    RadioGroup group = new RadioGroup(activity);
                    for (int option = 0; option < field.getJSONArray("options").length(); option++) {
                        RadioButton button = new RadioButton(activity);
                        button.setId(View.generateViewId());
                        button.setTag(
                                field.getJSONArray("options")
                                        .getJSONObject(option)
                                        .getString("key"));
                        // Match the native radio factory's option layout for real validation.
                        LinearLayout content = new LinearLayout(activity);
                        content.addView(button);
                        LinearLayout main = new LinearLayout(activity);
                        main.addView(content);
                        RelativeLayout optionRoot = new RelativeLayout(activity);
                        optionRoot.addView(main);
                        group.addView(optionRoot);
                    }
                    group.setTag(R.id.error, field.getJSONObject("v_required").getString("err"));
                    view = group;
                } else {
                    view = new EditText(activity);
                }
                view.setId(View.generateViewId());
                view.setTag(R.id.address, "step3:" + key);
                view.setTag(R.id.key, key);
                view.setTag(R.id.type, field.getString("type"));
                view.setTag(R.id.extraPopup, false);
                view.setTag(R.id.relevance, field.getJSONObject("relevance").toString());
                LinearLayout container = new LinearLayout(activity);
                container.setId(View.generateViewId());
                container.addView(view);
                root.addView(container);
                view.setTag(R.id.canvas_ids, new JSONArray().put(container.getId()).toString());
                views.put(key, view);
            }
        }

        private void answer(String key, String value) throws Exception {
            JSONObject field = fields.get(key);
            field.put("value", value);
            View view = views.get(key);
            if (view instanceof RadioGroup) {
                JSONArray options = field.getJSONArray("options");
                for (int i = 0; i < options.length(); i++) {
                    String option = options.getJSONObject(i).getString("key");
                    ((RadioButton) view.findViewWithTag(option)).setChecked(value.equals(option));
                }
            } else if (view instanceof EditText) {
                ((EditText) view).setText(value);
            }
        }

        private void refresh() {
            // Apply native relevance to the fields in their form dependency order.
            for (View view : views.values()) activity.evaluateRelevance(view);
        }

        private void assertHidden(String key) {
            assertEquals(key, View.GONE, ((View) views.get(key).getParent()).getVisibility());
            assertFalse(key, views.get(key).isEnabled());
        }

        private void assertShown(String key) {
            assertEquals(key, View.VISIBLE, ((View) views.get(key).getParent()).getVisibility());
            assertTrue(key, views.get(key).isEnabled());
        }

        private boolean resultValid() {
            return NativeRadioButtonFactory.isValid(
                    (RadioGroup) views.get(DBConstants.KEY.TEST_RESULT));
        }
    }

    public static class TestFormActivity extends JsonFormActivity {
        private void load(JSONObject form) {
            mJSONObject = form;
            skipLogicViews = new LinkedHashMap<>();
            refreshSkipLogic(null, null, false, "step3", false);
        }

        private void evaluateRelevance(View view) {
            addRelevance(view, false, false);
        }
    }
}
