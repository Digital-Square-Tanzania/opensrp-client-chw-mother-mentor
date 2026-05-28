package org.smartregister.chw.mothermentor.domain;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.smartregister.util.Utils;

import java.io.Serializable;
import java.util.Date;

public class MemberObject implements Serializable {

    private String familyHeadName;
    private String familyHeadPhoneNumber;
    private String firstName;
    private String middleName;
    private String lastName;
    private String address;
    private String gender;
    private String martialStatus;
    private String uniqueId;
    private String age;
    private String dob;
    private String relationalid;
    private String details;
    private String dateChwMotherMentorTest;
    private String feverMotherMentorChw;
    private String feverDuration;
    private String dateHfMotherMentorTest;
    private Date mothermentorTestDate;
    private String mothermentorTreat;
    private String famLlin;
    private String llin2Days;
    private String llinCondition;
    private String mothermentorEduChw;
    private String baseEntityId;
    private String relationalId;
    private String primaryCareGiver;
    private String primaryCareGiverName;
    private String primaryCareGiverPhone;
    private String familyHead;
    private String familyBaseEntityId;
    private String familyName;
    private String phoneNumber;
    private String mothermentorFollowUpDate;
    private String enrollmentDate;
    private String mother_mentorClientNumber;
    private String tbClientNumber;

    public MemberObject() {
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return Utils.getName(getFirstName(), getLastName());
    }

    public int getAge() {
        return new Period(new DateTime(dob), new DateTime()).getYears();
    }

    public void setAge(String age) {
        this.age = age;
    }

    public int getVisitAge() {

        return new Period(new DateTime(dob), new DateTime()).getYears();
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getMartialStatus() {
        return martialStatus;
    }
    public void setMartialStatus(String martialStatus) {
        this.martialStatus = martialStatus;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getRelationalid() {
        return relationalid;
    }

    public void setRelationalid(String relationalid) {
        this.relationalid = relationalid;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getDateChwMotherMentorTest() {
        return dateChwMotherMentorTest;
    }

    public void setDateChwMotherMentorTest(String dateChwMotherMentorTest) {
        this.dateChwMotherMentorTest = dateChwMotherMentorTest;
    }

    public String getFeverMotherMentorChw() {
        return feverMotherMentorChw;
    }

    public void setFeverMotherMentorChw(String feverMotherMentorChw) {
        this.feverMotherMentorChw = feverMotherMentorChw;
    }

    public String getFeverDuration() {
        return feverDuration;
    }

    public void setFeverDuration(String feverDuration) {
        this.feverDuration = feverDuration;
    }

    public String getDateHfMotherMentorTest() {
        return dateHfMotherMentorTest;
    }

    public void setDateHfMotherMentorTest(String dateHfMotherMentorTest) {
        this.dateHfMotherMentorTest = dateHfMotherMentorTest;
    }

    public Date getMotherMentorTestDate() {
        return mothermentorTestDate;
    }

    public void setMotherMentorTestDate(Date mothermentorTestDate) {
        this.mothermentorTestDate = mothermentorTestDate;
    }

    public String getMotherMentorTreat() {
        return mothermentorTreat;
    }

    public void setMotherMentorTreat(String mothermentorTreat) {
        this.mothermentorTreat = mothermentorTreat;
    }

    public String getFamLlin() {
        return famLlin;
    }

    public void setFamLlin(String famLlin) {
        this.famLlin = famLlin;
    }

    public String getLlin2Days() {
        return llin2Days;
    }

    public void setLlin2Days(String llin2Days) {
        this.llin2Days = llin2Days;
    }

    public String getLlinCondition() {
        return llinCondition;
    }

    public void setLlinCondition(String llinCondition) {
        this.llinCondition = llinCondition;
    }

    public String getMotherMentorEduChw() {
        return mothermentorEduChw;
    }

    public void setMotherMentorEduChw(String mothermentorEduChw) {
        this.mothermentorEduChw = mothermentorEduChw;
    }

    public String getBaseEntityId() {
        return baseEntityId;
    }

    public void setBaseEntityId(String baseEntityId) {
        this.baseEntityId = baseEntityId;
    }

    public String getRelationalId() {
        return relationalId;
    }

    public void setRelationalId(String relationalId) {
        this.relationalId = relationalId;
    }

    public String getFamilyBaseEntityId() {
        return familyBaseEntityId;
    }

    public void setFamilyBaseEntityId(String familyBaseEntityId) {
        this.familyBaseEntityId = familyBaseEntityId;
    }

    public String getPrimaryCareGiver() {
        return primaryCareGiver;
    }

    public void setPrimaryCareGiver(String primaryCareGiver) {
        this.primaryCareGiver = primaryCareGiver;
    }

    public String getFamilyHead() {
        return familyHead;
    }

    public void setFamilyHead(String familyHead) {
        this.familyHead = familyHead;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }


    public String getFamilyHeadName() {
        return familyHeadName;
    }

    public void setFamilyHeadName(String familyHeadName) {
        this.familyHeadName = familyHeadName;
    }

    public String getFamilyHeadPhoneNumber() {
        return familyHeadPhoneNumber;
    }

    public void setFamilyHeadPhoneNumber(String familyHeadPhoneNumber) {
        this.familyHeadPhoneNumber = familyHeadPhoneNumber;
    }

    public String getPrimaryCareGiverName() {
        return primaryCareGiverName;
    }

    public void setPrimaryCareGiverName(String primaryCareGiverName) {
        this.primaryCareGiverName = primaryCareGiverName;
    }

    public String getPrimaryCareGiverPhone() {
        return primaryCareGiverPhone;
    }

    public void setPrimaryCareGiverPhone(String primaryCareGiverPhone) {
        this.primaryCareGiverPhone = primaryCareGiverPhone;
    }

    public String getMotherMentorFollowUpDate() {
        return mothermentorFollowUpDate;
    }

    public void setMotherMentorFollowUpDate(String mothermentorFollowUpDate) {
        this.mothermentorFollowUpDate = mothermentorFollowUpDate;
    }

    public String getEnrollmentDate() {
        return enrollmentDate;
    }

    public void setEnrollmentDate(String enrollmentDate) {
        this.enrollmentDate = enrollmentDate;
    }

    public String getMotherMentorClientNumber() {
        return mother_mentorClientNumber;
    }

    public void setMotherMentorClientNumber(String mother_mentorClientNumber) {
        this.mother_mentorClientNumber = mother_mentorClientNumber;
    }

    public String getTbClientNumber() {
        return tbClientNumber;
    }

    public void setTbClientNumber(String tbClientNumber) {
        this.tbClientNumber = tbClientNumber;
    }
}
