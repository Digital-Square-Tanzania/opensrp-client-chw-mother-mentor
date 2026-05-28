package org.smartregister.chw.mothermentor.interactor;


import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorInvestigationActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorSampleActionHelper;
import org.smartregister.chw.mothermentor.actionhelper.MotherMentorSourceActionHelper;
import org.smartregister.chw.mothermentor.contract.BaseMotherMentorVisitContract;
import org.smartregister.chw.mothermentor.domain.VisitDetail;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.AppExecutors;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.sync.helper.ECSyncHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

public class BaseMotherMentorServiceVisitInteractor extends BaseMotherMentorVisitInteractor {

    protected BaseMotherMentorVisitContract.InteractorCallBack callBack;

    String visitType;
    private final LinkedHashMap<String, BaseMotherMentorVisitAction> actionList;
    protected AppExecutors appExecutors;
    private ECSyncHelper syncHelper;
    private Context mContext;
    private MotherMentorInvestigationActionHelper contactTbInvestigationHelper;


    @VisibleForTesting
    public BaseMotherMentorServiceVisitInteractor(AppExecutors appExecutors, MotherMentorLibrary MotherMentorLibrary, ECSyncHelper syncHelper) {
        this.appExecutors = appExecutors;
        this.mContext = MotherMentorLibrary.getInstance().context().applicationContext();
        this.syncHelper = syncHelper;
        this.actionList = new LinkedHashMap<>();
    }

    public BaseMotherMentorServiceVisitInteractor(String visitType) {
        this(new AppExecutors(), MotherMentorLibrary.getInstance(), MotherMentorLibrary.getInstance().getEcSyncHelper());
        this.visitType = visitType;
    }

    @Override
    protected String getCurrentVisitType() {
        if (StringUtils.isNotBlank(visitType)) {
            return visitType;
        }
        return super.getCurrentVisitType();
    }

    /**
     * this method is used to list all actions
     * @param callBack
     */
    @Override
    protected void populateActionList(BaseMotherMentorVisitContract.InteractorCallBack callBack) {
        this.callBack = callBack;
        final Runnable runnable = () -> {
            try {
                evaluateMotherMentorSource(details);
                evaluateContactTbInvestigation(details);
                evaluateContactMotherMentorInvestigation(details);
                evaluateMotherMentorSample(details);

            } catch (BaseMotherMentorVisitAction.ValidationException e) {
                Timber.e(e);
            }

            appExecutors.mainThread().execute(() -> callBack.preloadActions(actionList));
        };

        appExecutors.diskIO().execute(runnable);
    }

    /**
     * this action deals Type of Contact (Njia ya kuchunguza Kifua Kikuu)
     * @param details
     * @throws BaseMotherMentorVisitAction.ValidationException
     */
    private void evaluateMotherMentorSource(Map<String, List<VisitDetail>> details) throws BaseMotherMentorVisitAction.ValidationException {

        MotherMentorSourceActionHelper actionHelper = new MotherMentorSourceActionHelper(mContext, memberObject);
        BaseMotherMentorVisitAction action = getBuilder(context.getString(R.string.mothermentor_source))
                .withOptional(false)
                .withDetails(details)
                .withHelper(actionHelper)
                .withFormName(Constants.MotherMentor_FOLLOWUP_FORMS.MOTHERMENTOR_INDEX_CLIENT_DETAILS_SOURCE)
                .build();
        actionList.put(context.getString(R.string.mothermentor_source), action);

    }

    /**
     * this action deals with TB Investigation (Uchunguzi wa Kifua Kikuu)
     * @param details
     * @throws BaseMotherMentorVisitAction.ValidationException
     */
    private void evaluateContactTbInvestigation(Map<String, List<VisitDetail>> details) throws BaseMotherMentorVisitAction.ValidationException {

        MotherMentorInvestigationActionHelper actionHelper = new MotherMentorInvestigationActionHelper(mContext, memberObject);
        contactTbInvestigationHelper = actionHelper;
        BaseMotherMentorVisitAction action = getBuilder(context.getString(R.string.mothermentor_contact_tb_investigation))
                .withOptional(false)
                .withDetails(details)
                .withHelper(actionHelper)
                .withValidator(getSourceContactTypeValidator("tb"))
                .withFormName(Constants.MotherMentor_FOLLOWUP_FORMS.MOTHERMENTOR_CONTACT_TB_INVESTIGATION)
                .build();
        actionList.put(context.getString(R.string.mothermentor_contact_tb_investigation), action);
    }

    /**
     * this action deals with MotherMentor Investigation (Uchunguzi wa Ukoma)
     * @param details
     * @throws BaseMotherMentorVisitAction.ValidationException
     */
    private void evaluateContactMotherMentorInvestigation(Map<String, List<VisitDetail>> details) throws BaseMotherMentorVisitAction.ValidationException {

        MotherMentorInvestigationActionHelper actionHelper = new MotherMentorInvestigationActionHelper(mContext, memberObject);
        BaseMotherMentorVisitAction action = getBuilder(context.getString(R.string.mothermentor_contact_mother_mentor_investigation))
                .withOptional(false)
                .withDetails(details)
                .withHelper(actionHelper)
                .withValidator(getSourceContactTypeValidator("mother_mentor"))
                .withFormName(Constants.MotherMentor_FOLLOWUP_FORMS.MOTHERMENTOR_CONTACT_LEPROSY_INVESTIGATION)
                .build();
        actionList.put(context.getString(R.string.mothermentor_contact_mother_mentor_investigation), action);
    }

    /**
     * this action deals with sample collection (kuchukua sampuli)
     * @param details
     * @throws BaseMotherMentorVisitAction.ValidationException
     */
    private void evaluateMotherMentorSample(Map<String, List<VisitDetail>> details) throws BaseMotherMentorVisitAction.ValidationException {

        MotherMentorSampleActionHelper actionHelper = new MotherMentorSampleActionHelper(mContext, memberObject, contactTbInvestigationHelper);
        BaseMotherMentorVisitAction action = getBuilder(context.getString(R.string.mothermentor_sample))
                .withOptional(false)
                .withDetails(details)
                .withHelper(actionHelper)
                .withValidator(getSampleEligibilityValidator(actionHelper))
                .withFormName(Constants.MotherMentor_FOLLOWUP_FORMS.MOTHERMENTOR_SAMPLE)
                .build();
        actionList.put(context.getString(R.string.mothermentor_sample), action);
    }

    private BaseMotherMentorVisitAction.Validator getSourceContactTypeValidator(final String requiredType) {
        return new BaseMotherMentorVisitAction.Validator() {
            @Override
            public boolean isValid(String key) {
                return isSourceContactTypeSelected(requiredType);
            }

            @Override
            public boolean isEnabled(String key) {
                return isValid(key);
            }

            @Override
            public void onChanged(String key) {
                // UI refresh handles visibility changes when data updates
            }
        };
    }

    private boolean isSourceContactTypeSelected(String requiredType) {
        if (StringUtils.isBlank(requiredType) || context == null) {
            return false;
        }

        BaseMotherMentorVisitAction sourceAction = actionList.get(context.getString(R.string.mothermentor_source));
        if (sourceAction == null) {
            return false;
        }

        String payload = sourceAction.getJsonPayload();
        if (StringUtils.isBlank(payload)) {
            return false;
        }

        try {
            JSONObject jsonObject = new JSONObject(payload);
            Set<String> contactTypes = extractContactTypes(jsonObject);

            String normalizedRequiredType = normalizeContactType(requiredType);
            if (StringUtils.isBlank(normalizedRequiredType)) {
                normalizedRequiredType = requiredType.toLowerCase(Locale.ENGLISH);
            }

            return contactTypes.contains(normalizedRequiredType);
        } catch (Exception e) {
            Timber.e(e);
        }

        return false;
    }

    private Set<String> extractContactTypes(JSONObject jsonObject) {
        Set<String> normalizedTypes = new HashSet<>();
        if (jsonObject == null) {
            return normalizedTypes;
        }

        String[] candidateKeys = new String[]{
                "index_case_condition_types",
                "relationship_to_index_client"
        };

        for (String key : candidateKeys) {
            List<String> selections = extractSelections(jsonObject, key);
            for (String selection : selections) {
                if (StringUtils.isBlank(selection)) {
                    continue;
                }

                String normalizedSelection = selection.trim().toLowerCase(Locale.ENGLISH);
                normalizedTypes.add(normalizedSelection);

                String normalizedContactType = normalizeContactType(selection);
                if (StringUtils.isNotBlank(normalizedContactType)) {
                    normalizedTypes.add(normalizedContactType);
                }
            }
        }

        return normalizedTypes;
    }

    private String normalizeContactType(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ENGLISH);
        switch (normalized) {
            case "tb":
            case "high_tb_burden_area":
                return "tb";
            case "mother_mentor":
                return "mother_mentor";
            default:
                return null;
        }
    }

    private List<String> extractSelections(JSONObject jsonObject, String key) {
        List<String> selections = new ArrayList<>();
        if (jsonObject == null || StringUtils.isBlank(key)) {
            return selections;
        }

        try {
            JSONObject fieldObject = FormUtils.getFieldFromForm(jsonObject, key);
            if (fieldObject == null || !fieldObject.has(JsonFormConstants.VALUE)) {
                return selections;
            }

            Object rawValue = fieldObject.get(JsonFormConstants.VALUE);
            if (rawValue instanceof JSONArray) {
                JSONArray array = (JSONArray) rawValue;
                for (int i = 0; i < array.length(); i++) {
                    String value = array.optString(i);
                    if (StringUtils.isNotBlank(value)) {
                        selections.add(value.trim());
                    }
                }
            } else if (rawValue instanceof String) {
                String value = (String) rawValue;
                if (StringUtils.isNotBlank(value)) {
                    String[] tokens = value.split("[,\\s]+");
                    for (String token : tokens) {
                        if (StringUtils.isNotBlank(token)) {
                            selections.add(token.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e);
        }

        return selections;
    }

    private BaseMotherMentorVisitAction.Validator getSampleEligibilityValidator(final MotherMentorSampleActionHelper sampleActionHelper) {
        return new BaseMotherMentorVisitAction.Validator() {
            @Override
            public boolean isValid(String key) {
                return sampleActionHelper != null
                        && isSourceContactTypeSelected("tb")
                        && sampleActionHelper.isEligibleForSampleCollection();
            }

            @Override
            public boolean isEnabled(String key) {
                return isValid(key);
            }

            @Override
            public void onChanged(String key) {
                // No-op
            }
        };
    }

    @Override
    protected String getEncounterType() {
        return Constants.EVENT_TYPE.MOTHERMENTOR_CONTACT_VISIT;
    }

    @Override
    protected String getTableName() {
        return Constants.TABLES.MOTHERMENTOR_SERVICES;
    }




}
