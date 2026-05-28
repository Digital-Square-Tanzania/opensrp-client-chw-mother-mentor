package org.smartregister.chw.mothermentor_sample.presenter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.smartregister.chw.mothermentor.contract.MotherMentorRegisterFragmentContract;
import org.smartregister.chw.mothermentor.presenter.BaseMotherMentorRegisterFragmentPresenter;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.configurableviews.model.View;

import java.util.Set;
import java.util.TreeSet;

public class BaseMotherMentorRegisterFragmentPresenterTest {
    @Mock
    protected MotherMentorRegisterFragmentContract.View view;

    @Mock
    protected MotherMentorRegisterFragmentContract.Model model;

    private BaseMotherMentorRegisterFragmentPresenter baseMotherMentorRegisterFragmentPresenter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        baseMotherMentorRegisterFragmentPresenter = new BaseMotherMentorRegisterFragmentPresenter(view, model, "");
    }

    @Test
    public void assertNotNull() {
        Assert.assertNotNull(baseMotherMentorRegisterFragmentPresenter);
    }

    @Test
    public void getMainCondition() {
        Assert.assertEquals(" ec_mothermentor_screening.is_closed = 0 ", baseMotherMentorRegisterFragmentPresenter.getMainCondition());
    }

    @Test
    public void getDueFilterCondition() {
        Assert.assertEquals(" (cast( julianday(STRFTIME('%Y-%m-%d', datetime('now'))) -  julianday(IFNULL(SUBSTR(mothermentor_test_date,7,4)|| '-' || SUBSTR(mothermentor_test_date,4,2) || '-' || SUBSTR(mothermentor_test_date,1,2),'')) as integer) between 7 and 14) ", baseMotherMentorRegisterFragmentPresenter.getDueFilterCondition());
    }

    @Test
    public void getDefaultSortQuery() {
        Assert.assertEquals(Constants.TABLES.MOTHERMENTOR_SCREENING + "." + DBConstants.KEY.LAST_INTERACTED_WITH + " DESC ", baseMotherMentorRegisterFragmentPresenter.getDefaultSortQuery());
    }

    @Test
    public void getMainTable() {
        Assert.assertEquals(Constants.TABLES.MOTHERMENTOR_SCREENING, baseMotherMentorRegisterFragmentPresenter.getMainTable());
    }

    @Test
    public void initializeQueries() {
        Set<View> visibleColumns = new TreeSet<>();
        baseMotherMentorRegisterFragmentPresenter.initializeQueries(null);
        Mockito.doNothing().when(view).initializeQueryParams(Constants.TABLES.MOTHERMENTOR_SCREENING, null, null);
        Mockito.verify(view).initializeQueryParams(Constants.TABLES.MOTHERMENTOR_SCREENING, null, null);
        Mockito.verify(view).initializeAdapter(visibleColumns);
        Mockito.verify(view).countExecute();
        Mockito.verify(view).filterandSortInInitializeQueries();
    }

}