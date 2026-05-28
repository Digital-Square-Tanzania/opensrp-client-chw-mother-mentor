package org.smartregister.chw.mothermentor_sample.fragment;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.reflect.Whitebox;
import org.smartregister.chw.mothermentor.activity.BaseMotherMentorProfileActivity;
import org.smartregister.chw.mothermentor.fragment.BaseMotherMentorRegisterFragment;
import org.smartregister.commonregistry.CommonPersonObjectClient;

import static org.mockito.Mockito.times;

public class BaseMotherMentorRegisterFragmentTest {
    @Mock
    public BaseMotherMentorRegisterFragment baseTestRegisterFragment;

    @Mock
    public CommonPersonObjectClient client;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test(expected = Exception.class)
    public void openProfile() throws Exception {
        Whitebox.invokeMethod(baseTestRegisterFragment, "openProfile", client);
        PowerMockito.mockStatic(BaseMotherMentorProfileActivity.class);
        BaseMotherMentorProfileActivity.startProfileActivity(null, null);
        PowerMockito.verifyStatic(times(1));

    }
}
