package org.smartregister.chw.mothermentor.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.contract.MotherMentorProfileContract;
import org.smartregister.chw.mothermentor.custom_views.BaseMotherMentorFloatingMenu;
import org.smartregister.chw.mothermentor.dao.MotherMentorDao;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.domain.Visit;
import org.smartregister.chw.mothermentor.interactor.BaseMotherMentorProfileInteractor;
import org.smartregister.chw.mothermentor.presenter.BaseMotherMentorProfilePresenter;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.MotherMentorUtil;
import org.smartregister.domain.AlertStatus;
import org.smartregister.helper.ImageRenderHelper;
import org.smartregister.view.activity.BaseProfileActivity;
import org.smartregister.view.customcontrols.CustomFontTextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;
import timber.log.Timber;


public abstract class BaseMotherMentorProfileActivity extends BaseProfileActivity implements MotherMentorProfileContract.View, MotherMentorProfileContract.InteractorCallBack {

    protected MemberObject memberObject;
    protected MotherMentorProfileContract.Presenter profilePresenter;
    protected CircleImageView imageView;
    protected TextView textViewName;
    protected TextView textViewGender;
    protected TextView textViewLocation;
    protected TextView textViewUniqueID;
    protected TextView textViewRecordMotherMentor;
    protected TextView textViewRecordTbContactVisit;
    protected TextView textViewRecordMotherMentorTreatmentStartDate;
    protected TextView textViewRecordAnc;
    protected TextView textViewContinueMotherMentor;
    protected TextView textViewContinueMotherMentorService;
    protected TextView manualProcessVisit;
    protected TextView textview_positive_date;
    protected TextView textViewRegisterMotherMentorContact;
    protected View view_last_visit_row;
    protected View view_most_due_overdue_row;
    protected View view_family_row;
    protected View view_positive_date_row;
    protected RelativeLayout rlLastVisit;
    protected RelativeLayout rlObservationResults;
    protected RelativeLayout rlUpcomingServices;
    protected RelativeLayout rlFamilyServicesDue;
    protected RelativeLayout visitStatus;
    protected RelativeLayout visitInProgress;
    protected RelativeLayout mothermentorServiceInProgress;
    protected ImageView imageViewCross;
    protected TextView textViewUndo;
    protected RelativeLayout rlMotherMentorPositiveDate;
    protected TextView textViewVisitDone;
    protected RelativeLayout visitDone;
    protected LinearLayout recordVisits;
    protected TextView textViewVisitDoneEdit;
    protected TextView textViewRecordAncNotDone;
    protected String profileType;
    protected BaseMotherMentorFloatingMenu baseMotherMentorFloatingMenu;
    protected CustomFontTextView ivViewHistoryArrow;
    private TextView tvUpComingServices;
    private TextView tvFamilyStatus;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
    private ProgressBar progressBar;

    public static void startProfileActivity(Activity activity, String baseEntityId) {
        Intent intent = new Intent(activity, BaseMotherMentorProfileActivity.class);
        intent.putExtra(Constants.ACTIVITY_PAYLOAD.BASE_ENTITY_ID, baseEntityId);
        activity.startActivity(intent);
    }

    public abstract void openObservationResults();

    public abstract void openMotherMentorContactRegister();

    @Override
    protected void onCreation() {
        setContentView(R.layout.activity_mothermentor_profile);
        Toolbar toolbar = findViewById(R.id.collapsing_toolbar);
        setSupportActionBar(toolbar);
        String baseEntityId = getIntent().getStringExtra(Constants.ACTIVITY_PAYLOAD.BASE_ENTITY_ID);
        profileType = getIntent().getStringExtra(Constants.ACTIVITY_PAYLOAD.PROFILE_TYPE);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            Drawable upArrow = getResources().getDrawable(R.drawable.ic_arrow_back_white_24dp);
            upArrow.setColorFilter(getResources().getColor(R.color.text_blue), PorterDuff.Mode.SRC_ATOP);
            actionBar.setHomeAsUpIndicator(upArrow);
        }

        toolbar.setNavigationOnClickListener(v -> BaseMotherMentorProfileActivity.this.finish());
        appBarLayout = this.findViewById(R.id.collapsing_toolbar_appbarlayout);
        if (Build.VERSION.SDK_INT >= 21) {
            appBarLayout.setOutlineProvider(null);
        }

        textViewName = findViewById(R.id.textview_name);
        textViewGender = findViewById(R.id.textview_gender);
        textViewLocation = findViewById(R.id.textview_address);
        textViewUniqueID = findViewById(R.id.textview_id);
        view_last_visit_row = findViewById(R.id.view_last_visit_row);
        view_most_due_overdue_row = findViewById(R.id.view_most_due_overdue_row);
        view_family_row = findViewById(R.id.view_family_row);
        view_positive_date_row = findViewById(R.id.view_positive_date_row);
        imageViewCross = findViewById(R.id.tick_image);
        tvUpComingServices = findViewById(R.id.textview_name_due);
        tvFamilyStatus = findViewById(R.id.textview_family_has);
        textview_positive_date = findViewById(R.id.textview_positive_date);
        rlLastVisit = findViewById(R.id.rlLastVisit);
        rlObservationResults = findViewById(R.id.rlObservationResults);
        rlUpcomingServices = findViewById(R.id.rlUpcomingServices);
        rlFamilyServicesDue = findViewById(R.id.rlFamilyServicesDue);
        rlMotherMentorPositiveDate = findViewById(R.id.rlMotherMentorPositiveDate);
        textViewVisitDone = findViewById(R.id.textview_visit_done);
        visitStatus = findViewById(R.id.record_visit_not_done_bar);
        visitDone = findViewById(R.id.visit_done_bar);
        visitInProgress = findViewById(R.id.record_visit_in_progress);
        mothermentorServiceInProgress = findViewById(R.id.record_mothermentor_service_visit_in_progress);
        recordVisits = findViewById(R.id.record_visits);
        progressBar = findViewById(R.id.progress_bar);
        textViewRecordAncNotDone = findViewById(R.id.textview_record_anc_not_done);
        textViewVisitDoneEdit = findViewById(R.id.textview_edit);
        textViewRecordMotherMentor = findViewById(R.id.textview_record_mothermentor);
        textViewRecordTbContactVisit = findViewById(R.id.textview_record_mothermentor_contact_visit);
        textViewRecordMotherMentorTreatmentStartDate = findViewById(R.id.textview_record_mother_mentor_start_date);
        textViewContinueMotherMentor = findViewById(R.id.textview_continue);
        textViewContinueMotherMentorService = findViewById(R.id.continue_mothermentor_service);
        textViewRegisterMotherMentorContact = findViewById(R.id.textview_register_mother_mentor_contact);
        manualProcessVisit = findViewById(R.id.textview_manual_process);
        textViewRecordAnc = findViewById(R.id.textview_record_anc);
        textViewUndo = findViewById(R.id.textview_undo);
        imageView = findViewById(R.id.imageview_profile);

        ivViewHistoryArrow = findViewById(R.id.ivViewHistoryArrow);


        ivViewHistoryArrow.setOnClickListener(this);
        textViewRecordAncNotDone.setOnClickListener(this);
        textViewVisitDoneEdit.setOnClickListener(this);
        rlLastVisit.setOnClickListener(this);
        rlUpcomingServices.setOnClickListener(this);
        rlFamilyServicesDue.setOnClickListener(this);
        rlMotherMentorPositiveDate.setOnClickListener(this);
        textViewRecordMotherMentor.setOnClickListener(this);
        textViewRecordTbContactVisit.setOnClickListener(this);
        textViewContinueMotherMentor.setOnClickListener(this);
        textViewContinueMotherMentorService.setOnClickListener(this);
        textViewRegisterMotherMentorContact.setOnClickListener(this);
        manualProcessVisit.setOnClickListener(this);
        textViewRecordAnc.setOnClickListener(this);
        textViewUndo.setOnClickListener(this);

        imageRenderHelper = new ImageRenderHelper(this);
        memberObject = getMemberObject(baseEntityId);
        initializePresenter();
        profilePresenter.fillProfileData(memberObject);
        setupViews();
    }


    @Override
    protected void onResume() {
        super.onResume();
        MotherMentorDao.closeTbNegativeClients();
        new Handler(Looper.getMainLooper()).postDelayed(this::setupViews, 200);
    }

    @Override
    protected void onResumption() {
        super.onResumption();
        setupViews();
    }

    @Override
    protected void setupViews() {
        initializeFloatingMenu();
        setupButtons();
    }

    protected void setupButtons() {
        rlLastVisit.setVisibility(View.GONE);
    }

    protected Visit getServiceVisit() {
        return MotherMentorLibrary.getInstance().visitRepository().getLatestVisit(memberObject.getBaseEntityId(), Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES);
    }


    protected void processMotherMentorService() {
        findViewById(R.id.family_mothermentor_head).setVisibility(View.VISIBLE);
    }


    protected MemberObject getMemberObject(String baseEntityId) {
        MemberObject member = MotherMentorDao.getMember(baseEntityId);
        if (member != null) {
            return member;
        }

        return MotherMentorDao.getContact(baseEntityId);

    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.title_layout) {
            onBackPressed();
        } else if (id == R.id.rlLastVisit) {
            this.openMedicalHistory();
        } else if (id == R.id.rlUpcomingServices) {
            this.openUpcomingService();
        } else if (id == R.id.rlFamilyServicesDue) {
            this.openFamilyDueServices();
        } else if (id == R.id.textview_record_mothermentor) {
            if (textViewRecordMotherMentor.getText().equals(getString(R.string.record_mothermentor))) {
                this.openRecordClientVisit();
            } else if (textViewRecordMotherMentor.getText().equals(getString(R.string.record_mothermentor_contact_visit))) {
                this.openRecordTbContactVisit();
            } else if (textViewRecordMotherMentor.getText().equals(getString(R.string.record_mothermentor_client_followup_visit))) {
                this.openFollowupVisit();
            } else if (textViewRecordMotherMentor.getText().equals(getString(R.string.record_mothermentor_contact_visit_followup))) {
                this.openTbContactFollowUpVisit();
            } else if (textViewRecordMotherMentor.getText().equals(getString(R.string.record_observation_results))) {
                this.openObservationResults();
            } else {
                Toast.makeText(getApplicationContext(), "No click", Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.continue_mothermentor_service) {
            this.continueService();
        } else if (id == R.id.textview_continue) {
            this.continueContactVisit();
        } else if (id == R.id.textview_register_mother_mentor_contact) {
            this.openMotherMentorContactRegister();
        }
    }

    @Override
    protected void initializePresenter() {
        showProgressBar(true);
        profilePresenter = new BaseMotherMentorProfilePresenter(this, new BaseMotherMentorProfileInteractor(), memberObject);
        fetchProfileData();
        profilePresenter.refreshProfileBottom();
    }

    public void initializeFloatingMenu() {
        if (StringUtils.isNotBlank(memberObject.getPhoneNumber())) {
            baseMotherMentorFloatingMenu = new BaseMotherMentorFloatingMenu(this, memberObject);
            baseMotherMentorFloatingMenu.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
            LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            addContentView(baseMotherMentorFloatingMenu, linearLayoutParams);
        }
    }


    @Override
    public void hideView() {
        //Implement later
    }

    @Override
    public void openFollowupVisit() {
        //Implement in application
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void setProfileViewWithData() {
        textViewName.setText(String.format("%s %s %s, %d", memberObject.getFirstName(), memberObject.getMiddleName(), memberObject.getLastName(), memberObject.getAge()));
        textViewGender.setText(MotherMentorUtil.getGenderTranslated(this, memberObject.getGender()));
        textViewLocation.setText(memberObject.getAddress());
        textViewUniqueID.setText(memberObject.getUniqueId());

        if (StringUtils.isNotBlank(memberObject.getPrimaryCareGiver()) && memberObject.getPrimaryCareGiver().equals(memberObject.getBaseEntityId())) {
            findViewById(R.id.primary_mothermentor_caregiver).setVisibility(View.GONE);
        }

        if (memberObject.getMotherMentorTestDate() != null) {
            textview_positive_date.setText(getString(R.string.mothermentor_positive) + " " + formatTime(memberObject.getMotherMentorTestDate()));
        }
    }

    @Override
    public void setOverDueColor() {
        textViewRecordMotherMentor.setBackground(getResources().getDrawable(R.drawable.record_btn_selector_overdue));

    }

    @Override
    protected ViewPager setupViewPager(ViewPager viewPager) {
        return null;
    }

    @Override
    protected void fetchProfileData() {
        //fetch profile data
    }

    @Override
    public void showProgressBar(boolean status) {
        progressBar.setVisibility(status ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void refreshMedicalHistory(boolean hasHistory) {
        showProgressBar(false);
//       rlLastVisit.setVisibility(hasHistory ? View.VISIBLE : View.GONE);
    }

    @Override
    public void refreshUpComingServicesStatus(String service, AlertStatus status, Date date) {
        showProgressBar(false);
        if (status == AlertStatus.complete) return;
        view_most_due_overdue_row.setVisibility(View.GONE);
        rlUpcomingServices.setVisibility(View.GONE);

        if (status == AlertStatus.upcoming) {
            tvUpComingServices.setText(MotherMentorUtil.fromHtml(getString(R.string.vaccine_service_upcoming, service, dateFormat.format(date))));
        } else {
            tvUpComingServices.setText(MotherMentorUtil.fromHtml(getString(R.string.vaccine_service_due, service, dateFormat.format(date))));
        }
    }

    @Override
    public void refreshFamilyStatus(AlertStatus status) {
        showProgressBar(false);
        if (status == AlertStatus.complete) {
            setFamilyStatus(getString(R.string.family_has_nothing_due));
        } else if (status == AlertStatus.normal) {
            setFamilyStatus(getString(R.string.family_has_services_due));
        } else if (status == AlertStatus.urgent) {
            tvFamilyStatus.setText(MotherMentorUtil.fromHtml(getString(R.string.family_has_service_overdue)));
        }
    }

    private void setFamilyStatus(String familyStatus) {
        view_family_row.setVisibility(View.VISIBLE);
        rlFamilyServicesDue.setVisibility(View.GONE);
        tvFamilyStatus.setText(familyStatus);
    }

    @Override
    public void openMedicalHistory() {
        //implementation here

    }

    @Override
    public void openUpcomingService() {
        //implement
    }

    @Override
    public void openFamilyDueServices() {
        //implement
    }

    @Nullable
    private String formatTime(Date dateTime) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy");
            return formatter.format(dateTime);
        } catch (Exception e) {
            Timber.d(e);
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_CODE_GET_JSON && resultCode == RESULT_OK) {
            profilePresenter.saveForm(data.getStringExtra(Constants.JSON_FORM_EXTRA.JSON));
//           finish();
            textViewRecordTbContactVisit.setVisibility(View.GONE);

        }
    }

    protected boolean isVisitOnProgress(Visit visit) {

        return visit != null && !visit.getProcessed();
    }
}
