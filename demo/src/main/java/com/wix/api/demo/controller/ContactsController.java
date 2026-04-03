package com.wix.api.demo.controller;

import java.util.List;
import java.util.Map;

import com.wix.api.WixClient;
import com.wix.api.common.PagingRequest;
import com.wix.api.demo.WixClientProvider;
import com.wix.api.module.contacts.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactsController {

    private final WixClientProvider provider;

    @GetMapping
    public ContactList list(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return wix(apiKey, siteId).contacts().listContacts(
                PagingRequest.builder().limit(limit).offset(offset).build());
    }

    @GetMapping("/{contactId}")
    public Contact get(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @PathVariable String contactId) {
        return wix(apiKey, siteId).contacts().getContact(contactId);
    }

    @PostMapping("/query")
    public ContactList query(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @RequestBody QueryContactsRequest request) {
        return wix(apiKey, siteId).contacts().queryContacts(request);
    }

    @DeleteMapping("/{contactId}")
    public void delete(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @PathVariable String contactId) {
        wix(apiKey, siteId).contacts().deleteContact(contactId);
    }

    @GetMapping("/labels")
    public LabelList listLabels(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return wix(apiKey, siteId).contacts().listLabels(
                PagingRequest.builder().limit(limit).offset(offset).build());
    }

    private WixClient wix(String apiKey, String siteId) {
        return provider.get(apiKey, siteId);
    }
}
