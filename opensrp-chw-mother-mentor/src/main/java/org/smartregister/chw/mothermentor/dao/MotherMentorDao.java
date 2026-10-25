package org.smartregister.chw.mothermentor.dao;

import android.database.Cursor;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.chw.mothermentor.MotherMentorLibrary;
import org.smartregister.chw.mothermentor.domain.MemberObject;
import org.smartregister.chw.mothermentor.repository.VisitDetailsRepository;
import org.smartregister.chw.mothermentor.repository.VisitRepository;
import org.smartregister.chw.mothermentor.util.Constants;
import org.smartregister.chw.mothermentor.util.DBConstants;
import org.smartregister.chw.mothermentor.util.MotherMentorHouseholdVisitUtils;
import org.smartregister.commonregistry.CommonPersonObject;
import org.smartregister.commonregistry.CommonRepository;
import org.smartregister.dao.AbstractDao;
import org.smartregister.util.Utils;

import timber.log.Timber;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MotherMentorDao extends AbstractDao {
    private static final SimpleDateFormat df =
            new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private static final String ISO_DATE_WITH_MILLIS_TZ = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final String ISO_DATE_WITH_TZ = "yyyy-MM-dd'T'HH:mm:ssXXX";
    private static final String ISO_DATE_WITH_MILLIS_TZ_NO_COLON = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final String ISO_DATE_WITH_TZ_NO_COLON = "yyyy-MM-dd'T'HH:mm:ssZ";

    private static final DataMap<MemberObject> memberObjectMap =
            cursor -> {
                MemberObject memberObject = new MemberObject();

                memberObject.setFirstName(getCursorValue(cursor, DBConstants.KEY.FIRST_NAME, ""));
                memberObject.setMiddleName(getCursorValue(cursor, DBConstants.KEY.MIDDLE_NAME, ""));
                memberObject.setLastName(getCursorValue(cursor, DBConstants.KEY.LAST_NAME, ""));
                memberObject.setAddress(getCursorValue(cursor, DBConstants.KEY.VILLAGE_TOWN));
                memberObject.setGender(getCursorValue(cursor, DBConstants.KEY.GENDER));
                memberObject.setMartialStatus(
                        getCursorValue(cursor, DBConstants.KEY.MARITAL_STATUS));
                memberObject.setUniqueId(getCursorValue(cursor, DBConstants.KEY.UNIQUE_ID, ""));
                memberObject.setCtcNumber(getCursorValue(cursor, DBConstants.KEY.CTC_NUMBER, ""));
                memberObject.setDob(getCursorValue(cursor, DBConstants.KEY.DOB));
                memberObject.setFamilyBaseEntityId(
                        getCursorValue(cursor, DBConstants.KEY.FAMILY_BASE_ENTITY_ID, ""));
                memberObject.setRelationalId(
                        getCursorValue(cursor, DBConstants.KEY.RELATIONAL_ID, ""));
                memberObject.setPrimaryCareGiver(
                        getCursorValue(cursor, DBConstants.KEY.PRIMARY_CARE_GIVER));
                memberObject.setFamilyName(getCursorValue(cursor, DBConstants.KEY.FAMILY_NAME, ""));
                memberObject.setPhoneNumber(
                        getCursorValue(cursor, DBConstants.KEY.PHONE_NUMBER, ""));
                memberObject.setMotherMentorTestDate(
                        getCursorValueAsDate(cursor, DBConstants.KEY.MOTHERMENTOR_TEST_DATE, df));
                memberObject.setBaseEntityId(
                        getCursorValue(cursor, DBConstants.KEY.BASE_ENTITY_ID, ""));
                memberObject.setFamilyHead(getCursorValue(cursor, DBConstants.KEY.FAMILY_HEAD, ""));
                memberObject.setFamilyHeadPhoneNumber(
                        getCursorValue(cursor, DBConstants.KEY.PCG_PHONE_NUMBER, ""));
                memberObject.setFamilyHeadPhoneNumber(
                        getCursorValue(cursor, DBConstants.KEY.FAMILY_HEAD_PHONE_NUMBER, ""));
                memberObject.setEnrollmentDate(
                        getCursorValue(cursor, DBConstants.KEY.ENROLLMENT_DATE, ""));

                String familyHeadName =
                        getCursorValue(cursor, DBConstants.KEY.FAMILY_HEAD_FIRST_NAME, "")
                                + " "
                                + getCursorValue(
                                        cursor, DBConstants.KEY.FAMILY_HEAD_MIDDLE_NAME, "");

                familyHeadName =
                        (familyHeadName.trim()
                                        + " "
                                        + getCursorValue(
                                                cursor, DBConstants.KEY.FAMILY_HEAD_LAST_NAME, ""))
                                .trim();
                memberObject.setFamilyHeadName(familyHeadName);

                String familyPcgName =
                        getCursorValue(cursor, DBConstants.KEY.PCG_FIRST_NAME, "")
                                + " "
                                + getCursorValue(cursor, DBConstants.KEY.PCG_MIDDLE_NAME, "");

                familyPcgName =
                        (familyPcgName.trim()
                                        + " "
                                        + getCursorValue(cursor, DBConstants.KEY.PCG_LAST_NAME, ""))
                                .trim();
                memberObject.setPrimaryCareGiverName(familyPcgName);

                memberObject.setTbClientNumber(
                        getCursorValue(cursor, DBConstants.KEY.TB_CLIENT_NUMBER, ""));
                memberObject.setMotherMentorClientNumber(
                        getCursorValue(cursor, DBConstants.KEY.LEPROSY_CLIENT_NUMBER, ""));

                return memberObject;
            };

    /** Loads a household profile using the host app's configured family table. */
    public static MemberObject getHouseholdMember(String baseEntityId, String householdTable) {
        if (StringUtils.isBlank(baseEntityId) || StringUtils.isBlank(householdTable)) {
            return null;
        }
        try {
            CommonRepository repository =
                    MotherMentorLibrary.getInstance().context().commonrepository(householdTable);
            if (repository == null) {
                return null;
            }

            CommonPersonObject personObject = repository.findByBaseEntityId(baseEntityId);
            if (personObject != null) {
                MemberObject memberObject = new MemberObject();
                memberObject.setBaseEntityId(personObject.getCaseId());
                memberObject.setFamilyBaseEntityId(personObject.getCaseId());
                memberObject.setFirstName(
                        Utils.getValue(
                                personObject.getColumnmaps(), DBConstants.KEY.FIRST_NAME, false));
                memberObject.setLastName(
                        Utils.getValue(
                                personObject.getColumnmaps(), DBConstants.KEY.LAST_NAME, false));
                memberObject.setUniqueId(
                        Utils.getValue(
                                personObject.getColumnmaps(), DBConstants.KEY.UNIQUE_ID, false));
                memberObject.setAddress(
                        Utils.getValue(
                                personObject.getColumnmaps(), DBConstants.KEY.VILLAGE_TOWN, false));
                memberObject.setFamilyHead(
                        Utils.getValue(
                                personObject.getColumnmaps(), DBConstants.KEY.FAMILY_HEAD, false));
                memberObject.setPrimaryCareGiver(
                        Utils.getValue(
                                personObject.getColumnmaps(),
                                DBConstants.KEY.PRIMARY_CARE_GIVER,
                                false));
                memberObject.setPhoneNumber(
                        Utils.getValue(
                                personObject.getColumnmaps(), DBConstants.KEY.PHONE_NUMBER, false));
                return memberObject;
            }
        } catch (Exception e) {
            Timber.e(e, "Unable to load Mother Mentor household");
        }
        return null;
    }

    /** Loads the active Child EID enrollment used to recommend a follow-up test. */
    public static Map<String, String> getChildEidTestEnrollmentValues(String baseEntityId) {
        Map<String, String> values = new HashMap<>();
        if (StringUtils.isBlank(baseEntityId)) return values;
        String query =
                "SELECT m."
                        + DBConstants.KEY.DOB
                        + ", e."
                        + DBConstants.KEY.WEANING_DATE
                        + ", e."
                        + DBConstants.KEY.LAST_TEST
                        + " FROM "
                        + Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID
                        + " e INNER JOIN ec_family_member m ON m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = e."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " WHERE e."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = ? AND e."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 ORDER BY e."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";
        try (Cursor cursor =
                MotherMentorLibrary.getInstance()
                        .getRepository()
                        .getReadableDatabase()
                        .rawQuery(query, new String[] {baseEntityId})) {
            if (cursor.moveToFirst()) {
                for (String key :
                        new String[] {
                            DBConstants.KEY.DOB,
                            DBConstants.KEY.WEANING_DATE,
                            DBConstants.KEY.LAST_TEST
                        }) {
                    int index = cursor.getColumnIndexOrThrow(key);
                    values.put(key, cursor.isNull(index) ? null : cursor.getString(index));
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Unable to load Child EID enrollment data");
        }
        return values;
    }

    public static String getMotherMentorClientStatus(String baseEntityId) {
        String status =
                getClientStatusFromTable(Constants.TABLES.MOTHERMENTOR_ENROLLMENT, baseEntityId);
        if (StringUtils.isBlank(status)) {
            status =
                    getClientStatusFromTable(
                            Constants.TABLES.MOTHERMENTOR_ENROLLMENT, baseEntityId);
        }

        if (StringUtils.isBlank(status)) {
            if (getContact(baseEntityId) != null) {
                status = "contact";
            } else {
                status = "client";
            }
        }

        return StringUtils.defaultString(status);
    }

    private static String getClientStatusFromTable(String tableName, String baseEntityId) {
        String sql =
                "SELECT "
                        + DBConstants.KEY.STATUS
                        + " FROM "
                        + tableName
                        + " WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<String> dataMap = cursor -> getCursorValue(cursor, DBConstants.KEY.STATUS);
        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty()) {
            return res.get(0);
        }
        return null;
    }

    public static String getTbScreeningStatus(String baseEntityId) {
        String sql =
                "SELECT "
                        + DBConstants.KEY.SCREENING_STATUS
                        + ", "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " FROM ec_mothermentor_enrollment WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' UNION ALL SELECT "
                        + DBConstants.KEY.SCREENING_STATUS
                        + ", "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " FROM ec_mothermentor_screening WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<String> dataMap =
                cursor -> getCursorValue(cursor, DBConstants.KEY.SCREENING_STATUS);
        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty() && res.get(0) != null) {
            return res.get(0);
        }
        return null;
    }

    public static boolean isTbPresumptiveClient(String baseEntityId) {
        String screeningStatus = getTbScreeningStatus(baseEntityId);
        if (StringUtils.isBlank(screeningStatus)) {
            return false;
        }

        String normalizedStatus = screeningStatus.toLowerCase(Locale.ENGLISH);
        return normalizedStatus.contains("tb_presamptive")
                || normalizedStatus.contains("tb_presumptive");
    }

    public static boolean isMotherMentorPresumptiveClient(String baseEntityId) {
        String screeningStatus = getTbScreeningStatus(baseEntityId);
        if (StringUtils.isBlank(screeningStatus)) {
            return false;
        }

        String normalizedStatus = screeningStatus.toLowerCase(Locale.ENGLISH);
        return normalizedStatus.contains("leprocy_presamptive")
                || normalizedStatus.contains("leprocy_presumptive")
                || normalizedStatus.contains("mother_mentor_presamptive")
                || normalizedStatus.contains("mother_mentor_presumptive");
    }

    public static String getMotherMentorObservationResults(String baseEntityId) {
        ObservationResults observationResults = getLatestObservationResults(baseEntityId);
        if (observationResults == null) {
            return "";
        }

        String tbSampleTestResults = observationResults.getTbSampleTestResults();
        if (StringUtils.isNotBlank(tbSampleTestResults)) {
            return tbSampleTestResults;
        }

        return StringUtils.defaultString(observationResults.getClinicalDecision());
    }

    public static ObservationResults getLatestObservationResults(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return null;
        }

        String sql =
                "SELECT "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + ", "
                        + DBConstants.KEY.INVESTIGATION_TYPE
                        + ", "
                        + DBConstants.KEY.TB_DIAGNOSTIC_TEST_TYPE
                        + ", "
                        + DBConstants.KEY.TB_SAMPLE_TEST_RESULTS
                        + ", "
                        + DBConstants.KEY.CLINICAL_DECISION
                        + ", "
                        + DBConstants.KEY.MOTHER_MENTOR_DIAGNOSTIC_METHOD
                        + ", "
                        + DBConstants.KEY.TB_TREATMENT_START_DATE
                        + ", "
                        + DBConstants.KEY.MOTHER_MENTOR_TREATMENT_START_DATE
                        + ", "
                        + DBConstants.KEY.MOTHER_MENTOR_INVESTIGATION_RESULTS
                        + " FROM ec_mothermentor_observation_results p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC";

        DataMap<ObservationResults> dataMap =
                cursor -> {
                    String tbSampleTestResults =
                            getCursorValue(cursor, DBConstants.KEY.TB_SAMPLE_TEST_RESULTS, "");
                    String clinicalDecision =
                            getCursorValue(cursor, DBConstants.KEY.CLINICAL_DECISION, "");
                    String mother_mentorInvestigationResults =
                            getCursorValue(
                                    cursor,
                                    DBConstants.KEY.MOTHER_MENTOR_INVESTIGATION_RESULTS,
                                    "");
                    boolean poorQualitySample =
                            StringUtils.containsIgnoreCase(
                                    tbSampleTestResults, "poor_quality_sample");

                    return new ObservationResults(
                            getCursorValue(cursor, DBConstants.KEY.LAST_INTERACTED_WITH, ""),
                            getCursorValue(cursor, DBConstants.KEY.INVESTIGATION_TYPE, ""),
                            getCursorValue(cursor, DBConstants.KEY.TB_DIAGNOSTIC_TEST_TYPE, ""),
                            tbSampleTestResults,
                            clinicalDecision,
                            getCursorValue(
                                    cursor, DBConstants.KEY.MOTHER_MENTOR_DIAGNOSTIC_METHOD, ""),
                            getCursorValue(cursor, DBConstants.KEY.TB_TREATMENT_START_DATE, ""),
                            getCursorValue(
                                    cursor, DBConstants.KEY.MOTHER_MENTOR_TREATMENT_START_DATE, ""),
                            mother_mentorInvestigationResults,
                            poorQualitySample);
                };

        List<ObservationResults> res = readData(sql, dataMap);
        return mergeObservationResults(res);
    }

    protected static ObservationResults mergeObservationResults(
            List<ObservationResults> observationResults) {
        if (observationResults == null || observationResults.isEmpty()) {
            return null;
        }

        String lastInteractedWith = "";
        String investigationType = "";
        String tbDiagnosticTestType = "";
        String tbSampleTestResults = "";
        String clinicalDecision = "";
        String mother_mentorDiagnosticMethod = "";
        String tbTreatmentStartDate = "";
        String mother_mentorTreatmentStartDate = "";
        String mother_mentorInvestigationResults = "";

        for (ObservationResults current : observationResults) {
            if (current == null) {
                continue;
            }

            if (StringUtils.isBlank(lastInteractedWith)) {
                lastInteractedWith = StringUtils.defaultString(current.getLastInteractedWith());
            }

            investigationType =
                    pickFirstNonBlankValue(investigationType, current.getInvestigationType());
            tbDiagnosticTestType =
                    pickFirstNonBlankValue(tbDiagnosticTestType, current.getTbDiagnosticTestType());
            tbSampleTestResults =
                    pickFirstNonBlankValue(tbSampleTestResults, current.getTbSampleTestResults());
            clinicalDecision =
                    pickFirstNonBlankValue(clinicalDecision, current.getClinicalDecision());
            mother_mentorDiagnosticMethod =
                    pickFirstNonBlankValue(
                            mother_mentorDiagnosticMethod,
                            current.getMotherMentorDiagnosticMethod());
            tbTreatmentStartDate =
                    pickFirstNonBlankValue(tbTreatmentStartDate, current.getTbTreatmentStartDate());
            mother_mentorTreatmentStartDate =
                    pickFirstNonBlankValue(
                            mother_mentorTreatmentStartDate,
                            current.getMotherMentorTreatmentStartDate());
            mother_mentorInvestigationResults =
                    pickFirstNonBlankValue(
                            mother_mentorInvestigationResults,
                            current.getMotherMentorInvestigationResults());
        }

        boolean poorQualitySample =
                StringUtils.containsIgnoreCase(tbSampleTestResults, "poor_quality_sample");

        return new ObservationResults(
                lastInteractedWith,
                investigationType,
                tbDiagnosticTestType,
                tbSampleTestResults,
                clinicalDecision,
                mother_mentorDiagnosticMethod,
                tbTreatmentStartDate,
                mother_mentorTreatmentStartDate,
                mother_mentorInvestigationResults,
                poorQualitySample);
    }

    private static String pickFirstNonBlankValue(String currentValue, String candidateValue) {
        return StringUtils.isNotBlank(currentValue)
                ? currentValue
                : StringUtils.defaultString(candidateValue);
    }

    public static boolean isClientTbOrMotherMentorNegative(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return false;
        }

        ObservationResults observationResults = getLatestObservationResults(baseEntityId);
        if (observationResults == null) {
            return false;
        }

        String tbSampleResult =
                StringUtils.defaultString(observationResults.getTbSampleTestResults())
                        .toLowerCase(Locale.ENGLISH);
        String clinicalDecision =
                StringUtils.defaultString(observationResults.getClinicalDecision())
                        .toLowerCase(Locale.ENGLISH);
        String mother_mentorResult =
                StringUtils.defaultString(observationResults.getMotherMentorInvestigationResults())
                        .toLowerCase(Locale.ENGLISH);

        boolean tbNegative =
                "tb_dr_tb_undetected".equals(tbSampleResult)
                        || "non_suggestive".equals(clinicalDecision);
        boolean mother_mentorNegative = "no_mother_mentor_detected".equals(mother_mentorResult);
        return tbNegative || mother_mentorNegative;
    }

    /**
     * Close Mother Mentor screening records whose latest observation shows the client is
     * TB-negative. This uses the most recent entry in ec_mothermentor_observation_results per
     * client and closes only when TB results are negative and mother_mentor has not been confirmed
     * positive. All related open observation, follow-up, and visit records are closed alongside the
     * screening row.
     */
    public static void closeTbNegativeClients() {
        String baseEntitySubQuery =
                "SELECT scr."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " FROM ec_mothermentor_enrollment scr   INNER JOIN (    SELECT latest."
                        + DBConstants.KEY.ENTITY_ID
                        + ", latest."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + ",            lower(ifnull(latest."
                        + DBConstants.KEY.TB_SAMPLE_TEST_RESULTS
                        + ", '')) AS tb_result,            lower(ifnull(latest."
                        + DBConstants.KEY.CLINICAL_DECISION
                        + ", '')) AS clinical_result,            lower(ifnull(latest."
                        + DBConstants.KEY.MOTHER_MENTOR_INVESTIGATION_RESULTS
                        + ", '')) AS mother_mentor_result     FROM"
                        + " ec_mothermentor_observation_results latest     WHERE latest."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND latest."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " = (      SELECT MAX(inner_tbl."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + ") FROM ec_mothermentor_observation_results inner_tbl       WHERE"
                        + " inner_tbl."
                        + DBConstants.KEY.ENTITY_ID
                        + " = latest."
                        + DBConstants.KEY.ENTITY_ID
                        + " AND inner_tbl."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0    )  ) latest ON scr."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = latest."
                        + DBConstants.KEY.ENTITY_ID
                        + "   WHERE scr."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0     AND scr."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " <= latest."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + "     AND (latest.tb_result = 'tb_dr_tb_undetected' OR"
                        + " latest.clinical_result = 'non_suggestive' OR"
                        + " latest.mother_mentor_result = 'no_mother_mentor_detected') ";

        List<String> baseEntityIds =
                readData(
                        baseEntitySubQuery,
                        cursor -> getCursorValue(cursor, DBConstants.KEY.BASE_ENTITY_ID));
        if (baseEntityIds == null || baseEntityIds.isEmpty()) {
            return;
        }

        String joinedIds = "'" + String.join("','", baseEntityIds) + "'";

        String closeObservationSql =
                "UPDATE ec_mothermentor_observation_results SET "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 1 WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.ENTITY_ID
                        + " IN ("
                        + joinedIds
                        + ")";
        updateDB(closeObservationSql);

        String closeFollowUpSql =
                "UPDATE ec_mothermentor_followup_visit SET "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 1 WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.ENTITY_ID
                        + " IN ("
                        + joinedIds
                        + ")";
        updateDB(closeFollowUpSql);

        String closeVisitSql =
                "UPDATE ec_mothermentor_visit SET "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 1 WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.ENTITY_ID
                        + " IN ("
                        + joinedIds
                        + ")";
        updateDB(closeVisitSql);

        String closeScreeningSql =
                "UPDATE ec_mothermentor_enrollment SET "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 1 WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " IN ("
                        + joinedIds
                        + ")";
        updateDB(closeScreeningSql);
    }

    public static String getTBmother_mentorFollowUpVisit(String baseEntityId) {
        String sql =
                "SELECT "
                        + DBConstants.KEY.FOLLOW_UP_REASON
                        + " FROM ec_mothermentor_followup_visit p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<String> dataMap =
                cursor -> {
                    return getCursorValue(cursor, DBConstants.KEY.FOLLOW_UP_REASON);
                };

        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty() && res.get(0) != null) {
            return res.get(0);
        }
        return "";
    }

    public static boolean isClientDeceased(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return false;
        }

        String sql =
                "SELECT "
                        + DBConstants.KEY.FOLLOW_UP_OUTCOME
                        + " FROM ec_mothermentor_followup_visit p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<String> dataMap =
                cursor -> getCursorValue(cursor, DBConstants.KEY.FOLLOW_UP_OUTCOME, "");
        List<String> res = readData(sql, dataMap);
        if (res == null || res.isEmpty()) {
            return false;
        }

        return StringUtils.equalsIgnoreCase("client_deceased", res.get(0));
    }

    public static FollowUpVisit getLatestFollowUpVisit(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return null;
        }

        String sql =
                "SELECT "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + ", "
                        + DBConstants.KEY.FOLLOW_UP_REASON
                        + ", "
                        + DBConstants.KEY.FOLLOW_UP_OUTCOME
                        + ", "
                        + DBConstants.KEY.REASON_CLIENT_NOT_FOUND
                        + ", "
                        + DBConstants.KEY.RETURNED_TO_TREATMENT
                        + ", "
                        + DBConstants.KEY.TB_TREATMENT_START_DATE
                        + ", "
                        + DBConstants.KEY.SERVICE_ACCESS_CHALLENGES
                        + ", "
                        + DBConstants.KEY
                                .REASONS_FOR_NOT_RETURNING_TO_SERVICES_WHILE_NOT_FACING_CHALLENGES
                        + ", "
                        + DBConstants.KEY.CLIENT_CHALLENGE_TYPES
                        + ", "
                        + DBConstants.KEY.HEALTH_FACILITY_CHALLENGES_DETAIL
                        + ", "
                        + DBConstants.KEY.RETURN_TO_TREATMENT_PROMPT
                        + " FROM ec_mothermentor_followup_visit WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<FollowUpVisit> dataMap =
                cursor ->
                        new FollowUpVisit(
                                getCursorValue(cursor, DBConstants.KEY.LAST_INTERACTED_WITH, ""),
                                getCursorValue(cursor, DBConstants.KEY.FOLLOW_UP_REASON, ""),
                                getCursorValue(cursor, DBConstants.KEY.FOLLOW_UP_OUTCOME, ""),
                                getCursorValue(cursor, DBConstants.KEY.REASON_CLIENT_NOT_FOUND, ""),
                                getCursorValue(cursor, DBConstants.KEY.RETURNED_TO_TREATMENT, ""),
                                getCursorValue(cursor, DBConstants.KEY.TB_TREATMENT_START_DATE, ""),
                                getCursorValue(
                                        cursor, DBConstants.KEY.SERVICE_ACCESS_CHALLENGES, ""),
                                getCursorValue(
                                        cursor,
                                        DBConstants.KEY
                                                .REASONS_FOR_NOT_RETURNING_TO_SERVICES_WHILE_NOT_FACING_CHALLENGES,
                                        ""),
                                getCursorValue(cursor, DBConstants.KEY.CLIENT_CHALLENGE_TYPES, ""),
                                getCursorValue(
                                        cursor,
                                        DBConstants.KEY.HEALTH_FACILITY_CHALLENGES_DETAIL,
                                        ""),
                                getCursorValue(
                                        cursor, DBConstants.KEY.RETURN_TO_TREATMENT_PROMPT, ""));

        List<FollowUpVisit> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty()) {
            return res.get(0);
        }
        return null;
    }

    public static boolean hasFollowUpVisitWithTreatmentStartDate(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return false;
        }

        String sql =
                "SELECT count("
                        + DBConstants.KEY.ENTITY_ID
                        + ") count FROM ec_mothermentor_followup_visit WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' AND ifnull("
                        + DBConstants.KEY.TB_TREATMENT_START_DATE
                        + ", '') <> ''";

        DataMap<Integer> dataMap = cursor -> getCursorIntValue(cursor, "count");

        List<Integer> res = readData(sql, dataMap);
        if (res == null || res.size() != 1) {
            return false;
        }

        Integer count = res.get(0);
        return count != null && count > 0;
    }

    public static String getTBmother_mentorVisit(String baseEntityId) {
        String sql =
                "SELECT "
                        + DBConstants.KEY.EVENT_DATE
                        + ", "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " FROM ec_mothermentor_visit p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<String> dataMap =
                cursor -> {
                    String eventDate = getCursorValue(cursor, DBConstants.KEY.EVENT_DATE, "");
                    if (StringUtils.isNotBlank(eventDate)) {
                        return eventDate;
                    }
                    return getCursorValue(cursor, DBConstants.KEY.LAST_INTERACTED_WITH, "");
                };

        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty() && res.get(0) != null) {
            return res.get(0);
        }
        return "";
    }

    public static Date getLatestTbSampleCollectionDate(String baseEntityId) {
        return getLatestEventDate(
                Constants.TABLES.MOTHERMENTOR_VISIT, DBConstants.KEY.ENTITY_ID, baseEntityId);
    }

    public static Date getLatestMotherMentorScreeningDate(String baseEntityId) {
        Date screeningDate =
                getLatestEventDate(
                        Constants.TABLES.MOTHERMENTOR_ENROLLMENT,
                        DBConstants.KEY.BASE_ENTITY_ID,
                        baseEntityId);
        return screeningDate != null
                ? screeningDate
                : getLatestEventDate(
                        Constants.TABLES.MOTHERMENTOR_ENROLLMENT,
                        DBConstants.KEY.BASE_ENTITY_ID,
                        baseEntityId);
    }

    private static Date getLatestEventDate(
            String tableName, String entityColumn, String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return null;
        }

        String sql =
                "SELECT "
                        + DBConstants.KEY.EVENT_DATE
                        + " FROM "
                        + tableName
                        + " p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + entityColumn
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.EVENT_DATE
                        + " DESC LIMIT 1";

        DataMap<String> dataMap = cursor -> getCursorValue(cursor, DBConstants.KEY.EVENT_DATE, "");

        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty()) {
            return parseEventDate(res.get(0));
        }
        return null;
    }

    private static Date parseEventDate(String eventDate) {
        if (StringUtils.isBlank(eventDate)) {
            return null;
        }

        String normalizedDate = normalizeTimezoneOffset(eventDate);
        Date parsedDate = tryParseDate(eventDate, ISO_DATE_WITH_MILLIS_TZ);
        if (parsedDate != null) return parsedDate;

        parsedDate = tryParseDate(eventDate, ISO_DATE_WITH_TZ);
        if (parsedDate != null) return parsedDate;

        parsedDate = tryParseDate(normalizedDate, ISO_DATE_WITH_MILLIS_TZ_NO_COLON);
        if (parsedDate != null) return parsedDate;

        parsedDate = tryParseDate(normalizedDate, ISO_DATE_WITH_TZ_NO_COLON);
        if (parsedDate != null) return parsedDate;

        return tryParseDate(eventDate, getNativeFormsDateFormat().toPattern());
    }

    private static Date tryParseDate(String dateValue, String pattern) {
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault()).parse(dateValue);
        } catch (ParseException ignored) {
            return null;
        }
    }

    private static String normalizeTimezoneOffset(String eventDate) {
        int length = eventDate.length();
        if (length > 5
                && eventDate.charAt(length - 3) == ':'
                && (eventDate.charAt(length - 6) == '+' || eventDate.charAt(length - 6) == '-')) {
            return eventDate.substring(0, length - 3) + eventDate.substring(length - 2);
        }
        return eventDate;
    }

    public static boolean hasCompletedHouseholdVisitToday(String baseEntityId, Date now) {
        if (StringUtils.isBlank(baseEntityId) || now == null) return false;
        return MotherMentorHouseholdVisitUtils.hasCompletedVisitToday(
                MotherMentorLibrary.getInstance()
                        .visitRepository()
                        .getAllVisitsProcessed(
                                Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES, baseEntityId),
                now);
    }

    public static boolean hasMotherMentorVisit(String baseEntityId) {
        String sql =
                "SELECT count(p."
                        + DBConstants.KEY.ENTITY_ID
                        + ") count FROM ec_mothermentor_visit p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "'";

        DataMap<Integer> dataMap = cursor -> getCursorIntValue(cursor, "count");

        List<Integer> res = readData(sql, dataMap);
        if (res == null || res.size() != 1) {
            return false;
        }
        Integer count = res.get(0);
        return count != null && count > 0;
    }

    public static Date getMotherMentorTestDate(String baseEntityID) {
        String sql =
                "select "
                        + DBConstants.KEY.MOTHERMENTOR_TEST_DATE
                        + " from ec_mothermentor_enrollment where "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityID
                        + "'";

        DataMap<Date> dataMap =
                cursor ->
                        getCursorValueAsDate(
                                cursor,
                                DBConstants.KEY.MOTHERMENTOR_TEST_DATE,
                                getNativeFormsDateFormat());

        List<Date> res = readData(sql, dataMap);
        if (res == null || res.size() != 1) return null;

        return res.get(0);
    }

    public static String getClientMotherMentorID(String baseEntityId) {
        String sql =
                "SELECT "
                        + DBConstants.KEY.MOTHERMENTOR_CLIENT_ID
                        + " FROM ec_mothermentor_enrollment p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.ENROLLMENT_DATE
                        + " DESC LIMIT 1";

        DataMap<String> dataMap =
                cursor -> getCursorValue(cursor, DBConstants.KEY.MOTHERMENTOR_CLIENT_ID);

        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty() && res.get(0) != null) {
            return res.get(0);
        }
        return "";
    }

    public static String getIndexClientIdForContact(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return null;
        }

        String sql =
                "SELECT "
                        + DBConstants.KEY.INDEX_CLIENT_ID
                        + " FROM ec_mothermentor_contacts WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<String> dataMap = cursor -> getCursorValue(cursor, DBConstants.KEY.INDEX_CLIENT_ID);

        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty() && res.get(0) != null) {
            return res.get(0);
        }
        return null;
    }

    public static ClientNumberInfo getIndexClientNumbers(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) {
            return null;
        }

        String sql =
                "SELECT "
                        + DBConstants.KEY.TB_CLIENT_NUMBER
                        + ", "
                        + DBConstants.KEY.LEPROSY_CLIENT_NUMBER
                        + " FROM ec_mothermentor_enrollment WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";

        DataMap<ClientNumberInfo> dataMap =
                cursor ->
                        new ClientNumberInfo(
                                getCursorValue(cursor, DBConstants.KEY.TB_CLIENT_NUMBER),
                                getCursorValue(cursor, DBConstants.KEY.LEPROSY_CLIENT_NUMBER));

        List<ClientNumberInfo> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty()) {
            return res.get(0);
        }
        return null;
    }

    public static String getEnrollmentDate(String baseEntityId) {
        String sql =
                "SELECT "
                        + DBConstants.KEY.ENROLLMENT_DATE
                        + " FROM ec_mothermentor_enrollment p  WHERE p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityId
                        + "' ORDER BY "
                        + DBConstants.KEY.ENROLLMENT_DATE
                        + " DESC LIMIT 1";

        DataMap<String> dataMap = cursor -> getCursorValue(cursor, DBConstants.KEY.ENROLLMENT_DATE);

        List<String> res = readData(sql, dataMap);
        if (res != null && !res.isEmpty() && res.get(0) != null) {
            return res.get(0);
        }
        return "";
    }

    public static int getVisitNumber(String baseEntityID) {
        String sql =
                "SELECT "
                        + DBConstants.KEY.VISIT_NUMBER
                        + "  FROM ec_mothermentor_follow_up_visit WHERE "
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND "
                        + DBConstants.KEY.ENTITY_ID
                        + "='"
                        + baseEntityID
                        + "' ORDER BY "
                        + DBConstants.KEY.VISIT_NUMBER
                        + " DESC LIMIT 1";
        DataMap<Integer> map = cursor -> getCursorIntValue(cursor, DBConstants.KEY.VISIT_NUMBER);
        List<Integer> res = readData(sql, map);

        if (res != null && !res.isEmpty() && res.get(0) != null) {
            return res.get(0) + 1;
        } else return 0;
    }

    public static boolean isRegisteredForMotherMentor(String baseEntityID) {
        String sql =
                "SELECT SUM(count) count FROM (SELECT count(p."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ") count FROM ec_mothermentor_enrollment p WHERE p."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityID
                        + "' AND p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 UNION ALL SELECT count(s."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ") count FROM ec_mothermentor_enrollment s WHERE s."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityID
                        + "' AND s."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 )";

        DataMap<Integer> dataMap = cursor -> getCursorIntValue(cursor, "count");

        List<Integer> res = readData(sql, dataMap);
        if (res == null || res.size() != 1) return false;

        return res.get(0) > 0;
    }

    public static boolean isHouseholdEnrolledForMotherMentor(String householdBaseEntityId) {
        String sql =
                "SELECT count(p."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ") count FROM "
                        + Constants.TABLES.MAMA_KINARA_HOUSEHOLD_ENROLMENT
                        + " p WHERE p."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + householdBaseEntityId
                        + "' AND p."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND p."
                        + DBConstants.KEY.CONSENT
                        + " = 'ndiyo'";

        DataMap<Integer> dataMap = cursor -> getCursorIntValue(cursor, "count");

        List<Integer> res = readData(sql, dataMap);
        if (res == null || res.isEmpty()) {
            return false;
        }

        return res.get(0) > 0;
    }

    public static Integer getMotherMentorFamilyMembersCount(String familyBaseEntityId) {
        String sql =
                "SELECT count(emc."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ") count FROM ec_mothermentor_enrollment emc INNER Join ec_family_member"
                        + " fm on fm."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = emc."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " WHERE fm."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " = '"
                        + familyBaseEntityId
                        + "' AND fm."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND emc."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND emc.mothermentor = 1";

        DataMap<Integer> dataMap = cursor -> getCursorIntValue(cursor, "count");

        List<Integer> res = readData(sql, dataMap);
        if (res == null || res.isEmpty()) return 0;
        return res.get(0);
    }

    public static MemberObject getMember(String baseEntityID) {
        MemberObject memberObject =
                getMember(baseEntityID, Constants.TABLES.MOTHERMENTOR_ENROLLMENT);
        return memberObject != null
                ? memberObject
                : getMember(baseEntityID, Constants.TABLES.MOTHERMENTOR_ENROLLMENT);
    }

    /**
     * Includes locally saved services awaiting processing and services restored through sync.
     * Enrollment and the separate IIT, partner, child and household visits are not service visits.
     */
    public static boolean hasMotherMentorServices(String baseEntityId) {
        if (StringUtils.isBlank(baseEntityId)) return false;

        String query =
                "SELECT 1 FROM "
                        + VisitRepository.VISIT_TABLE
                        + " WHERE "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = ? AND "
                        + DBConstants.KEY.VISIT_TYPE
                        + " = ? LIMIT 1";
        try (Cursor cursor =
                MotherMentorLibrary.getInstance()
                        .getRepository()
                        .getReadableDatabase()
                        .rawQuery(
                                query,
                                new String[] {
                                    baseEntityId, Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES
                                })) {
            return cursor.moveToFirst();
        }
    }

    /**
     * ART may have started before enrollment or during a later service visit. The visit being
     * edited must not count as prior initiation.
     */
    public static boolean wasArtInitiatedBeforeVisit(String baseEntityId, String currentVisitId) {
        if (StringUtils.isBlank(baseEntityId)) return false;

        String query =
                "SELECT 1 FROM "
                        + Constants.TABLES.MOTHERMENTOR_ENROLLMENT
                        + " WHERE "
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = ? AND (lower(trim("
                        + DBConstants.KEY.ART_STATUS
                        + ")) IN ('yes', 'ndiyo') OR lower(trim(ifnull("
                        + DBConstants.KEY.ART_START_DATE
                        + ", ''))) NOT IN ('', 'null'))"
                        + " UNION ALL SELECT 1 FROM "
                        + VisitRepository.VISIT_TABLE
                        + " v INNER JOIN "
                        + VisitDetailsRepository.VISIT_DETAILS_TABLE
                        + " d ON d."
                        + DBConstants.KEY.VISIT_ID
                        + " = v."
                        + DBConstants.KEY.VISIT_ID
                        + " WHERE v."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = ? AND v."
                        + DBConstants.KEY.VISIT_TYPE
                        + " = ? AND d."
                        + DBConstants.KEY.VISIT_KEY
                        + " = ? AND lower(trim(d."
                        + DBConstants.KEY.DETAILS
                        + ")) IN ('yes', 'ndiyo') AND (? = '' OR v."
                        + DBConstants.KEY.VISIT_ID
                        + " <> ?) LIMIT 1";
        try (Cursor cursor =
                MotherMentorLibrary.getInstance()
                        .getRepository()
                        .getReadableDatabase()
                        .rawQuery(
                                query,
                                new String[] {
                                    baseEntityId,
                                    baseEntityId,
                                    Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,
                                    DBConstants.KEY.NEW_PMTCT_ART_INITIATION,
                                    StringUtils.defaultString(currentVisitId),
                                    StringUtils.defaultString(currentVisitId)
                                })) {
            return cursor.moveToFirst();
        }
    }

    /**
     * Find group linkage recorded in this client's earlier service visits, including visits
     * awaiting processing. When editing, the current visit and any later visits are excluded.
     */
    public static Set<String> getPriorGroupLinkages(String baseEntityId, String currentVisitId) {
        if (StringUtils.isBlank(baseEntityId)) return Collections.emptySet();

        String query =
                "SELECT DISTINCT d."
                        + DBConstants.KEY.VISIT_KEY
                        + " FROM "
                        + VisitRepository.VISIT_TABLE
                        + " v INNER JOIN "
                        + VisitDetailsRepository.VISIT_DETAILS_TABLE
                        + " d ON d."
                        + DBConstants.KEY.VISIT_ID
                        + " = v."
                        + DBConstants.KEY.VISIT_ID
                        + " WHERE v."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = ? AND v."
                        + DBConstants.KEY.VISIT_TYPE
                        + " = ? AND d."
                        + DBConstants.KEY.VISIT_KEY
                        + " IN (?, ?) AND lower(trim(d."
                        + DBConstants.KEY.DETAILS
                        + ")) IN (?, ?, ?) AND (? = '' OR (v."
                        + DBConstants.KEY.VISIT_ID
                        + " <> ? AND CAST(v."
                        + DBConstants.KEY.VISIT_DATE
                        + " AS INTEGER) < (SELECT CAST(editing."
                        + DBConstants.KEY.VISIT_DATE
                        + " AS INTEGER) FROM "
                        + VisitRepository.VISIT_TABLE
                        + " editing WHERE editing."
                        + DBConstants.KEY.VISIT_ID
                        + " = ? AND editing."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = ? AND editing."
                        + DBConstants.KEY.VISIT_TYPE
                        + " = ? LIMIT 1)))";
        Set<String> groups = new HashSet<>();
        try (Cursor cursor =
                MotherMentorLibrary.getInstance()
                        .getRepository()
                        .getReadableDatabase()
                        .rawQuery(
                                query,
                                new String[] {
                                    baseEntityId,
                                    Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES,
                                    DBConstants.KEY.HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP,
                                    DBConstants.KEY.HAS_BEEN_LINKED_TO_IGA_GROUP,
                                    "yes",
                                    "ndiyo",
                                    Constants.GROUP_LINKAGE.ALREADY_IN_GROUP,
                                    StringUtils.defaultString(currentVisitId),
                                    StringUtils.defaultString(currentVisitId),
                                    StringUtils.defaultString(currentVisitId),
                                    baseEntityId,
                                    Constants.EVENT_TYPE.MOTHER_MENTOR_SERVICES
                                })) {
            while (cursor.moveToNext()) {
                groups.add(getCursorValue(cursor, DBConstants.KEY.VISIT_KEY));
            }
        }
        return groups;
    }

    /** Visit participants can belong to any Mother Mentor enrollment register. */
    public static java.util.Map<String, String> getVisitEnrollmentValues(String baseEntityID) {
        String id = baseEntityID.replace("'", "''");
        String sql =
                "SELECT e."
                        + DBConstants.KEY.PREGNANCY_STATUS
                        + ", e."
                        + DBConstants.KEY.ART_START_DATE
                        + ", EXISTS (SELECT 1 FROM ec_mothermentor_enroll_it i WHERE i."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ") AS "
                        + DBConstants.KEY.IIT_ENROLLED
                        + ", EXISTS (SELECT 1 FROM ec_mothermentor_enroll_partner p WHERE p."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ") AS "
                        + DBConstants.KEY.PARTNER_ENROLLED
                        + ", EXISTS (SELECT 1 FROM ec_mothermentor_enroll_child_eid c WHERE c."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + ") AS "
                        + DBConstants.KEY.CHILD_EID_ENROLLED
                        + ", CASE WHEN e."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " IS NULL THEN 'false' ELSE 'true' END AS "
                        + DBConstants.KEY.PRIMARY_CLIENT
                        + " FROM ec_family_member m LEFT JOIN ec_mothermentor_enrollment e ON e."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " WHERE m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + id
                        + "' LIMIT 1";
        List<java.util.Map<String, String>> rows =
                readData(
                        sql,
                        cursor -> {
                            java.util.Map<String, String> result = new java.util.HashMap<>();
                            result.put(
                                    DBConstants.KEY.PRIMARY_MOTHERMENTOR_CLIENT,
                                    getCursorValue(cursor, DBConstants.KEY.PRIMARY_CLIENT));
                            result.put(
                                    DBConstants.KEY.IIT_ENROLLED,
                                    getCursorIntValue(cursor, DBConstants.KEY.IIT_ENROLLED) == 1
                                            ? "true"
                                            : "false");
                            result.put(
                                    DBConstants.KEY.PARTNER_ENROLLED,
                                    getCursorIntValue(cursor, DBConstants.KEY.PARTNER_ENROLLED) == 1
                                            ? "true"
                                            : "false");
                            result.put(
                                    DBConstants.KEY.CHILD_EID_ENROLLED,
                                    getCursorIntValue(cursor, DBConstants.KEY.CHILD_EID_ENROLLED)
                                                    == 1
                                            ? "true"
                                            : "false");
                            result.put(
                                    DBConstants.KEY.VISIT_PREGNANCY_STATUS,
                                    getCursorValue(cursor, DBConstants.KEY.PREGNANCY_STATUS));
                            result.put(
                                    DBConstants.KEY.VISIT_ART_START_DATE,
                                    getCursorValue(cursor, DBConstants.KEY.ART_START_DATE));
                            return result;
                        });
        return rows == null || rows.isEmpty() ? new java.util.HashMap<>() : rows.get(0);
    }

    public static MemberObject getVisitMember(String baseEntityID) {
        MemberObject member = getMember(baseEntityID);
        if (member == null) member = getSecondaryEnrollmentMember(baseEntityID);
        return member != null ? member : getContact(baseEntityID);
    }

    public static MemberObject getSecondaryEnrollmentMember(String baseEntityID) {
        MemberObject memberObject =
                getSecondaryEnrollmentMember(
                        baseEntityID, Constants.TABLES.MOTHERMENTOR_ENROLL_IIT);
        if (memberObject != null) {
            return memberObject;
        }

        memberObject =
                getSecondaryEnrollmentMember(
                        baseEntityID, Constants.TABLES.MOTHERMENTOR_ENROLL_PARTNER);
        if (memberObject != null) {
            return memberObject;
        }

        return getSecondaryEnrollmentMember(
                baseEntityID, Constants.TABLES.MOTHERMENTOR_ENROLL_CHILD_EID);
    }

    private static MemberObject getSecondaryEnrollmentMember(
            String baseEntityID, String tableName) {
        String sql =
                "select m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " , m."
                        + DBConstants.KEY.UNIQUE_ID
                        + " , m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " , f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " as "
                        + DBConstants.KEY.FAMILY_BASE_ENTITY_ID
                        + ", m."
                        + DBConstants.KEY.DOB
                        + " , m."
                        + DBConstants.KEY.FIRST_NAME
                        + " , m."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " , m."
                        + DBConstants.KEY.LAST_NAME
                        + " , m."
                        + DBConstants.KEY.GENDER
                        + " , m."
                        + DBConstants.KEY.MARITAL_STATUS
                        + " , m."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " , m."
                        + DBConstants.KEY.OTHER_PHONE_NUMBER
                        + " , f."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_NAME
                        + " ,f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " , f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " , f."
                        + DBConstants.KEY.VILLAGE_TOWN
                        + " ,fh."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_FIRST_NAME
                        + " , fh."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_MIDDLE_NAME
                        + " , fh."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_LAST_NAME
                        + ", fh."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_PHONE_NUMBER
                        + " ,  pcg."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_FIRST_NAME
                        + " , pcg."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_LAST_NAME
                        + " , pcg."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.PCG_MIDDLE_NAME
                        + " , pcg."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as  "
                        + DBConstants.KEY.PCG_PHONE_NUMBER
                        + " , mr.* from ec_family_member m inner join ec_family f on m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " = f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " inner join "
                        + tableName
                        + " mr on mr."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " left join ec_family_member fh on fh."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " left join ec_family_member pcg on pcg."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " where mr."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND m."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND f."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = '"
                        + baseEntityID
                        + "' ORDER BY mr."
                        + DBConstants.KEY.LAST_INTERACTED_WITH
                        + " DESC LIMIT 1";
        List<MemberObject> res = readData(sql, memberObjectMap);
        if (res == null || res.isEmpty()) return null;

        return res.get(0);
    }

    private static MemberObject getMember(String baseEntityID, String tableName) {
        String sql =
                "select m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " , m."
                        + DBConstants.KEY.UNIQUE_ID
                        + " , m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " , f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " as "
                        + DBConstants.KEY.FAMILY_BASE_ENTITY_ID
                        + ", m."
                        + DBConstants.KEY.DOB
                        + " , m."
                        + DBConstants.KEY.FIRST_NAME
                        + " , m."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " , m."
                        + DBConstants.KEY.LAST_NAME
                        + " , m."
                        + DBConstants.KEY.GENDER
                        + " , m."
                        + DBConstants.KEY.MARITAL_STATUS
                        + " , m."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " , m."
                        + DBConstants.KEY.OTHER_PHONE_NUMBER
                        + " , f."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_NAME
                        + " ,f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " , f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " , f."
                        + DBConstants.KEY.VILLAGE_TOWN
                        + " ,fh."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_FIRST_NAME
                        + " , fh."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_MIDDLE_NAME
                        + " , fh."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_LAST_NAME
                        + ", fh."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_PHONE_NUMBER
                        + " ,  pcg."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_FIRST_NAME
                        + " , pcg."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_LAST_NAME
                        + " , pcg."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.PCG_MIDDLE_NAME
                        + " , pcg."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as  "
                        + DBConstants.KEY.PCG_PHONE_NUMBER
                        + " , mr.* from ec_family_member m inner join ec_family f on m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " = f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " inner join "
                        + tableName
                        + " mr on mr."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " left join ec_family_member fh on fh."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " left join ec_family_member pcg on pcg."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " where mr."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " ='"
                        + baseEntityID
                        + "' ";
        List<MemberObject> res = readData(sql, memberObjectMap);
        if (res == null || res.size() != 1) return null;

        return res.get(0);
    }

    public static MemberObject getContact(String baseEntityID) {
        String sql =
                "select m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " , m."
                        + DBConstants.KEY.UNIQUE_ID
                        + " , m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " , f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " as "
                        + DBConstants.KEY.FAMILY_BASE_ENTITY_ID
                        + ", m."
                        + DBConstants.KEY.DOB
                        + " , m."
                        + DBConstants.KEY.FIRST_NAME
                        + " , m."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " , m."
                        + DBConstants.KEY.LAST_NAME
                        + " , m."
                        + DBConstants.KEY.GENDER
                        + " , m."
                        + DBConstants.KEY.MARITAL_STATUS
                        + " , m."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " , m."
                        + DBConstants.KEY.OTHER_PHONE_NUMBER
                        + " , f."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_NAME
                        + " ,f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " , f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " , f."
                        + DBConstants.KEY.VILLAGE_TOWN
                        + " ,fh."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_FIRST_NAME
                        + " , fh."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_MIDDLE_NAME
                        + " , fh."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_LAST_NAME
                        + ", fh."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_PHONE_NUMBER
                        + " ,  pcg."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_FIRST_NAME
                        + " , pcg."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_LAST_NAME
                        + " , pcg."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.PCG_MIDDLE_NAME
                        + " , pcg."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as  "
                        + DBConstants.KEY.PCG_PHONE_NUMBER
                        + " , mr.* from ec_family_member m inner join ec_family f on m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " = f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " inner join ec_mothermentor_contacts mr on mr."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " left join ec_family_member fh on fh."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " left join ec_family_member pcg on pcg."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " where mr."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND m."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND f."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " ='"
                        + baseEntityID
                        + "' ";
        List<MemberObject> res = readData(sql, memberObjectMap);
        if (res == null || res.size() != 1) return null;

        return res.get(0);
    }

    public static List<MemberObject> getMembers() {
        String sql =
                "select m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " , m."
                        + DBConstants.KEY.UNIQUE_ID
                        + " , m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " , f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " as "
                        + DBConstants.KEY.FAMILY_BASE_ENTITY_ID
                        + ", m."
                        + DBConstants.KEY.DOB
                        + " , m."
                        + DBConstants.KEY.FIRST_NAME
                        + " , m."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " , m."
                        + DBConstants.KEY.LAST_NAME
                        + " , m."
                        + DBConstants.KEY.GENDER
                        + " , m."
                        + DBConstants.KEY.MARITAL_STATUS
                        + " , m."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " , m."
                        + DBConstants.KEY.OTHER_PHONE_NUMBER
                        + " , f."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_NAME
                        + " ,f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " , f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " , f."
                        + DBConstants.KEY.VILLAGE_TOWN
                        + " ,fh."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_FIRST_NAME
                        + " , fh."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_MIDDLE_NAME
                        + " , fh."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_LAST_NAME
                        + ", fh."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as "
                        + DBConstants.KEY.FAMILY_HEAD_PHONE_NUMBER
                        + " ,  pcg."
                        + DBConstants.KEY.FIRST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_FIRST_NAME
                        + " , pcg."
                        + DBConstants.KEY.LAST_NAME
                        + " as "
                        + DBConstants.KEY.PCG_LAST_NAME
                        + " , pcg."
                        + DBConstants.KEY.MIDDLE_NAME
                        + " as "
                        + DBConstants.KEY.PCG_MIDDLE_NAME
                        + " , pcg."
                        + DBConstants.KEY.PHONE_NUMBER
                        + " as  "
                        + DBConstants.KEY.PCG_PHONE_NUMBER
                        + " , mr.* from ec_family_member m inner join ec_family f on m."
                        + DBConstants.KEY.RELATIONAL_ID
                        + " = f."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " inner join ec_mothermentor_enrollment mr on mr."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = m."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " left join ec_family_member fh on fh."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.FAMILY_HEAD
                        + " left join ec_family_member pcg on pcg."
                        + DBConstants.KEY.BASE_ENTITY_ID
                        + " = f."
                        + DBConstants.KEY.PRIMARY_CARE_GIVER
                        + " where mr."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND m."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 AND f."
                        + DBConstants.KEY.IS_CLOSED
                        + " = 0 ";

        return readData(sql, memberObjectMap);
    }

    public static class ClientNumberInfo {
        private final String tbClientNumber;
        private final String mother_mentorClientNumber;

        public ClientNumberInfo(String tbClientNumber, String mother_mentorClientNumber) {
            this.tbClientNumber = tbClientNumber;
            this.mother_mentorClientNumber = mother_mentorClientNumber;
        }

        public String getTbClientNumber() {
            return tbClientNumber;
        }

        public String getMotherMentorClientNumber() {
            return mother_mentorClientNumber;
        }
    }

    public static class FollowUpVisit {
        private final String lastInteractedWith;
        private final String followUpReason;
        private final String followUpOutcome;
        private final String reasonClientNotFound;
        private final String returnedToTreatment;
        private final String tbTreatmentStartDate;
        private final String serviceAccessChallenges;
        private final String reasonsForNotReturningWithoutChallenges;
        private final String clientChallengeTypes;
        private final String healthFacilityChallengesDetail;
        private final String returnToTreatmentPrompt;

        public FollowUpVisit(
                String lastInteractedWith,
                String followUpReason,
                String followUpOutcome,
                String reasonClientNotFound,
                String returnedToTreatment,
                String tbTreatmentStartDate,
                String serviceAccessChallenges,
                String reasonsForNotReturningWithoutChallenges,
                String clientChallengeTypes,
                String healthFacilityChallengesDetail,
                String returnToTreatmentPrompt) {
            this.lastInteractedWith = lastInteractedWith;
            this.followUpReason = followUpReason;
            this.followUpOutcome = followUpOutcome;
            this.reasonClientNotFound = reasonClientNotFound;
            this.returnedToTreatment = returnedToTreatment;
            this.tbTreatmentStartDate = tbTreatmentStartDate;
            this.serviceAccessChallenges = serviceAccessChallenges;
            this.reasonsForNotReturningWithoutChallenges = reasonsForNotReturningWithoutChallenges;
            this.clientChallengeTypes = clientChallengeTypes;
            this.healthFacilityChallengesDetail = healthFacilityChallengesDetail;
            this.returnToTreatmentPrompt = returnToTreatmentPrompt;
        }

        public String getLastInteractedWith() {
            return lastInteractedWith;
        }

        public String getFollowUpReason() {
            return followUpReason;
        }

        public String getFollowUpOutcome() {
            return followUpOutcome;
        }

        public String getReasonClientNotFound() {
            return reasonClientNotFound;
        }

        public String getReturnedToTreatment() {
            return returnedToTreatment;
        }

        public String getTbTreatmentStartDate() {
            return tbTreatmentStartDate;
        }

        public String getServiceAccessChallenges() {
            return serviceAccessChallenges;
        }

        public String getReasonsForNotReturningWithoutChallenges() {
            return reasonsForNotReturningWithoutChallenges;
        }

        public String getClientChallengeTypes() {
            return clientChallengeTypes;
        }

        public String getHealthFacilityChallengesDetail() {
            return healthFacilityChallengesDetail;
        }

        public String getReturnToTreatmentPrompt() {
            return returnToTreatmentPrompt;
        }
    }

    public static class ObservationResults {
        private final String lastInteractedWith;
        private final String investigationType;
        private final String tbDiagnosticTestType;
        private final String tbSampleTestResults;
        private final String clinicalDecision;
        private final String mother_mentorDiagnosticMethod;
        private final String tbTreatmentStartDate;
        private final String mother_mentorTreatmentStartDate;
        private final String mother_mentorInvestigationResults;
        private final boolean poorQualitySample;

        public ObservationResults(
                String lastInteractedWith,
                String investigationType,
                String tbDiagnosticTestType,
                String tbSampleTestResults,
                String clinicalDecision,
                String mother_mentorDiagnosticMethod,
                String tbTreatmentStartDate,
                String mother_mentorTreatmentStartDate,
                String mother_mentorInvestigationResults,
                boolean poorQualitySample) {
            this.lastInteractedWith = lastInteractedWith;
            this.investigationType = investigationType;
            this.tbDiagnosticTestType = tbDiagnosticTestType;
            this.tbSampleTestResults = tbSampleTestResults;
            this.clinicalDecision = clinicalDecision;
            this.mother_mentorDiagnosticMethod = mother_mentorDiagnosticMethod;
            this.tbTreatmentStartDate = tbTreatmentStartDate;
            this.mother_mentorTreatmentStartDate = mother_mentorTreatmentStartDate;
            this.mother_mentorInvestigationResults = mother_mentorInvestigationResults;
            this.poorQualitySample = poorQualitySample;
        }

        public String getLastInteractedWith() {
            return lastInteractedWith;
        }

        public String getInvestigationType() {
            return investigationType;
        }

        public String getTbDiagnosticTestType() {
            return tbDiagnosticTestType;
        }

        public String getTbSampleTestResults() {
            return tbSampleTestResults;
        }

        public String getClinicalDecision() {
            return clinicalDecision;
        }

        public String getMotherMentorDiagnosticMethod() {
            return mother_mentorDiagnosticMethod;
        }

        public String getTbTreatmentStartDate() {
            return tbTreatmentStartDate;
        }

        public String getMotherMentorTreatmentStartDate() {
            return mother_mentorTreatmentStartDate;
        }

        public String getMotherMentorInvestigationResults() {
            return mother_mentorInvestigationResults;
        }

        public boolean isPoorQualitySample() {
            return poorQualitySample;
        }
    }
}
