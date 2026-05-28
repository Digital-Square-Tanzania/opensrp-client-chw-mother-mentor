package org.smartregister.chw.mothermentor_sample.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.room.util.StringUtil;

import com.vijay.jsonwizard.activities.JsonWizardFormActivity;
import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.domain.Form;
import com.vijay.jsonwizard.factory.FileSourceFactoryHelper;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;

import org.json.JSONObject;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;
import org.smartregister.chw.mothermentor_sample.R;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.view.activity.SecuredActivity;

import java.time.Year;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import timber.log.Timber;

public class EntryActivity extends SecuredActivity implements View.OnClickListener, BaseMotherMentorVisitContract.VisitView {
    private static MemberObject mothermentorMemberObject;

    public static MemberObject getSampleMember() {
        if (mothermentorMemberObject == null) {
            mothermentorMemberObject = new MemberObject();
            mothermentorMemberObject.setFirstName("Glory");
            mothermentorMemberObject.setLastName("Juma");
            mothermentorMemberObject.setMiddleName("Ali");
            mothermentorMemberObject.setGender("Female");
            mothermentorMemberObject.setMartialStatus("Married");
            mothermentorMemberObject.setAddress("Morogoro");
            mothermentorMemberObject.setDob("1982-01-18T03:00:00.000+03:00");
            mothermentorMemberObject.setUniqueId("3503504");
            mothermentorMemberObject.setBaseEntityId("3503504");
            mothermentorMemberObject.setFamilyBaseEntityId("3503504");
        }

        return mothermentorMemberObject;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLocale("sw");
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        findViewById(R.id.mothermentor_activity).setOnClickListener(this);
        findViewById(R.id.mothermentor_screening).setOnClickListener(this);
        findViewById(R.id.mothermentor_visit_record).setOnClickListener(this);
        findViewById(R.id.mothermentor_mobilization).setOnClickListener(this);
        findViewById(R.id.mothermentor_observation_results).setOnClickListener(this);
        findViewById(R.id.mothermentor_followup_visit).setOnClickListener(this);
        findViewById(R.id.mothermentor_home_visit).setOnClickListener(this);
        findViewById(R.id.mothermentor_profile).setOnClickListener(this);
        findViewById(R.id.mothermentor_contact_profile).setOnClickListener(this);
        findViewById(R.id.mothermentor_update_member_profile).setOnClickListener(this);
        findViewById(R.id.mothermentor_contact_visit).setOnClickListener(this);
    }

    private void setLocale(String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    @Override
    protected void onCreation() {
        Timber.v("onCreation");
    }

    @Override
    protected void onResumption() {
        Timber.v("onCreation");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.mothermentor_activity:
                startActivity(new Intent(this, MotherMentorRegisterActivity.class));
                break;
            case R.id.mothermentor_home_visit:
                MotherMentorServiceActivity.startMotherMentorVisitActivity(this, "12345", true);
                break;
            case R.id.mothermentor_profile:
                MotherMentorMemberProfileActivity.startMe(this, "12345");
                break;
            case R.id.mothermentor_contact_profile:
                MotherMentorContactProfileActivity.startMe(this, "12345");
                break;
            case R.id.mothermentor_update_member_profile:
                UpdateMotherMentorMemberProfileActivity.startMe(this, "12345");
                break;
            case R.id.mothermentor_contact_visit:
                MotherMentorServiceActivity.startMotherMentorVisitActivity(this, "98765", false);
                break;
            case R.id.mothermentor_screening:
                try {
                    startForm("mothermentor_screening");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case R.id.mothermentor_mobilization:
                try {
                    startForm("mothermentor_mobilization_session");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case R.id.mothermentor_observation_results:
                try {
                    startForm("mothermentor_observation_results");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case R.id.mothermentor_followup_visit:
                try {
                    startForm("mothermentor_followup_visit");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case R.id.mothermentor_visit_record:
                try {
                    startForm("mothermentor_record_visit");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                break;
        }
    }

    @SuppressLint("TimberArgCount")
    private void startForm(String formName) throws Exception {
        JSONObject jsonForm = FileSourceFactoryHelper.getFileSource("").getFormFromFile(getApplicationContext(), formName);

        String currentLocationId = "Tanzania";
        if (jsonForm != null) {

            JSONArray dataFields = jsonForm.getJSONObject(JsonFormConstants.STEP1).getJSONArray(JsonFormConstants.FIELDS);
            JSONObject clientID = JsonFormUtils.getFieldJSONObject(dataFields, "tb_client_number");
            JSONObject clientIdUkoma = JsonFormUtils.getFieldJSONObject(dataFields, "mother_mentor_client_number");

            if (clientID != null) {
                clientID.put("mask", "##-##-##-######-#/KK/" + Calendar.getInstance().get(Calendar.YEAR) + "/#");
            }

            if (clientIdUkoma != null) {
                clientIdUkoma.put("mask", "##-##-##-######-#/UK/" + Calendar.getInstance().get(Calendar.YEAR) + "/#");
            }

            jsonForm.getJSONObject("metadata").put("encounter_location", currentLocationId);
            Intent intent = new Intent(this, JsonWizardFormActivity.class);
            intent.putExtra("json", jsonForm.toString());

            Form form = new Form();
            form.setWizard(true);
            form.setNextLabel("Next");
            form.setPreviousLabel("Previous");
            form.setSaveLabel("Save");
            form.setHideSaveLabel(true);

            intent.putExtra("form", form);
            startActivityForResult(intent, Constants.REQUEST_CODE_GET_JSON);
        }
    }


    @Override
    public void onDialogOptionUpdated(String jsonString) {
        Timber.v("onDialogOptionUpdated %s", jsonString);
    }

    @Override
    public Context getMyContext() {
        return this;
    }
}
