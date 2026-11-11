package org.smartregister.chw.mothermentor.actionhelper;

import static org.smartregister.chw.mothermentor.util.MotherMentorMotherPregnancyUtils.displayDate;

import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.FACILITY_RETURN_DATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_NOT_RETURNED_REASON_OTHER;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_RETURN_AGREEMENT;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.IIT_RETURN_STATE;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.OTHER;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.RETURNED_TO_FACILITY;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON;
import static org.smartregister.chw.mothermentor.util.DBConstants.KEY.SERVICE_INTERRUPTION_REASON_OTHER;

import com.vijay.jsonwizard.constants.JsonFormConstants;
import com.vijay.jsonwizard.utils.FormUtils;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.json.JSONException;
import org.json.JSONObject;
import org.smartregister.chw.mothermentor.R;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;
import org.smartregister.chw.mothermentor.util.MotherMentorHeiSchedule;
import org.smartregister.chw.mothermentor.util.MotherMentorIitReturnUtils;
import org.smartregister.chw.mothermentor.util.MotherMentorIitState;
import org.smartregister.chw.mothermentor.util.MotherMentorMotherVisitUtils;

import timber.log.Timber;

/** Stages the questions for the return phase recorded before the visit starts. */
public class MotherMentorIitReturnActionHelper extends BaseMotherMentorActionHelper {
    private final String returnState;
    private final String missedDate;
    private final int refusedCount;
    private final int notReturnedCount;
    private final LocalDate serviceDate;

    public MotherMentorIitReturnActionHelper(String returnState, String missedDate,
            int refusedCount, int notReturnedCount, LocalDate serviceDate) {
        this.returnState = MotherMentorIitState.REFERRED.equals(returnState)
                ? MotherMentorIitState.REFERRED : MotherMentorIitState.NONE;
        this.missedDate = missedDate;
        this.refusedCount = refusedCount;
        this.notReturnedCount = notReturnedCount;
        this.serviceDate = serviceDate == null ? LocalDate.now() : serviceDate;
    }

    @Override
    public String getPreProcessed() {
        if (StringUtils.isBlank(sourceForm)) return null;
        try {
            JSONObject form = new JSONObject(sourceForm);
            setField(form, IIT_RETURN_STATE, JsonFormConstants.VALUE, returnState);
            LocalDate missed = MotherMentorHeiSchedule.parseDate(missedDate);
            setField(form, "iit_missed_appointment_note", "text", missed == null
                    ? context.getString(R.string.mothermentor_iit_return_missed_appointment_unknown)
                    : context.getString(R.string.mothermentor_iit_missed_appointment_return_banner,
                            displayDate(missed),
                            Math.max(0, Days.daysBetween(missed, serviceDate).getDays())));
            if (missed != null) {
                setField(form, FACILITY_RETURN_DATE, "min_date", missed.toString("dd-MM-yyyy"));
            }
            setField(form, FACILITY_RETURN_DATE, "max_date", serviceDate.toString("dd-MM-yyyy"));
            applyCounterNote(form, "iit_refused_note", refusedCount,
                    R.string.mothermentor_iit_refusal_strike,
                    R.string.mothermentor_iit_refusal_close);
            applyCounterNote(form, "iit_not_returned_note", notReturnedCount,
                    R.string.mothermentor_iit_not_returned_strike,
                    R.string.mothermentor_iit_not_returned_close);
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e, "Unable to prepare the Mother IIT return form");
            return sourceForm;
        }
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        return completed(MotherMentorIitReturnUtils.isComplete(
                MotherMentorMotherVisitUtils.formValues(payload), returnState, missedDate, serviceDate));
    }

    @Override
    public String evaluateSubTitle() {
        if (MotherMentorIitState.NONE.equals(returnState)) {
            return translate("mothermentor_iit_return_agreement_", getValue(IIT_RETURN_AGREEMENT));
        }
        String answer = getValue(RETURNED_TO_FACILITY);
        String label = translate("mothermentor_answer_", answer);
        String date = "yes".equals(answer) ? getValue(FACILITY_RETURN_DATE) : null;
        return StringUtils.isBlank(label) || StringUtils.isBlank(date) ? label : label + " · " + date;
    }

    @Override
    public String postProcess(String source) {
        if (StringUtils.isBlank(source)) return null;
        try {
            JSONObject form = new JSONObject(source);
            setField(form, IIT_RETURN_STATE, JsonFormConstants.VALUE, returnState);
            if (MotherMentorIitState.NONE.equals(returnState)) {
                clear(form, RETURNED_TO_FACILITY, FACILITY_RETURN_DATE, IIT_NOT_RETURNED_REASON,
                        IIT_NOT_RETURNED_REASON_OTHER);
                if (!OTHER.equals(getValue(SERVICE_INTERRUPTION_REASON))) {
                    clear(form, SERVICE_INTERRUPTION_REASON_OTHER);
                }
            } else {
                clear(form, SERVICE_INTERRUPTION_REASON, SERVICE_INTERRUPTION_REASON_OTHER,
                        IIT_RETURN_AGREEMENT);
                String answer = getValue(RETURNED_TO_FACILITY);
                if (!"yes".equals(answer)) clear(form, FACILITY_RETURN_DATE);
                if (!"no".equals(answer)) clear(form, IIT_NOT_RETURNED_REASON);
                if (!"no".equals(answer) || !OTHER.equals(getValue(IIT_NOT_RETURNED_REASON))) {
                    clear(form, IIT_NOT_RETURNED_REASON_OTHER);
                }
            }
            return form.toString();
        } catch (JSONException e) {
            Timber.e(e, "Unable to clear inactive Mother IIT return answers");
            return null;
        }
    }

    private void applyCounterNote(JSONObject form, String key, int count, int strike, int close)
            throws JSONException {
        setField(form, key, "text", count >= 2 ? context.getString(close)
                : context.getString(strike, count));
        if (count >= 2) setField(form, key, "toaster_type", "problem");
    }

    private static void clear(JSONObject form, String... keys) throws JSONException {
        for (String key : keys) setField(form, key, JsonFormConstants.VALUE, "");
    }

    private static void setField(JSONObject form, String key, String property, String value)
            throws JSONException {
        JSONObject field = FormUtils.getFieldFromForm(form, key);
        if (field != null) field.put(property, value);
    }
}
