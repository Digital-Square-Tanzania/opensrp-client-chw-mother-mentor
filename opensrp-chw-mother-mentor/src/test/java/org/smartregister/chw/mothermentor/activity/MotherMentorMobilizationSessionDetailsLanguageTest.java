package org.smartregister.chw.mothermentor.activity;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.dao.AbstractDao;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.repository.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMobilizationSessionDetailsLanguageTest {
    private static final String SESSION = "session-1114";
    private static final String TABLE = Constants.TABLES.MOTHERMENTOR_MOBILIZATION;
    private static final String DATE = "27-10-2026";
    private static final String COMMENT = "Shughuli ilikuwa nzuri";
    private SQLiteDatabase data;
    private Repository previousRepository;

    @Before
    public void setUp() {
        previousRepository = ReflectionHelpers.getStaticField(AbstractDao.class, "repository");
        data = SQLiteDatabase.create(null);
        String[] columns = {
            DBConstants.KEY.ID, DBConstants.KEY.BASE_ENTITY_ID, DBConstants.KEY.SH_TAREHE,
            DBConstants.KEY.SH_AINA, DBConstants.KEY.SH_AINA_OTHER, DBConstants.KEY.GPS,
            DBConstants.KEY.SH_MADA, DBConstants.KEY.SH_MADA_OTHER, DBConstants.KEY.ELIM_M,
            DBConstants.KEY.ELIM_F, DBConstants.KEY.ELIM_TOTAL, DBConstants.KEY.MAONI,
            DBConstants.KEY.REFERRED_MALE, DBConstants.KEY.REFERRED_FEMALE,
            DBConstants.KEY.REFERRED_TOTAL, DBConstants.KEY.FACILITY_REFERRAL_SERVICES,
            DBConstants.KEY.FACILITY_REFERRAL_OTHER
        };
        List<String> definitions = new ArrayList<>();
        for (String column : columns) definitions.add(column + " TEXT");
        data.execSQL("CREATE TABLE " + TABLE + " (" + String.join(",", definitions) + ")");
        ContentValues row = new ContentValues();
        row.put(DBConstants.KEY.ID, SESSION);
        row.put(DBConstants.KEY.BASE_ENTITY_ID, SESSION);
        row.put(DBConstants.KEY.SH_TAREHE, DATE);
        row.put(DBConstants.KEY.SH_AINA, "kampeni");
        row.put(DBConstants.KEY.SH_AINA_OTHER, "Other activity entered by the user");
        row.put(DBConstants.KEY.GPS, "-3.5, 37.2");
        row.put(DBConstants.KEY.SH_MADA,
                "[pmtct, hiv, syphilis, hepb, fp, nutrition, tb, gbv, nyingine]");
        row.put(DBConstants.KEY.SH_MADA_OTHER, "Mada iliyoandikwa na mtumiaji");
        row.put(DBConstants.KEY.ELIM_M, "2");
        row.put(DBConstants.KEY.ELIM_F, "5");
        row.put(DBConstants.KEY.ELIM_TOTAL, "7");
        row.put(DBConstants.KEY.REFERRED_MALE, "1");
        row.put(DBConstants.KEY.REFERRED_FEMALE, "2");
        row.put(DBConstants.KEY.REFERRED_TOTAL, "3");
        row.put(DBConstants.KEY.FACILITY_REFERRAL_SERVICES,
                "[hiv_testing, syphilis_testing, hepatitis_b_testing, anc, ctc, gbv, other]");
        row.put(DBConstants.KEY.FACILITY_REFERRAL_OTHER, "Huduma iliyoandikwa na mtumiaji");
        row.put(DBConstants.KEY.MAONI, COMMENT);
        data.insertOrThrow(TABLE, null, row);

        Repository repository = mock(Repository.class);
        net.zetetic.database.sqlcipher.SQLiteDatabase cipher =
                mock(net.zetetic.database.sqlcipher.SQLiteDatabase.class);
        when(repository.getReadableDatabase()).thenReturn(cipher);
        when(cipher.rawQuery(anyString(), any(String[].class)))
                .thenAnswer(call -> data.rawQuery(call.getArgument(0), call.getArgument(1)));
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", repository);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(AbstractDao.class, "repository", previousRepository);
        data.close();
    }

    @Test
    public void selectedSwahiliRendersEveryDetailOnAnEnglishDevice() {
        RuntimeEnvironment.setQualifiers("en-rUS");
        assertSession("sw");
    }

    @Test
    public void selectedEnglishRendersEveryDetailOnASwahiliDevice() {
        RuntimeEnvironment.setQualifiers("sw");
        assertSession("en");
    }

    @Test
    public void reopeningTheSameSavedSessionUsesTheLatestLanguagePreference() {
        RuntimeEnvironment.setQualifiers("en-rUS");
        for (String language : new String[] {"en", "sw", "en"}) assertSession(language);
        try (Cursor sessions = data.rawQuery("SELECT * FROM " + TABLE, null)) {
            assertEquals(1, sessions.getCount());
        }
    }

    @Test
    public void missingSessionUsesTheSelectedLanguageForTitleAndEmptyState() {
        RuntimeEnvironment.setQualifiers("en-rUS");
        selectLanguage("sw");
        try (ActivityController<MotherMentorMobilizationSessionDetailsActivity> controller =
                activity("missing-session")) {
            Context expected = localized("sw");
            assertEquals(Arrays.asList("<",
                    expected.getString(R.string.mothermentor_mobilization_session_details),
                    expected.getString(R.string.no_record)), text(controller.get()));
        }
    }

    private void assertSession(String language) {
        selectLanguage(language);
        Context expected = localized(language);
        try (ActivityController<MotherMentorMobilizationSessionDetailsActivity> controller =
                activity(SESSION)) {
            MotherMentorMobilizationSessionDetailsActivity activity = controller.get();
            assertEquals(language, activity.getResources().getConfiguration().locale.getLanguage());
            List<String> values = new ArrayList<>();
            values.add("<");
            values.add(expected.getString(R.string.mothermentor_mobilization_session_details));
            detail(values, expected, R.string.mothermentor_activity_date, DATE);
            detail(values, expected, R.string.mothermentor_activity_type,
                    expected.getString(R.string.mothermentor_activity_type_kampeni));
            detail(values, expected, R.string.mothermentor_activity_type_other,
                    "Other activity entered by the user");
            detail(values, expected, R.string.mothermentor_recorded_location, "-3.5, 37.2");
            detail(values, expected, R.string.mothermentor_education_topics_provided,
                    labels(expected, R.string.mothermentor_topic_pmtct,
                            R.string.mothermentor_topic_hiv, R.string.mothermentor_topic_syphilis,
                            R.string.mothermentor_topic_hepb, R.string.mothermentor_topic_fp,
                            R.string.mothermentor_topic_nutrition, R.string.mothermentor_topic_tb,
                            R.string.mothermentor_topic_gbv, R.string.mothermentor_other_specify));
            detail(values, expected, R.string.mothermentor_education_topics_other,
                    "Mada iliyoandikwa na mtumiaji");
            detail(values, expected, R.string.mothermentor_men_educated, "2");
            detail(values, expected, R.string.mothermentor_women_educated, "5");
            detail(values, expected, R.string.mothermentor_total_educated, "7");
            detail(values, expected, R.string.mothermentor_referred_male, "1");
            detail(values, expected, R.string.mothermentor_referred_female, "2");
            detail(values, expected, R.string.mothermentor_referred_total, "3");
            detail(values, expected, R.string.mothermentor_facility_referral_services,
                    labels(expected, R.string.mothermentor_referral_hiv_testing,
                            R.string.mothermentor_referral_syphilis_testing,
                            R.string.mothermentor_referral_hepatitis_b_testing,
                            R.string.mothermentor_referral_anc, R.string.mothermentor_referral_ctc,
                            R.string.mothermentor_referral_gbv, R.string.mothermentor_referral_other));
            detail(values, expected, R.string.mothermentor_facility_referral_other,
                    "Huduma iliyoandikwa na mtumiaji");
            detail(values, expected, R.string.mothermentor_comments, COMMENT);
            assertEquals(values, text(activity));
        }
    }

    private ActivityController<MotherMentorMobilizationSessionDetailsActivity> activity(String id) {
        Context context = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(context, MotherMentorMobilizationSessionDetailsActivity.class)
                .putExtra("session_id", id);
        return Robolectric.buildActivity(MotherMentorMobilizationSessionDetailsActivity.class, intent)
                .setup();
    }

    private void selectLanguage(String language) {
        // Save only the preference; LangUtils.saveLanguage also changes resources and masks the bug.
        new AllSharedPreferences(PreferenceManager.getDefaultSharedPreferences(
                RuntimeEnvironment.getApplication())).saveLanguagePreference(language);
    }

    private Context localized(String language) {
        Context context = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(new Locale(language));
        return context.createConfigurationContext(configuration);
    }

    private void detail(List<String> values, Context context, int label, String value) {
        values.add(context.getString(label));
        values.add(value);
    }

    private String labels(Context context, int... resources) {
        List<String> values = new ArrayList<>();
        for (int resource : resources) values.add(context.getString(resource));
        return String.join("\n", values);
    }

    private List<String> text(MotherMentorMobilizationSessionDetailsActivity activity) {
        List<String> values = new ArrayList<>();
        collectText(activity.findViewById(android.R.id.content), values);
        return values;
    }

    private void collectText(View view, List<String> values) {
        if (view instanceof TextView) values.add(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), values);
        }
    }
}
