package org.smartregister.chw.mothermentor.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.dao.MotherMentorMobilizationDao;
import org.smartregister.chw.mothermentor.model.MotherMentorMobilizationModel;

import java.util.ArrayList;
import java.util.List;

public class MotherMentorMobilizationSessionDetailsActivity extends Activity {
    private static final String EXTRA_SESSION_ID = "session_id";

    public static void startMe(Activity activity, String sessionId) {
        Intent intent = new Intent(activity, MotherMentorMobilizationSessionDetailsActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        MotherMentorMobilizationModel session = StringUtils.isBlank(sessionId) ? null : MotherMentorMobilizationDao.getMobilizationSession(sessionId);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        root.addView(createHeader());

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        if (session == null) {
            TextView empty = text(getString(R.string.no_record), 16, Color.DKGRAY, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            content.addView(empty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            addDetail(content, R.string.mothermentor_activity_date, session.getSessionDate());
            addDetail(content, R.string.mothermentor_activity_type, translateActivityType(session.getActivityType()));
            addDetail(content, R.string.mothermentor_activity_type_other, session.getActivityTypeOther());
            addDetail(content, R.string.mothermentor_recorded_location, session.getGps());
            addDetail(content, R.string.mothermentor_education_topics_provided, translateEducationTopics(session.getEducationTopics()));
            addDetail(content, R.string.mothermentor_education_topics_other, session.getEducationTopicsOther());
            addDetail(content, R.string.mothermentor_men_educated, session.getMenEducated());
            addDetail(content, R.string.mothermentor_women_educated, session.getWomenEducated());
            addDetail(content, R.string.mothermentor_total_educated, StringUtils.defaultIfBlank(session.getTotalEducated(), session.getSessionParticipants()));
            addDetail(content, R.string.mothermentor_referred_male, session.getReferredMen());
            addDetail(content, R.string.mothermentor_referred_female, session.getReferredWomen());
            addDetail(content, R.string.mothermentor_referred_total, session.getReferredTotal());
            addDetail(content, R.string.mothermentor_facility_referral_services, translateReferralServices(session.getReferralServices()));
            addDetail(content, R.string.mothermentor_facility_referral_other, session.getReferralOther());
            addDetail(content, R.string.mothermentor_comments, session.getComments());
        }

        setContentView(root);
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(16), 0);
        header.setBackgroundColor(Color.rgb(48, 56, 60));

        TextView back = text("<", 30, Color.WHITE, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));

        TextView title = text(getString(R.string.mothermentor_mobilization_session_details), 18, Color.WHITE, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        return header;
    }

    private void addDetail(LinearLayout parent, int labelRes, String value) {
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value.trim())) {
            return;
        }

        TextView label = text(getString(labelRes), 13, Color.rgb(80, 80, 80), Typeface.BOLD);
        label.setPadding(0, dp(10), 0, dp(2));
        parent.addView(label);

        TextView detail = text(value, 16, Color.BLACK, Typeface.NORMAL);
        detail.setPadding(0, 0, 0, dp(8));
        parent.addView(detail);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setLineSpacing(0, 1.1f);
        return textView;
    }

    private String translateActivityType(String value) {
        if ("baraza".equals(value)) return getString(R.string.mothermentor_activity_type_baraza);
        if ("anc".equals(value)) return getString(R.string.mothermentor_activity_type_anc);
        if ("dini".equals(value)) return getString(R.string.mothermentor_activity_type_dini);
        if ("kampeni".equals(value)) return getString(R.string.mothermentor_activity_type_kampeni);
        if ("shule".equals(value)) return getString(R.string.mothermentor_activity_type_shule);
        if ("nyingine".equals(value)) return getString(R.string.mothermentor_other_specify);
        return value;
    }

    private String translateEducationTopics(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        String cleaned = value.replace("[", "").replace("]", "");
        String[] keys = cleaned.split(",");
        List<String> labels = new ArrayList<>();
        for (String rawKey : keys) {
            String key = rawKey.trim();
            if (StringUtils.isNotBlank(key)) {
                labels.add(translateEducationTopic(key));
            }
        }
        return StringUtils.join(labels, "\n");
    }

    private String translateEducationTopic(String value) {
        if ("pmtct".equals(value)) return getString(R.string.mothermentor_topic_pmtct);
        if ("hiv".equals(value)) return getString(R.string.mothermentor_topic_hiv);
        if ("syphilis".equals(value)) return getString(R.string.mothermentor_topic_syphilis);
        if ("hepb".equals(value)) return getString(R.string.mothermentor_topic_hepb);
        if ("fp".equals(value)) return getString(R.string.mothermentor_topic_fp);
        if ("nutrition".equals(value)) return getString(R.string.mothermentor_topic_nutrition);
        if ("tb".equals(value)) return getString(R.string.mothermentor_topic_tb);
        if ("gbv".equals(value)) return getString(R.string.mothermentor_topic_gbv);
        if ("nyingine".equals(value)) return getString(R.string.mothermentor_other_specify);
        return value;
    }

    private String translateReferralServices(String value) {
        if (StringUtils.isBlank(value)) return value;
        List<String> labels = new ArrayList<>();
        for (String raw : value.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
            String key = raw.trim();
            switch (key) {
                case "hiv_testing": labels.add(getString(R.string.mothermentor_referral_hiv_testing)); break;
                case "syphilis_testing": labels.add(getString(R.string.mothermentor_referral_syphilis_testing)); break;
                case "hepatitis_b_testing": labels.add(getString(R.string.mothermentor_referral_hepatitis_b_testing)); break;
                case "anc": labels.add(getString(R.string.mothermentor_referral_anc)); break;
                case "ctc": labels.add(getString(R.string.mothermentor_referral_ctc)); break;
                case "gbv": labels.add(getString(R.string.mothermentor_referral_gbv)); break;
                case "other": labels.add(getString(R.string.mothermentor_referral_other)); break;
                default: labels.add(key);
            }
        }
        return StringUtils.join(labels, "\n");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
