package org.smartregister.chw.mothermentor.util;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;

import java.util.Map;

/** Presentation of library-owned tracing data inside the host's existing client profile. */
public final class MotherMentorChildEidProfileUi {
    private View summary;
    private TextView closedBanner;

    public void hide() {
        if (summary != null) summary.setVisibility(View.GONE);
        if (closedBanner != null) closedBanner.setVisibility(View.GONE);
    }

    public void bind(Activity activity, MotherMentorChildEidRepository.State child,
            Map<String, String> identity, LocalDate day, TextView pill) {
        TextView button = activity.findViewById(R.id.textview_record_mothermentor);
        if (child == null || button == null || !(button.getParent() instanceof LinearLayout)) {
            hide();
            return;
        }
        LinearLayout parent = (LinearLayout) button.getParent();
        if (summary == null) {
            summary = activity.getLayoutInflater().inflate(R.layout.view_mothermentor_child_eid_summary, parent, false);
            parent.addView(summary, parent.indexOfChild(button));
        }
        summary.setVisibility(View.VISIBLE);
        String age = MotherMentorChildEidLabels.age(activity,
                child.getChildDob() == null ? null : child.getChildDob().toString(), day);
        String name = (value(identity, DBConstants.KEY.FIRST_NAME) + " "
                + value(identity, DBConstants.KEY.MIDDLE_NAME) + " "
                + value(identity, DBConstants.KEY.LAST_NAME)).trim().replaceAll("\\s+", " ");
        TextView nameView = activity.findViewById(R.id.textview_name);
        if (nameView != null && !name.isEmpty()) nameView.setText(age.isEmpty() ? name : name + ", " + age);
        for (int id : new int[] {R.id.mothermentor_profile_demographics, R.id.textview_gender,
                R.id.textview_address, R.id.textview_id, R.id.textview_ctc_number}) {
            View view = activity.findViewById(id);
            if (view != null) view.setVisibility(View.GONE);
        }
        text(R.id.mothermentor_eid_profile_metadata).setText(MotherMentorChildEidLabels.cardSummary(
                activity, value(identity, DBConstants.KEY.MOTHER_NAME),
                value(identity, DBConstants.KEY.VILLAGE_TOWN), value(identity, DBConstants.KEY.HEI_NUMBER)));
        text(R.id.mothermentor_eid_profile_age).setText(activity.getString(R.string.mothermentor_eid_child_age,
                age.isEmpty() ? activity.getString(R.string.mothermentor_eid_not_recorded) : age));
        String test = child.getReferralTest().isEmpty() ? child.getMissedTest() : child.getReferralTest();
        text(R.id.mothermentor_eid_profile_referral).setText(activity.getString(R.string.mothermentor_eid_referral_status,
                child.isClosed() ? MotherMentorChildEidLabels.closedReason(activity, child.getClosedStatus())
                        : MotherMentorChildEidLabels.referral(activity, child.getReferralState(), test)));
        String last = MotherMentorChildEidLabels.lastTest(activity, child.getClinicalState().getSamplingHistory());
        text(R.id.mothermentor_eid_profile_last_test).setText(last);
        text(R.id.mothermentor_eid_profile_last_test).setVisibility(last.isEmpty() ? View.GONE : View.VISIBLE);
        String reason = MotherMentorChildEidLabels.entryReason(activity, child.getMissedTest(), child.getMissedDue());
        text(R.id.mothermentor_eid_profile_reason).setText(reason);
        text(R.id.mothermentor_eid_profile_reason).setVisibility(reason.isEmpty() ? View.GONE : View.VISIBLE);
        text(R.id.mothermentor_eid_profile_reason_title).setVisibility(reason.isEmpty() ? View.GONE : View.VISIBLE);
        button.setText(R.string.mothermentor_eid_record_visit);
        if (child.isClosed()) {
            button.setVisibility(View.VISIBLE);
            button.setEnabled(false);
            button.setAlpha(0.5f);
        }
        bindPill(activity, child, pill, !reason.isEmpty());
        bindClosedBanner(activity, parent, button, child);
    }

    private void bindPill(Activity activity, MotherMentorChildEidRepository.State child, TextView pill,
            boolean missed) {
        if (pill == null) return;
        pill.setVisibility(View.VISIBLE);
        pill.setText(child.isClosed() ? activity.getString(R.string.mothermentor_eid_closed)
                : MotherMentorChildEidLabels.pill(activity, child.getReferralState(), missed));
        boolean success = child.isClosed() && MotherMentorChildEidLabels.isSuccessfulClose(child.getClosedStatus());
        boolean alert = child.isClosed() || "none".equals(child.getReferralState()) && missed;
        boolean pending = !child.isClosed() && ("awaiting_result".equals(child.getReferralState())
                || "awaiting_confirm".equals(child.getReferralState()));
        int foreground = success ? R.color.mothermentor_eid_success : alert ? R.color.mothermentor_eid_alert
                : pending ? R.color.mothermentor_eid_pending_text : R.color.customAppThemeBlue;
        int background = success ? R.color.mothermentor_eid_success_background : alert ? R.color.mothermentor_eid_alert_background
                : pending ? R.color.mothermentor_eid_pending_background : R.color.mothermentor_eid_referred_background;
        pill.setTextColor(activity.getResources().getColor(foreground));
        pill.setBackground(background(activity, background));
        pill.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
    }

    private void bindClosedBanner(Activity activity, LinearLayout parent, TextView button,
            MotherMentorChildEidRepository.State child) {
        if (closedBanner == null) {
            closedBanner = new TextView(activity);
            closedBanner.setTextSize(14);
            closedBanner.setTypeface(Typeface.DEFAULT_BOLD);
            closedBanner.setGravity(Gravity.CENTER);
            closedBanner.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
            parent.addView(closedBanner, parent.indexOfChild(button) + 1,
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        closedBanner.setVisibility(child.isClosed() ? View.VISIBLE : View.GONE);
        if (!child.isClosed()) return;
        String reason = MotherMentorChildEidLabels.closedReason(activity, child.getClosedStatus());
        String facility = child.getExitFacility();
        if (!android.text.TextUtils.isEmpty(facility)) reason += " · " + facility;
        LocalDate date = MotherMentorHeiSchedule.parseDate(child.getClosedDate());
        closedBanner.setText(date == null ? reason : activity.getString(R.string.mothermentor_eid_closed_on,
                reason, MotherMentorMotherPregnancyUtils.displayDate(date)));
        boolean success = MotherMentorChildEidLabels.isSuccessfulClose(child.getClosedStatus());
        closedBanner.setTextColor(activity.getResources().getColor(success
                ? R.color.mothermentor_eid_success : R.color.mothermentor_eid_alert));
        closedBanner.setBackground(background(activity, success
                ? R.color.mothermentor_eid_success_background : R.color.mothermentor_eid_alert_background));
    }

    private static GradientDrawable background(Activity activity, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(activity.getResources().getColor(color));
        shape.setCornerRadius(dp(activity, 12));
        return shape;
    }

    private TextView text(int id) { return summary.findViewById(id); }

    private static String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
