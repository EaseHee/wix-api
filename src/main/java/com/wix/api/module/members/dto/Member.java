package com.wix.api.module.members.dto;

import java.util.Map;

import lombok.Data;

@Data
public class Member {

    private String id;
    private String loginEmail;
    private String status;
    private String contactId;
    private String privacyStatus;
    private String activityStatus;
    private String createdDate;
    private String updatedDate;
    private Profile profile;

    @Data
    public static class Profile {
        private String nickname;
        private String slug;
        private Map<String, Object> profilePhoto;
        private String title;
    }
}
