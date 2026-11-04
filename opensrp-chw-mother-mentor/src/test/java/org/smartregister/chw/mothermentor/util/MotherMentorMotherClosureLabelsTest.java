package org.smartregister.chw.mothermentor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorMotherClosureLabelsTest {
    @Test
    @Config(qualifiers = "en")
    public void englishOutcomeAndReasonUseEnglishResources() {
        assertEquals("Moved facility", MotherMentorMotherClosureLabels.label(
                RuntimeEnvironment.getApplication(), MotherMentorMotherClosure.MOVED_FACILITY));
        assertFalse(MotherMentorMotherClosureLabels.note(RuntimeEnvironment.getApplication(),
                MotherMentorMotherClosure.GRADUATED).isEmpty());
    }

    @Test
    @Config(qualifiers = "sw")
    public void swahiliOutcomeAndReasonUseSwahiliResources() {
        assertEquals("Amehama kituo", MotherMentorMotherClosureLabels.label(
                RuntimeEnvironment.getApplication(), MotherMentorMotherClosure.MOVED_FACILITY));
        assertEquals("Amehitimu — mtoto ana majibu ya mwisho", MotherMentorMotherClosureLabels.label(
                RuntimeEnvironment.getApplication(), MotherMentorMotherClosure.GRADUATED));
    }
}
