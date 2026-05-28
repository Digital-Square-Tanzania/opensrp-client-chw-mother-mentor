package org.smartregister.chw.mothermentor.util;

public interface Constants {

    int REQUEST_CODE_GET_JSON = 2244;
    String ENCOUNTER_TYPE = "encounter_type";
    String STEP_ONE = "step1";
    String STEP_TWO = "step2";
    String MOTHERMENTOR_VISIT_GROUP = "mothermentor_visit_group";


    interface JSON_FORM_EXTRA {
        String JSON = "json";
        String ENCOUNTER_TYPE = "encounter_type";
        String EVENT_TYPE = "eventType";
    }

    interface EVENT_TYPE {
        String MOTHER_MENTOR_SCREENING = "MotherMentor Screening";
        String MOTHER_MENTOR_SERVICES = "MotherMentor Services";
        String MOTHER_MENTOR_FOLLOW_UP_VISIT = "MotherMentor Follow-up Visit";
        String VOID_EVENT = "Void Event";
        String CLOSE_MOTHER_MENTOR_SERVICE = "Close MotherMentor Service";
        String MOTHER_MENTOR_MOBILIZATION = "MotherMentor Mobilization Session";
        String MOTHERMENTOR_CONTACT_VISIT = "MotherMentor Contact Visit";
        String MOTHER_MENTOR_OBSERVATIONS_RESULT = "MOTHERMENTOR Contact Observation Results";
        String MOTHER_MENTOR_RECORD_VISIT = "MotherMentor Visit";
        String MOTHER_MENTOR_CLIENT_OBSERVATION = "MOTHERMENTOR Observation Results";
        String MOTHERMENTOR_CONTACTS = "MotherMentor Contact Registration";
        String RECORD_LEPROSY_TREATMENT_START_DATE = "Record MotherMentor Treatment Start Date";

    }

    interface FORMS {
        String MOTHER_MENTOR_SCREENING = "mothermentor_screening";
        String MOTHERMENTOR_MOBILIZATION_SESSION = "mothermentor_mobilization_session";
        String MOTHERMENTOR_CONTACT_REGISTRATION = "mothermentor_contact_registration";
        String RECORD_MOTHERMENTOR_VISIT = "mothermentor_record_visit";
        String OBSERVATION_RESULTS = "mothermentor_observation_results";
        String CONTACT_OBSERVATION_RESULTS = "mothermentor_contact_observation_results";
        String MOTHERMENTOR_FOLLOWUP_VISIT = "mothermentor_followup_visit";
        String RECORD_LEPROSY_TREATMENT_START_DATE = "mothermentor_record_mother_mentor_start_date";

    }

    interface MotherMentor_FOLLOWUP_FORMS {

        String MOTHERMENTOR_CONTACT_TB_INVESTIGATION = "mother_mentor_contact_tb_investigation";
        String MOTHERMENTOR_CONTACT_LEPROSY_INVESTIGATION = "mother_mentor_contact_mother_mentor_investigation";
        String MOTHERMENTOR_INDEX_CLIENT_DETAILS_SOURCE = "mothermentor_index_client_details";
        String MOTHERMENTOR_SAMPLE = "mother_mentor_sample";
    }

    interface TABLES {
        String MOTHERMENTOR_SCREENING = "ec_mothermentor_screening";
        String MOTHERMENTOR_CONTACTS = "ec_mothermentor_contacts";
        String MOTHERMENTOR_SERVICES = "ec_mothermentor_services";
        String MOTHERMENTOR_MOBILIZATION = "ec_mothermentor_mobilization";
        String MOTHERMENTOR_VISIT = "ec_mothermentor_visit";
    }

    interface ACTIVITY_PAYLOAD {
        String BASE_ENTITY_ID = "BASE_ENTITY_ID";
        String FAMILY_BASE_ENTITY_ID = "FAMILY_BASE_ENTITY_ID";
        String MOTHER_MENTOR_FORM_NAME = "MOTHERMENTOR_FORM_NAME";
        String MEMBER_PROFILE_OBJECT = "MemberObject";
        String EDIT_MODE = "editMode";
        String PROFILE_TYPE = "profile_type";

    }

    interface ACTIVITY_PAYLOAD_TYPE {
        String REGISTRATION = "REGISTRATION";
        String FOLLOW_UP_VISIT = "FOLLOW_UP_VISIT";
    }

    interface CONFIGURATION {
        String MOTHERMENTOR_ENROLLMENT = "mothermentor_screening";
    }

    interface MOTHERMENTOR_MEMBER_OBJECT {
        String MEMBER_OBJECT = "memberObject";
    }

    interface PROFILE_TYPES {
        String MOTHERMENTOR_PROFILE = "mothermentor_profile";
    }

    interface VALUES {
        String NONE = "none";
        String CHORDAE = "chordae";
        String HIV = "hiv";
        String RBG = "random_blood_glucose_test";
        String FBG = "fast_blood_glucose_test";
        String HYPERTENSION = "hypertension";
        String SILICON_OR_LEXAN = "silicon_or_lexan";
        String NEGATIVE = "negative";
        String SATISFACTORY = "satisfactory";
        String NEEDS_FOLLOWUP = "needs_followup";
        String YES = "yes";
    }

    interface TABLE_COLUMN {
        String GENITAL_EXAMINATION = "genital_examination";
        String SYSTOLIC = "systolic";
        String DIASTOLIC = "diastolic";
        String ANY_COMPLAINTS = "any_complaints";
        String CLIENT_DIAGNONISED_WITH = "is_client_diagnosed_with_any";
        String COMPLICATION_TYPE = "type_complication";
        String HEMATOLOGICAL_DISEASE_SYMPTOMS = "any_hematological_disease_symptoms";
        String KNOWN_ALLEGIES = "known_allergies";
        String HIV_RESULTS = "hiv_result";
        String HIV_VIRAL_LOAD = "hiv_viral_load_text";
        String TYPE_OF_BLOOD_FOR_GLUCOSE_TEST = "type_of_blood_for_glucose_test";
        String BLOOD_FOR_GLUCOSE = "blood_for_glucose";
        String DISCHARGE_CONDITION = "discharge_condition";
        String IS_MALE_PROCEDURE_CIRCUMCISION_CONDUCTED = "is_male_procedure_circumcision_conducted";
    }

}
