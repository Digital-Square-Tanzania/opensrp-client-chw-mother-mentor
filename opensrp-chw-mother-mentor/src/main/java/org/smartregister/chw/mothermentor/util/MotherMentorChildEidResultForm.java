package org.smartregister.chw.mothermentor.util;

import android.content.Context;

import org.joda.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;

import java.util.Map;

public final class MotherMentorChildEidResultForm {
    public static final String FORM_NAME = "mothermentor_child_eid_record_result";

    private MotherMentorChildEidResultForm() {}

    public static void prepare(JSONObject form, Context context, String stageCode, LocalDate sampleDate,
            String state) throws JSONException {
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_PENDING_TEST, stageCode);
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_PENDING_TEST_DATE, sampleDate.toString());
        MotherMentorMotherChildUtils.put(form, DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE, state);
        MotherMentorMotherChildUtils.put(form, "child_eid_result_sample_label",
                MotherMentorChildEidLabels.test(context, stageCode));
        MotherMentorMotherChildUtils.put(form, "child_eid_result_sample_date_label",
                MotherMentorMotherPregnancyUtils.displayDate(sampleDate));
        JSONObject receipt = MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MTOTO_RESULT_RECEIVED_DATE);
        receipt.put("min_date", sampleDate.toString("dd-MM-yyyy"));

        if (MotherMentorHeiSchedule.Stage.fromCode(stageCode)
                == MotherMentorHeiSchedule.Stage.EIGHTEEN_MONTHS) {
            JSONArray options = MotherMentorMotherChildUtils.field(form, DBConstants.KEY.MTOTO_CONFIRMATORY_RESULT)
                    .getJSONArray("options");
            for (int i = 0; i < options.length(); i++) {
                if ("pending".equals(options.getJSONObject(i).getString("key"))) {
                    options.remove(i);
                    break;
                }
            }
        }
    }

    public static int receiptDateError(Map<String, String> answers, LocalDate sampleDate) {
        if (MotherMentorChildEidReturnVisit.AWAITING_CONFIRM.equals(
                answers.get(DBConstants.KEY.MTOTO_RUFAA_STATE_BEFORE))) return 0;
        return receiptDateError(answers.get(DBConstants.KEY.MTOTO_RESULT_RECEIVED_DATE), sampleDate, LocalDate.now());
    }

    static int receiptDateError(String value, LocalDate sampleDate, LocalDate latestDate) {
        LocalDate received = MotherMentorHeiSchedule.parseDate(value);
        if (received == null || received.isAfter(latestDate)) return R.string.mothermentor_eid_received_invalid_date;
        return sampleDate != null && received.isBefore(sampleDate) ? R.string.mothermentor_eid_received_before_test : 0;
    }
}
