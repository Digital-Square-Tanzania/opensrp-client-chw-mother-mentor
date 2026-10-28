package org.smartregister.chw.mothermentor.actionhelper;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.model.BaseMotherMentorVisitAction;

/** Shared validation for a single partner test; each disease has its own action helper. */
public abstract class BaseMotherMentorPartnerTestActionHelper extends BaseMotherMentorActionHelper {
    private final String testedKey;
    private final String resultKey;
    private final String reasonKey;
    private final String otherKey;
    private final boolean alreadyInfectedAllowed;
    private final Runnable onTestChanged;

    protected BaseMotherMentorPartnerTestActionHelper(
            String testedKey,
            String resultKey,
            String reasonKey,
            String otherKey,
            boolean alreadyInfectedAllowed,
            Runnable onTestChanged) {
        this.testedKey = testedKey;
        this.resultKey = resultKey;
        this.reasonKey = reasonKey;
        this.otherKey = otherKey;
        this.alreadyInfectedAllowed = alreadyInfectedAllowed;
        this.onTestChanged = onTestChanged;
    }

    @Override
    public void onPayloadReceived(BaseMotherMentorVisitAction action) {
        if (onTestChanged != null) onTestChanged.run();
    }

    @Override
    public String evaluateSubTitle() {
        return "yes".equals(getValue(testedKey))
                ? translate("mothermentor_partner_result_", getValue(resultKey))
                : translate("mothermentor_partner_no_test_reason_", getValue(reasonKey));
    }

    @Override
    public BaseMotherMentorVisitAction.Status evaluateStatusOnPayload() {
        if ("yes".equals(getValue(testedKey))) {
            String result = getValue(resultKey);
            return completed("positive".equals(result) || "negative".equals(result));
        }
        String reason = getValue(reasonKey);
        return completed(
                "no".equals(getValue(testedKey))
                        && ("stock_out".equals(reason)
                                || "refused".equals(reason)
                                || "other".equals(reason)
                                || (alreadyInfectedAllowed && "already_infected".equals(reason)))
                        && (!"other".equals(reason)
                                || StringUtils.isNotBlank(getValue(otherKey))));
    }

    @Override
    public String postProcess(String source) {
        if ("yes".equals(getValue(testedKey))) {
            return clearField(clearField(source, reasonKey), otherKey);
        }
        source = clearField(source, resultKey);
        return "other".equals(getValue(reasonKey)) ? source : clearField(source, otherKey);
    }
}
