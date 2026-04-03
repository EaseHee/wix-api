package com.wix.api.module.contacts.dto;

import java.util.List;

import com.wix.api.common.PagingMetadata;
import lombok.Data;

@Data
public class ContactList {

    private List<Contact> contacts;
    private PagingMetadata pagingMetadata;
}
