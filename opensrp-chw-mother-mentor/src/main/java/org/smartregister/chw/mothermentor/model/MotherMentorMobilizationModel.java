package org.smartregister.chw.mothermentor.model;

public class MotherMentorMobilizationModel {
    private String sessionDate;
    private String sessionId;
    private String sessionParticipants;
    private String condomsIssued;
    private String activityType;
    private String activityTypeOther;
    private String gps;
    private String educationTopics;
    private String educationTopicsOther;
    private String menEducated;
    private String womenEducated;
    private String totalEducated;
    private String comments;

    public MotherMentorMobilizationModel() {
    }

    public MotherMentorMobilizationModel(String sessionDate, String sessionParticipants, String sessionId, String condomsIssued) {
        this.sessionDate = sessionDate;
        this.sessionParticipants = sessionParticipants;
        this.sessionId = sessionId;
        this.condomsIssued = condomsIssued;
    }

    public String getSessionParticipants() {
        return sessionParticipants;
    }

    public void setSessionParticipants(String sessionParticipants) {
        this.sessionParticipants = sessionParticipants;
    }

    public String getSessionDate() {
        return sessionDate;
    }


    public void setSessionDate(String sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCondomsIssued() {
        return condomsIssued;
    }

    public void setCondomsIssued(String condomsIssued) {
        this.condomsIssued = condomsIssued;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public String getActivityTypeOther() {
        return activityTypeOther;
    }

    public void setActivityTypeOther(String activityTypeOther) {
        this.activityTypeOther = activityTypeOther;
    }

    public String getGps() {
        return gps;
    }

    public void setGps(String gps) {
        this.gps = gps;
    }

    public String getEducationTopics() {
        return educationTopics;
    }

    public void setEducationTopics(String educationTopics) {
        this.educationTopics = educationTopics;
    }

    public String getEducationTopicsOther() {
        return educationTopicsOther;
    }

    public void setEducationTopicsOther(String educationTopicsOther) {
        this.educationTopicsOther = educationTopicsOther;
    }

    public String getMenEducated() {
        return menEducated;
    }

    public void setMenEducated(String menEducated) {
        this.menEducated = menEducated;
    }

    public String getWomenEducated() {
        return womenEducated;
    }

    public void setWomenEducated(String womenEducated) {
        this.womenEducated = womenEducated;
    }

    public String getTotalEducated() {
        return totalEducated;
    }

    public void setTotalEducated(String totalEducated) {
        this.totalEducated = totalEducated;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
    private String referredMen;
    public String getReferredMen() { return referredMen; }
    public void setReferredMen(String value) { referredMen = value; }

    private String referredWomen;
    public String getReferredWomen() { return referredWomen; }
    public void setReferredWomen(String value) { referredWomen = value; }

    private String referredTotal;
    public String getReferredTotal() { return referredTotal; }
    public void setReferredTotal(String value) { referredTotal = value; }

    private String referralServices;
    public String getReferralServices() { return referralServices; }
    public void setReferralServices(String value) { referralServices = value; }

    private String referralOther;
    public String getReferralOther() { return referralOther; }
    public void setReferralOther(String value) { referralOther = value; }

}
