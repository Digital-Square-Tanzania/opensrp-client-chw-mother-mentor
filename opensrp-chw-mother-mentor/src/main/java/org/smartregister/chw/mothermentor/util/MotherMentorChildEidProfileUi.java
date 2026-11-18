package org.smartregister.chw.mothermentor.util;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import org.joda.time.LocalDate;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;

import java.util.Map;

/** Presentation of library-owned tracing data inside the host's existing client profile. */
public final class MotherMentorChildEidProfileUi {
    private View summary;

    public void positionHeader(Activity activity, TextView pill) {
        View portrait = activity.findViewById(R.id.imageview_profile);
        if (portrait == null || pill == null || !(portrait.getParent() instanceof LinearLayout)) return;
        LinearLayout profile = (LinearLayout) portrait.getParent();
        int index = profile.indexOfChild(portrait);
        ViewGroup.LayoutParams portraitSize = portrait.getLayoutParams();
        profile.removeView(portrait);
        if (pill.getParent() instanceof ViewGroup) ((ViewGroup) pill.getParent()).removeView(pill);

        ConstraintLayout header = new ConstraintLayout(activity);
        profile.addView(header, index, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ConstraintLayout.LayoutParams photo = new ConstraintLayout.LayoutParams(portraitSize.width, portraitSize.height);
        photo.startToStart = photo.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        photo.topToTop = photo.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        header.addView(portrait, photo);

        ConstraintLayout.LayoutParams badge = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badge.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        badge.endToStart = portrait.getId();
        badge.topToTop = badge.bottomToBottom = portrait.getId();
        badge.horizontalBias = 1f;
        badge.constrainedWidth = true;
        badge.setMarginStart(dp(activity, 8));
        badge.setMarginEnd(dp(activity, 8));
        pill.setGravity(Gravity.CENTER);
        pill.setTextSize(12);
        header.addView(pill, badge);
    }

    public void hide() {
        if (summary != null) summary.setVisibility(View.GONE);
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
            View legacyActions = activity.findViewById(R.id.record_visits);
            if (legacyActions != null) legacyActions.setVisibility(View.GONE);
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
        String reason = MotherMentorChildEidLabels.entryReason(activity, child.getMissedTest(), child.getMissedDue());
        text(R.id.mothermentor_eid_profile_reason).setText(reason);
        text(R.id.mothermentor_eid_profile_reason).setVisibility(reason.isEmpty() ? View.GONE : View.VISIBLE);
        text(R.id.mothermentor_eid_profile_reason_title).setVisibility(reason.isEmpty() ? View.GONE : View.VISIBLE);
        LocalDate sample = child.isClosed() ? null
                : MotherMentorChildEidResultOverdue.overdueDate(child.getEnrollmentValues(), day);
        TextView overdue = text(R.id.mothermentor_eid_profile_overdue);
        overdue.setVisibility(sample == null ? View.GONE : View.VISIBLE);
        overdue.setText(sample == null ? "" : activity.getString(R.string.mothermentor_eid_overdue_sample,
                MotherMentorMotherPregnancyUtils.displayDate(sample)));
        button.setText(R.string.mothermentor_eid_record_visit);
        if (child.isClosed()) button.setVisibility(View.GONE);
        bindPill(activity, child, pill, !reason.isEmpty());
        bindClosedBanner(activity, child);
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

    private void bindClosedBanner(Activity activity, MotherMentorChildEidRepository.State child) {
        TextView closedBanner = text(R.id.mothermentor_eid_profile_closure);
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
