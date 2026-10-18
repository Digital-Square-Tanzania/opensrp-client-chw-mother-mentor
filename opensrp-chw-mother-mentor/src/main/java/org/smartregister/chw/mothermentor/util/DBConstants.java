package org.smartregister.chw.mothermentor.util;

public final class DBConstants {

    private DBConstants() {}

    public static final class KEY {
        private KEY() {}

        public static final String FIRST_NAME = "first_name";
        public static final String MIDDLE_NAME = "middle_name";
        public static final String LAST_NAME = "last_name";
        public static final String BASE_ENTITY_ID = "base_entity_id";
        public static final String FAMILY_BASE_ENTITY_ID = "family_base_entity_id";
        public static final String DOB = "dob";
        public static final String DOD = "dod";
        public static final String UNIQUE_ID = "unique_id";
        public static final String MARITAL_STATUS = "marital_status";
        public static final String LAST_INTERACTED_WITH = "last_interacted_with";
        public static final String VILLAGE_TOWN = "village_town";
        public static final String DATE_REMOVED = "date_removed";
        public static final String GENDER = "gender";
        public static final String RELATIONALID = "relationalid";
        public static final String DETAILS = "details";
        public static final String IS_CLOSED = "is_closed";
        public static final String IS_ANC_CLOSED = "is_anc_closed";
        public static final String IS_PNC_CLOSED = "is_pnc_closed";
        public static final String RELATIONAL_ID = "relational_id";
        public static final String FAMILY_HEAD = "family_head";
        public static final String PRIMARY_CARE_GIVER = "primary_caregiver";
        public static final String LAST_HOME_VISIT = "last_home_visit";
        public static final String VISIT_NOT_DONE = "visit_not_done";
        public static final String FAMILY_NAME = "family_name";
        public static final String PHONE_NUMBER = "phone_number";
        public static final String CHILD = "ec_child";
        public static final String MOBILIZATION_DATE = "mobilization_date";
        public static final String FEMALE_CLIENTS_REACHED = "female_clients_reached";
        public static final String MALE_CLIENTS_REACHED = "male_clients_reached";
        public static final String SH_TAREHE = "sh_tarehe";
        public static final String SH_AINA = "sh_aina";
        public static final String SH_AINA_OTHER = "sh_aina_other";
        public static final String SH_MADA = "sh_mada";
        public static final String SH_MADA_OTHER = "sh_mada_other";
        public static final String ELIM_F = "elim_f";
        public static final String ELIM_M = "elim_m";
        public static final String ELIM_TOTAL = "elim_total";
        public static final String GPS = "gps";
        public static final String MAONI = "maoni";

        public static final String KIT_CODE = "kit_code";
        public static final String KIT_FOR = "kit_for";
        public static final String HIVST_RESULT = "hivst_result";
        public static final String COLLECTION_DATE = "collection_date";
        public static final String INDEX_CLIENT_ID = "index_client_id";
        public static final String TB_CLIENT_NUMBER = "tb_client_number";
        public static final String LEPROSY_CLIENT_NUMBER = "mother_mentor_client_number";

        // ec_mothermentor_enrollment.
        public static final String ART_NO_REASON = "art_no_reason";
        public static final String ART_NO_REASON_OTHER = "art_no_reason_other";
        public static final String ART_START_DATE = "art_start_date";
        public static final String ART_STATUS = "art_status";
        public static final String ART_STATUS_REGISTRATION = "art_status_registration";
        public static final String BELONGS_TO_SPECIAL_GROUP = "belongs_to_special_group";
        public static final String CHILD_DOB = "child_dob";
        public static final String CLIENT_SPECIAL_GROUP = "client_special_group";
        public static final String CONTACT_LIVES_WITH_PATIENT_TYPE =
                "contact_lives_with_patient_type";
        public static final String CTC_NUMBER = "ctc_number";
        public static final String ENTITY_ID = "entity_id";
        public static final String EVENT_DATE = "event_date";
        public static final String MOTHER_MENTOR_INDEX_CLIENT_NUMBER =
                "mother_mentor_index_client_number";
        public static final String PREGNANCY_STATUS = "pregnancy_status";
        public static final String REGISTRATION_DATE = "registration_date";
        public static final String SCREENING_STATUS = "screening_status";
        public static final String STATUS = "status";
        public static final String TB_INDEX_CLIENT_NUMBER = "tb_index_client_number";

        // ec_mothermentor_observation_results.
        public static final String CLINICAL_DECISION = "clinical_decision";
        public static final String INVESTIGATION_TYPE = "investigation_type";
        public static final String MOTHER_MENTOR_DIAGNOSTIC_METHOD =
                "mother_mentor_diagnostic_method";
        public static final String MOTHER_MENTOR_INVESTIGATION_RESULTS =
                "mother_mentor_investigation_results";
        public static final String MOTHER_MENTOR_TREATMENT_START_DATE =
                "mother_mentor_treatment_start_date";
        public static final String TB_DIAGNOSTIC_TEST_TYPE = "tb_diagnostic_test_type";
        public static final String TB_SAMPLE_TEST_RESULTS = "tb_sample_test_results";
        public static final String TB_TREATMENT_START_DATE = "tb_treatment_start_date";

        // ec_mothermentor_followup_visit.
        public static final String CHILD_EID_ARRIVED_AT_FACILITY = "child_eid_arrived_at_facility";
        public static final String CHILD_EID_EXPECTED_FACILITY_VISIT_DATE =
                "child_eid_expected_facility_visit_date";
        public static final String CHILD_EID_FACILITY_ARRIVAL_DATE =
                "child_eid_facility_arrival_date";
        public static final String CHILD_EID_NON_ATTENDANCE_REASON =
                "child_eid_non_attendance_reason";
        public static final String CHILD_EID_NON_ATTENDANCE_REASON_OTHER =
                "child_eid_non_attendance_reason_other";
        public static final String CHILD_EID_TEST_TYPE = "child_eid_test_type";
        public static final String CLIENT_CHALLENGE_TYPES = "client_challenge_types";
        public static final String COMMENTS = "comments";
        public static final String EXPECTED_FACILITY_VISIT_DATE = "expected_facility_visit_date";
        public static final String FACILITY_RETURN_DATE = "facility_return_date";
        public static final String FOLLOW_UP_CATEGORY = "follow_up_category";
        public static final String FOLLOW_UP_OUTCOME = "follow_up_outcome";
        public static final String FOLLOW_UP_REASON = "follow_up_reason";
        public static final String FOLLOW_UP_STATUS = "follow_up_status";
        public static final String PARTNER_ARRIVED_AT_FACILITY = "partner_arrived_at_facility";
        public static final String PARTNER_EXPECTED_FACILITY_VISIT_DATE =
                "partner_expected_facility_visit_date";
        public static final String PARTNER_FACILITY_ARRIVAL_DATE = "partner_facility_arrival_date";
        public static final String PARTNER_NON_ATTENDANCE_REASON = "partner_non_attendance_reason";
        public static final String PARTNER_NON_ATTENDANCE_REASON_OTHER =
                "partner_non_attendance_reason_other";
        public static final String RETURNED_TO_FACILITY = "returned_to_facility";
        public static final String RETURNED_TO_TREATMENT = "returned_to_treatment";
        public static final String SERVICE_ACCESS_CHALLENGES = "service_access_challenges";
        public static final String SERVICE_INTERRUPTION_REASON = "service_interruption_reason";
        public static final String SERVICE_INTERRUPTION_REASON_OTHER =
                "service_interruption_reason_other";
        public static final String TAJA_SABABU_YA_HAJAPATIKANA = "taja_sababu_ya_hajapatikana";
        public static final String TAREHE_YA_MIADI = "tarehe_ya_miadi";

        // ec_mothermentor_visit.
        public static final String HAS_SAMPLE_BEEN_COLLECTED = "has_sample_been_collected";
        public static final String SAMPLE_CONTAINER_PROVIDED = "sample_container_provided";

        // ec_mothermentor_contact_visit.
        public static final String NAMBA_YA_MTEJA = "namba_ya_mteja";
        public static final String RELATIONSHIP_TO_INDEX_CLIENT = "relationship_to_index_client";
        public static final String SAMPLE_FOLLOW_UP_RESULTS = "sample_follow_up_results";

        // ec_mothermentor_household_enrolment.
        public static final String CONSENT = "consent";
        public static final String ENROLLMENT_DATE = "enrollment_date";
        public static final String ENTITY_TYPE = "entity_type";
        public static final String HOUSEHOLD_ID = "household_id";

        // ec_mothermentor_mobilization.
        public static final String FACILITY_REFERRAL_OTHER = "facility_referral_other";
        public static final String FACILITY_REFERRAL_SERVICES = "facility_referral_services";
        public static final String MOBILIZATION_AREA = "mobilization_area";
        public static final String MOBILIZATION_TOPICS = "mobilization_topics";
        public static final String NUMBER_OF_IEC_MATERIALS_PROVIDED =
                "number_of_IEC_materials_provided";
        public static final String OTHER_MOBILIZATION_AREA = "other_mobilization_area";
        public static final String OTHER_POPULATION_TYPE = "other_population_type";
        public static final String POPULATION_TYPE = "population_type";
        public static final String REFERRED_FEMALE = "referred_female";
        public static final String REFERRED_MALE = "referred_male";
        public static final String REFERRED_TOTAL = "referred_total";

        // ec_mothermentor_enroll_it.
        public static final String DANGER_FLAG = "danger_flag";
        public static final String EXPECTED_RETURN_DATE = "expected_return_date";
        public static final String WEEKS_LOST = "weeks_lost";

        // ec_mothermentor_enroll_child_eid.
        public static final String BREASTFEEDING_STATUS = "breastfeeding_status";
        public static final String CHILD_NAME = "child_name";
        public static final String CHILD_SEX = "child_sex";
        public static final String HEI_NUMBER = "hei_number";
        public static final String HEI_REGISTERED = "hei_registered";
        public static final String LAST_TEST = "last_test";
        public static final String LAST_TEST_RESULT = "last_test_result";
        public static final String MOTHER_BASE_ENTITY_ID = "mother_base_entity_id";
        public static final String MOTHER_NAME = "mother_name";
        public static final String WEANING_DATE = "weaning_date";

        // ec_mothermentor_enroll_partner.
        public static final String HIV_DISCLOSURE = "hiv_disclosure";
        public static final String HIV_TESTED = "hiv_tested";
        public static final String MOTHER_CTC_NUMBER = "mother_ctc_number";
        public static final String ON_CTC = "on_ctc";
        public static final String OTHER_RELATIONSHIP = "other_relationship";
        public static final String PARTNER_CTC = "partner_ctc";
        public static final String PARTNER_DOB = "partner_dob";
        public static final String PARTNER_NAME = "partner_name";
        public static final String PARTNER_PHONE_NUMBER = "partner_phone_number";
        public static final String PARTNER_SEX = "partner_sex";
        public static final String RELATIONSHIP_TO_MOTHER = "relationship_to_mother";
        public static final String TEST_RESULT = "test_result";

        // Visit observations.
        public static final String ART_RETURN_DATE = "art_return_date";
        public static final String ATTENDANCE_TYPE = "attendance_type";
        public static final String CHILD_EID_ENROLLED = "child_eid_enrolled";
        public static final String CHILD_FACILITY_DATE = "child_facility_date";
        public static final String CHILD_FOLLOWUP_TEST_STAGE = "child_followup_test_stage";
        public static final String CHILD_LINKING = "child_linking";
        public static final String CHILD_REACHED_FACILITY = "child_reached_facility";
        public static final String COUNSELLED_FEMALE = "counselled_female";
        public static final String COUNSELLED_MALE = "counselled_male";
        public static final String COUNSELLED_TOTAL = "counselled_total";
        public static final String EDUCATION_OTHER = "education_other";
        public static final String EDUCATION_PROVIDED = "education_provided";
        public static final String HAS_BEEN_LINKED_TO_IGA_GROUP = "has_been_linked_to_iga_group";
        public static final String HAS_BEEN_LINKED_TO_PSYCHOSOCIAL_SUPPORT_GROUP =
                "has_been_linked_to_psychosocial_support_group";
        public static final String HOUSEHOLD_MEMBERS_FOUND = "household_members_found";
        public static final String HOUSEHOLD_REFERRAL_GIVEN = "household_referral_given";
        public static final String HOUSEHOLD_REFERRAL_OTHER = "household_referral_other";
        public static final String HOUSEHOLD_REFERRAL_SERVICES = "household_referral_services";
        public static final String IIT_ENROLLED = "iit_enrolled";
        public static final String IIT_INTERRUPTION_OTHER = "iit_interruption_other";
        public static final String IIT_INTERRUPTION_REASON = "iit_interruption_reason";
        public static final String IIT_TRACING_OUTCOME = "iit_tracing_outcome";
        public static final String IIT_TRACING_VISIT = "iit_tracing_visit";
        public static final String MOTHERMENTOR_REPORTING_VERSION =
                "mothermentor_reporting_version";
        public static final String NEW_PMTCT_ART_INITIATION = "new_pmtct_art_initiation";
        public static final String NEXT_APPOINTMENT_DATE = "next_appointment_date";
        public static final String OTHER = "other";
        public static final String OTHER_PURPOSE = "other_purpose";
        public static final String OTHER_REASONS_ART_NO_REASON = "other_reasons_art_no_reason";
        public static final String OTHER_TOPIC = "other_topic";
        public static final String PARTICIPANTS = "participants";
        public static final String PARTNER_ENROLLED = "partner_enrolled";
        public static final String PARTNER_FACILITY_DATE = "partner_facility_date";
        public static final String PARTNER_LINKING = "partner_linking";
        public static final String PARTNER_REACHED_FACILITY = "partner_reached_facility";
        public static final String PRIMARY_MOTHERMENTOR_CLIENT = "primary_mothermentor_client";
        public static final String PURPOSE_OF_VISIT = "purpose_of_visit";
        public static final String REFERRAL_GIVEN = "referral_given";
        public static final String REFERRAL_OTHER = "referral_other";
        public static final String RETURNED_TO_ART = "returned_to_art";
        public static final String TOPICS_BEING_PROVIDED = "topics_being_provided";
        public static final String TOPICS_TAUGHT = "topics_taught";
        public static final String TYPE_OF_REFERRAL_GIVEN = "type_of_referral_given";
        public static final String VISIT_ART_START_DATE = "visit_art_start_date";
        public static final String VISIT_PREGNANCY_STATUS = "visit_pregnancy_status";
        public static final String WAY_EDUCATION_PROVIDED = "way_education_provided";

        // Shared visit and event columns.
        public static final String AGE = "age";
        public static final String EVENT_BASE_ENTITY_ID = "baseEntityId";
        public static final String DOB_UNKNOWN = "dob_unknown";
        public static final String EVENT_DATE_TIMESTAMP = "eventDate";
        public static final String EVENT_TYPE = "eventType";
        public static final String FAMILY_HEAD_PHONE_NUMBER = "family_head_phone_number";
        public static final String ID = "id";
        public static final String MOTHERMENTOR_VISIT_DATE = "mothermentor_visit_date";
        public static final String OTHER_PHONE_NUMBER = "other_phone_number";
        public static final String PROCESSED = "processed";
        public static final String SERVICE_INTERRUPTION_DURATION_WEEKS =
                "service_interruption_duration_weeks";
        public static final String VISIT_DATE = "visit_date";
        public static final String VISIT_ID = "visit_id";
        public static final String VISIT_KEY = "visit_key";
        public static final String VISIT_TYPE = "visit_type";
        // Visit storage and profile projections.
        public static final String CREATED_AT = "created_at";
        public static final String FAMILY_HEAD_FIRST_NAME = "family_head_first_name";
        public static final String FAMILY_HEAD_LAST_NAME = "family_head_last_name";
        public static final String FAMILY_HEAD_MIDDLE_NAME = "family_head_middle_name";
        public static final String FORM_SUBMISSION_ID = "form_submission_id";
        public static final String HEALTH_FACILITY_CHALLENGES_DETAIL =
                "health_facility_challenges_detail";
        public static final String HUMAN_READABLE_DETAILS = "human_readable_details";
        public static final String JSON_DETAILS = "json_details";
        public static final String MOTHERMENTOR_CLIENT_ID = "mothermentor_client_id";
        public static final String MOTHERMENTOR_TEST_DATE = "mothermentor_test_date";
        public static final String PARENT_CODE = "parent_code";
        public static final String PARENT_VISIT_ID = "parent_visit_id";
        public static final String PCG_FIRST_NAME = "pcg_first_name";
        public static final String PCG_LAST_NAME = "pcg_last_name";
        public static final String PCG_MIDDLE_NAME = "pcg_middle_name";
        public static final String PCG_PHONE_NUMBER = "pcg_phone_number";
        public static final String PRE_PROCESSED = "pre_processed";
        public static final String PREPROCESSED_DETAILS = "preprocessed_details";
        public static final String PREPROCESSED_TYPE = "preprocessed_type";
        public static final String PRIMARY_CLIENT = "primary_client";
        public static final String REASON_CLIENT_NOT_FOUND = "reason_client_not_found";
        public static final String
                REASONS_FOR_NOT_RETURNING_TO_SERVICES_WHILE_NOT_FACING_CHALLENGES =
                        "reasons_for_not_returning_to_services_while_not_facing_challenges";
        public static final String RETURN_TO_TREATMENT_PROMPT = "return_to_treatment_prompt";
        public static final String UPDATED_AT = "updated_at";
        public static final String VISIT_DETAILS_ID = "visit_details_id";
        public static final String VISIT_GROUP = "visit_group";
        public static final String VISIT_JSON = "visit_json";
        public static final String VISIT_NUMBER = "visit_number";
    }
}
