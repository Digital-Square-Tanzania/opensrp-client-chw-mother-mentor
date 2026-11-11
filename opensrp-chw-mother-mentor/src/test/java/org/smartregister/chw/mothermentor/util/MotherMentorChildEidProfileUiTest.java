package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.repository.MotherMentorChildEidRepository;
import org.smartregister.chw.mothermentor.repository.MotherMentorMotherChildRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class MotherMentorChildEidProfileUiTest {
    private final LocalDate day = new LocalDate(2026, 11, 4);

    @Test
    @Config(qualifiers = "en")
    public void englishProfileShowsActualIdentityAndMonthAgeWithoutBirthOrderAsHeiNumber() {
        Activity activity = screen();
        MotherMentorChildEidProfileUi ui = new MotherMentorChildEidProfileUi();
        ui.bind(activity, state(false), identity(), day, text(activity, R.id.client_metadata));
        assertEquals("Baraka John, 19 months", text(activity, R.id.textview_name).getText().toString());
        assertEquals("Mother: Neema John · Kibiti · HEI-0412-C01",
                text(activity, R.id.mothermentor_eid_profile_metadata).getText().toString());
        assertTrue(text(activity, R.id.mothermentor_eid_profile_reason).getText().toString().contains("04-10-2026"));
        assertAbsent(activity, "Child age:", "Referral status:", "Last test:");
        assertEquals(View.GONE, text(activity, R.id.textview_gender).getVisibility());
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliProfileKeepsThePillAndOmitsTheExtraSummaryRows() {
        Activity activity = screen();
        MotherMentorChildEidRepository.State child = state(false);
        when(child.getReferralState()).thenReturn("referred");
        when(child.getReferralTest()).thenReturn("miezi9");
        TextView pill = text(activity, R.id.client_metadata);
        new MotherMentorChildEidProfileUi().bind(activity, child, identity(), day, pill);
        assertEquals("Baraka John, Miezi 19", text(activity, R.id.textview_name).getText().toString());
        assertEquals("Amepewa rufaa", pill.getText().toString());
        assertAbsent(activity, "Umri wa mtoto:", "Hali ya rufaa:", "Kipimo cha mwisho:");
        assertEquals("Rekodi Mrejesho wa Ufuatiliaji",
                text(activity, R.id.textview_record_mothermentor).getText().toString());
    }

    private void assertAbsent(Activity activity, String... labels) {
        for (String label : labels) {
            ArrayList<View> matches = new ArrayList<>();
            activity.findViewById(android.R.id.content).findViewsWithText(
                    matches, label, View.FIND_VIEWS_WITH_TEXT);
            assertTrue(label, matches.isEmpty());
        }
    }

    @Test
    public void closedCardDisablesCtaAndHidingRemovesItsSummaryAndBanner() {
        Activity activity = screen();
        MotherMentorChildEidProfileUi ui = new MotherMentorChildEidProfileUi();
        MotherMentorChildEidRepository.State child = state(true);
        TextView cta = text(activity, R.id.textview_record_mothermentor);
        cta.setVisibility(View.GONE);
        ui.bind(activity, child, identity(), day, null);
        assertEquals(View.VISIBLE, cta.getVisibility());
        assertFalse(cta.isEnabled());
        LinearLayout parent = (LinearLayout) cta.getParent();
        View banner = parent.getChildAt(parent.indexOfChild(cta) + 1);
        assertEquals(View.VISIBLE, banner.getVisibility());
        assertTrue(((TextView) banner).getText().toString().contains("03-11-2026"));
        int count = parent.getChildCount();
        ui.bind(activity, child, identity(), day, null);
        assertEquals(count, parent.getChildCount());
        ui.hide();
        assertEquals(View.GONE, banner.getVisibility());
        assertEquals(View.GONE, parent.getChildAt(parent.indexOfChild(cta) - 1).getVisibility());
    }

    @Test
    public void openCardDoesNotUndoTheHostsCompletedVisitCooldown() {
        Activity activity = screen();
        TextView cta = text(activity, R.id.textview_record_mothermentor);
        cta.setEnabled(false);
        cta.setAlpha(0.5f);
        new MotherMentorChildEidProfileUi().bind(activity, state(false), identity(), day, null);
        assertFalse(cta.isEnabled());
        assertEquals(0.5f, cta.getAlpha(), 0f);
    }

    @Test
    public void actualProfileHidesTheEntireDemographicsRowIncludingItsSeparators() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat);
        // Preserve the actual XML hierarchy; font initialization belongs to the host app.
        activity.getLayoutInflater().setFactory2(new android.view.LayoutInflater.Factory2() {
            @Override public View onCreateView(View parent, String name, android.content.Context context,
                    android.util.AttributeSet attributes) {
                return "org.smartregister.view.customcontrols.CustomFontTextView".equals(name)
                        ? new TextView(context, attributes) : null;
            }
            @Override public View onCreateView(String name, android.content.Context context,
                    android.util.AttributeSet attributes) {
                return onCreateView(null, name, context, attributes);
            }
        });
        activity.setContentView(R.layout.activity_mothermentor_profile);
        new MotherMentorChildEidProfileUi().bind(activity, state(false), identity(), day, null);
        assertEquals(View.GONE, activity.findViewById(R.id.mothermentor_profile_demographics).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.textview_name).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.mothermentor_eid_profile_metadata).getVisibility());
    }

    private Activity screen() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout parent = new LinearLayout(activity);
        parent.setOrientation(LinearLayout.VERTICAL);
        for (int id : new int[] {R.id.textview_name, R.id.textview_gender, R.id.client_metadata,
                R.id.textview_record_mothermentor}) {
            TextView child = new TextView(activity);
            child.setId(id);
            parent.addView(child);
        }
        activity.setContentView(parent);
        return activity;
    }

    private MotherMentorChildEidRepository.State state(boolean closed) {
        MotherMentorChildEidRepository.State state = mock(MotherMentorChildEidRepository.State.class);
        when(state.getChildDob()).thenReturn(new LocalDate(2025, 4, 4));
        when(state.getReferralState()).thenReturn("none");
        when(state.getReferralTest()).thenReturn("");
        when(state.getMissedTest()).thenReturn("miezi9");
        when(state.getMissedDue()).thenReturn("2026-10-04");
        when(state.getClosedStatus()).thenReturn(closed ? "graduated" : "");
        when(state.getClosedDate()).thenReturn(closed ? "2026-11-03" : "");
        when(state.isClosed()).thenReturn(closed);
        when(state.getClinicalState()).thenReturn(new MotherMentorMotherChildRepository.State(new LocalDate(2025, 4, 4)));
        return state;
    }

    private Map<String, String> identity() {
        Map<String, String> values = new HashMap<>();
        values.put(DBConstants.KEY.FIRST_NAME, "Baraka");
        values.put(DBConstants.KEY.LAST_NAME, "John");
        values.put(DBConstants.KEY.MOTHER_NAME, "Neema John");
        values.put(DBConstants.KEY.VILLAGE_TOWN, "Kibiti");
        values.put(DBConstants.KEY.HEI_NUMBER, "HEI-0412-C01");
        values.put(DBConstants.KEY.HEI_FOLLOW_UP_NUMBER, "1");
        return values;
    }

    private TextView text(Activity activity, int id) { return activity.findViewById(id); }
}
