package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.util.JsonFormUtils;

import java.util.Iterator;

/** Reads native-form answers and legacy household payloads without deciding action completion. */
final class MotherMentorActionAnswers {
    private MotherMentorActionAnswers() {}

    static boolean hasAnswer(JSONObject form, String key, boolean checkbox) {
        if (form == null) return false;
        if (StringUtils.isNotBlank(JsonFormUtils.getValue(form, key))) return true;
        if (checkbox) return StringUtils.isNotBlank(JsonFormUtils.getCheckBoxValue(form, key));
        return hasLegacyAnswer(form, key);
    }

    private static boolean hasLegacyAnswer(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            if (object.has(key) && !object.isNull(key)) {
                Object value = object.opt(key);
                return value instanceof String
                        ? StringUtils.isNotBlank((String) value)
                        : value != null;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                if (hasLegacyAnswer(object.opt(keys.next()), key)) return true;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                if (hasLegacyAnswer(array.opt(i), key)) return true;
            }
        }
        return false;
    }
}
