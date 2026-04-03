package com.wix.api.module.contacts.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ContactInfo {

    private Name name;
    private Emails emails;
    private Phones phones;
    private Addresses addresses;
    private String company;
    private String jobTitle;
    private String birthdate;
    private LabelKeys labelKeys;
    private Map<String, Object> extendedFields;
    private Map<String, Object> picture;

    @Data
    public static class LabelKeys {
        private List<String> items;
    }

    @Data
    public static class Name {
        private String first;
        private String last;
    }

    @Data
    public static class Emails {
        private List<Email> items;
    }

    @Data
    public static class Email {
        private String id;
        private String email;
        private String tag;
        private boolean primary;
    }

    @Data
    public static class Phones {
        private List<Phone> items;
    }

    @Data
    public static class Phone {
        private String id;
        private String phone;
        private String tag;
        private boolean primary;
    }

    @Data
    public static class Addresses {
        private List<Address> items;
    }

    @Data
    public static class Address {
        private String id;
        private String tag;
        private String city;
        private String subdivision;
        private String country;
        private String postalCode;
        private String addressLine;
    }
}
