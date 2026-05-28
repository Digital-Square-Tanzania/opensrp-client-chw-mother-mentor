package org.smartregister.chw.mothermentor.model;

import org.json.JSONObject;
import org.smartregister.chw.mothermentor.contract.MotherMentorRegisterContract;
import org.smartregister.chw.mothermentor.util.MotherMentorJsonFormUtils;

public class BaseMotherMentorRegisterModel implements MotherMentorRegisterContract.Model {

    @Override
    public JSONObject getFormAsJson(String formName, String entityId, String currentLocationId) throws Exception {
        JSONObject jsonObject = MotherMentorJsonFormUtils.getFormAsJson(formName);
        MotherMentorJsonFormUtils.getRegistrationForm(jsonObject, entityId, currentLocationId);

        return jsonObject;
    }

}
