package org.smartregister.chw.mothermentor.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.custom_views.BaseMotherMentorFloatingMenu;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.fragment.BaseMotherMentorCallDialogFragment;
import org.smartregister.chw.mothermentor_sample.activity.BaseTestMotherMentorProfileActivity;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorProfileFloatingMenuTest {

    @Before
    public void resetCallWidget() {
        CallWidgetShadow.reset();
    }

    @Test
    public void missingPhoneDoesNotHidePencil() {
        for (String phone : new String[] {null, "", "   "}) {
            BaseMotherMentorProfileActivity activity = profile(member(phone));

            activity.initializeFloatingMenu();

            assertVisiblePencil(activity);
        }
    }

    @Test
    public void recordedPhoneKeepsPencilVisible() {
        BaseMotherMentorProfileActivity activity = profile(member("0712345678"));

        activity.initializeFloatingMenu();

        assertVisiblePencil(activity);
    }

    @Test
    public void profileRefreshesReuseOneFloatingMenu() {
        BaseMotherMentorProfileActivity activity = profile(member("0712345678"));
        activity.initializeFloatingMenu();
        BaseMotherMentorFloatingMenu firstMenu = activity.baseMotherMentorFloatingMenu;

        activity.initializeFloatingMenu();
        activity.initializeFloatingMenu();

        assertSame(firstMenu, activity.baseMotherMentorFloatingMenu);
        ViewGroup content = activity.findViewById(android.R.id.content);
        int menus = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            if (content.getChildAt(i) instanceof BaseMotherMentorFloatingMenu) {
                menus++;
            }
        }
        assertEquals(1, menus);
    }

    @Test
    public void missingMemberDoesNotAttachMenu() {
        BaseMotherMentorProfileActivity activity = profile(null);

        activity.initializeFloatingMenu();

        assertNull(activity.baseMotherMentorFloatingMenu);
        assertNull(activity.findViewById(R.id.mothermentor_fab));
    }

    @Test
    public void menuCanInitializeWhenMemberBecomesAvailable() {
        BaseMotherMentorProfileActivity activity = profile(null);
        activity.initializeFloatingMenu();

        activity.memberObject = member(null);
        activity.initializeFloatingMenu();

        assertVisiblePencil(activity);
    }

    @Test
    @Config(shadows = CallWidgetShadow.class)
    public void callActionOpensExistingWidgetWhenNumberRecorded() {
        MemberObject member = member("0712345678");
        BaseMotherMentorProfileActivity activity = profile(member);
        activity.initializeFloatingMenu();

        activity.findViewById(R.id.mothermentor_fab).performClick();
        activity.findViewById(R.id.callFab).performClick();

        assertSame(activity, CallWidgetShadow.activity);
        assertSame(member, CallWidgetShadow.member);
    }

    @Test
    @Config(shadows = CallWidgetShadow.class)
    public void reusedPencilUsesRefreshedMemberDetails() {
        BaseMotherMentorProfileActivity activity = profile(member(null));
        activity.initializeFloatingMenu();
        View pencil = activity.findViewById(R.id.mothermentor_fab);
        MemberObject refreshedMember = member("0712345678");

        activity.memberObject = refreshedMember;
        activity.initializeFloatingMenu();
        pencil.performClick();
        activity.findViewById(R.id.callFab).performClick();

        assertSame(pencil, activity.findViewById(R.id.mothermentor_fab));
        assertSame(refreshedMember, CallWidgetShadow.member);
    }

    @Test
    public void pencilOpensAndClosesMenu() {
        BaseMotherMentorProfileActivity activity = profile(member(null));
        activity.initializeFloatingMenu();
        View menu = activity.findViewById(R.id.menu_bar);
        View pencil = activity.findViewById(R.id.mothermentor_fab);
        assertEquals(View.GONE, menu.getVisibility());

        pencil.performClick();
        assertEquals(View.VISIBLE, menu.getVisibility());
        pencil.performClick();
        assertEquals(View.GONE, menu.getVisibility());
    }

    @Test
    public void referralStaysAvailableWithoutPhoneAndClosesMenuAfterSelection() {
        BaseMotherMentorProfileActivity activity = profile(member(null));
        activity.initializeFloatingMenu();
        AtomicInteger referrals = new AtomicInteger();
        activity.baseMotherMentorFloatingMenu.setReferralClickListener(
                view -> referrals.incrementAndGet());

        activity.findViewById(R.id.mothermentor_fab).performClick();

        assertFalse(activity.findViewById(R.id.callFab).isEnabled());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.CallTextViewHint).getVisibility());
        assertEquals(
                View.VISIBLE,
                activity.findViewById(R.id.refer_to_facility_layout).getVisibility());
        activity.findViewById(R.id.refer_to_facility_fab).performClick();
        assertEquals(1, referrals.get());
        assertEquals(View.GONE, activity.findViewById(R.id.menu_bar).getVisibility());
    }

    @Test
    @Config(shadows = CallWidgetShadow.class)
    public void missingPhoneCannotOpenCallWidget() {
        BaseMotherMentorProfileActivity activity = profile(member(null));
        activity.initializeFloatingMenu();
        activity.findViewById(R.id.mothermentor_fab).performClick();

        activity.findViewById(R.id.callFab).performClick();

        assertNull(CallWidgetShadow.activity);
    }

    @Test
    public void unavailableReferralHandlerDoesNotExposeAnUnimplementedAction() {
        BaseMotherMentorProfileActivity activity = profile(member(null));
        activity.initializeFloatingMenu();
        activity.findViewById(R.id.mothermentor_fab).performClick();

        assertEquals(
                View.GONE, activity.findViewById(R.id.refer_to_facility_layout).getVisibility());
    }

    @Test
    @Config(qualifiers = "sw")
    public void referralAndMissingPhoneLabelsAreInSwahili() {
        BaseMotherMentorProfileActivity activity = profile(member(null));
        Configuration configuration = new Configuration(activity.getResources().getConfiguration());
        configuration.setLocale(new Locale("sw"));
        activity.getResources().updateConfiguration(
                configuration, activity.getResources().getDisplayMetrics());
        activity.initializeFloatingMenu();
        activity.baseMotherMentorFloatingMenu.setReferralClickListener(view -> {});
        activity.findViewById(R.id.mothermentor_fab).performClick();

        TextView noPhone = activity.findViewById(R.id.CallTextViewHint);
        assertEquals("(Hakuna namba iliyotolewa)", noPhone.getText().toString());
        assertEquals(
                "Toa rufaa kwenda kituo cha kutolea huduma",
                activity.findViewById(R.id.refer_to_facility_fab).getContentDescription());
    }

    @Implements(BaseMotherMentorCallDialogFragment.class)
    public static class CallWidgetShadow {
        private static Activity activity;
        private static MemberObject member;

        @Implementation
        protected static BaseMotherMentorCallDialogFragment launchDialog(
                Activity caller, MemberObject client) {
            activity = caller;
            member = client;
            return null;
        }

        @Resetter
        public static void reset() {
            activity = null;
            member = null;
        }
    }

    private BaseMotherMentorProfileActivity profile(MemberObject member) {
        BaseMotherMentorProfileActivity activity =
                Robolectric.buildActivity(BaseTestMotherMentorProfileActivity.class).get();
        activity.setTheme(R.style.MotherMentorTheme_AppBarOverlay_Light);
        activity.setContentView(new FrameLayout(activity));
        ReflectionHelpers.setField(activity, "memberObject", member);
        return activity;
    }

    private MemberObject member(String phone) {
        MemberObject member = new MemberObject();
        member.setBaseEntityId("test-mother-mentor-client");
        member.setPhoneNumber(phone);
        return member;
    }

    private void assertVisiblePencil(BaseMotherMentorProfileActivity activity) {
        View pencil = activity.findViewById(R.id.mothermentor_fab);
        assertNotNull(pencil);
        assertEquals(View.VISIBLE, pencil.getVisibility());
        assertTrue(pencil.isClickable());
    }
}
