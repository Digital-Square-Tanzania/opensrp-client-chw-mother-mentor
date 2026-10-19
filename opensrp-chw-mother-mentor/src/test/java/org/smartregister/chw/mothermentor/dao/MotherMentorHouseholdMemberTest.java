package org.smartregister.chw.mothermentor.dao;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.commonregistry.CommonPersonObject;
import org.smartregister.commonregistry.CommonRepository;

import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, manifest = Config.NONE)
public class MotherMentorHouseholdMemberTest {
    private MotherMentorLibrary previous;
    private org.smartregister.Context context;
    private CommonRepository repository;

    @Before
    public void setUp() {
        previous = ReflectionHelpers.getStaticField(MotherMentorLibrary.class, "instance");
        MotherMentorLibrary library = mock(MotherMentorLibrary.class);
        context = mock(org.smartregister.Context.class);
        repository = mock(CommonRepository.class);
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", library);
        when(library.context()).thenReturn(context);
        when(context.commonrepository("configured_family_table")).thenReturn(repository);
    }

    @After
    public void tearDown() {
        ReflectionHelpers.setStaticField(MotherMentorLibrary.class, "instance", previous);
    }

    @Test
    public void loadsHouseholdIdentityAndContactFieldsFromConfiguredTable() {
        Map<String, String> columns = new HashMap<>();
        columns.put(DBConstants.KEY.FIRST_NAME, "Jaribio");
        columns.put(DBConstants.KEY.LAST_NAME, "Family");
        columns.put(DBConstants.KEY.UNIQUE_ID, "12345");
        columns.put(DBConstants.KEY.VILLAGE_TOWN, "Test village");
        columns.put(DBConstants.KEY.FAMILY_HEAD, "head-id");
        columns.put(DBConstants.KEY.PRIMARY_CARE_GIVER, "carer-id");
        columns.put(DBConstants.KEY.PHONE_NUMBER, "0700000000");
        CommonPersonObject person = mock(CommonPersonObject.class);
        when(person.getCaseId()).thenReturn("household-id");
        when(person.getColumnmaps()).thenReturn(columns);
        when(repository.findByBaseEntityId("household-id")).thenReturn(person);

        MemberObject member =
                MotherMentorDao.getHouseholdMember("household-id", "configured_family_table");

        assertNotNull(member);
        assertEquals("household-id", member.getBaseEntityId());
        assertEquals("household-id", member.getFamilyBaseEntityId());
        assertEquals("Jaribio", member.getFirstName());
        assertEquals("Family", member.getLastName());
        assertEquals("12345", member.getUniqueId());
        assertEquals("Test village", member.getAddress());
        assertEquals("head-id", member.getFamilyHead());
        assertEquals("carer-id", member.getPrimaryCareGiver());
        assertEquals("0700000000", member.getPhoneNumber());
        assertEquals("Jaribio Family", member.getFullName());
        verify(context).commonrepository("configured_family_table");
    }

    @Test
    public void missingHouseholdOrRepositoryReturnsNullForMemberFallback() {
        assertNull(MotherMentorDao.getHouseholdMember("missing", "configured_family_table"));
        assertNull(MotherMentorDao.getHouseholdMember("missing", "unconfigured_table"));
    }

    @Test
    public void emptyIdentityOrTableDoesNotQueryRepository() {
        assertNull(MotherMentorDao.getHouseholdMember(null, "configured_family_table"));
        assertNull(MotherMentorDao.getHouseholdMember("household-id", " "));
        verifyNoInteractions(context, repository);
    }

    @Test
    public void repositoryFailureReturnsNullForMemberFallback() {
        when(repository.findByBaseEntityId("household-id"))
                .thenThrow(new IllegalStateException("Repository unavailable"));
        assertNull(MotherMentorDao.getHouseholdMember("household-id", "configured_family_table"));
    }
}
