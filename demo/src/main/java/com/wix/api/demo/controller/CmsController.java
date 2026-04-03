package com.wix.api.demo.controller;

import java.util.Map;

import com.wix.api.WixClient;
import com.wix.api.common.PagingRequest;
import com.wix.api.common.WixQueryFilter;
import com.wix.api.demo.WixClientProvider;
import com.wix.api.module.cms.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cms")
@RequiredArgsConstructor
public class CmsController {

    private final WixClientProvider provider;

    @GetMapping("/collections")
    public DataCollectionList listCollections(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return wix(apiKey, siteId).cms().listDataCollections(
                PagingRequest.builder().limit(limit).offset(offset).build());
    }

    @GetMapping("/collection")
    public DataCollection getCollection(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam String collectionId) {
        return wix(apiKey, siteId).cms().getDataCollection(collectionId);
    }

    @GetMapping("/items")
    public DataItemList queryItems(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam String collectionId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return wix(apiKey, siteId).cms().queryDataItems(collectionId,
                WixQueryFilter.create()
                        .withPaging(PagingRequest.builder().limit(limit).offset(offset).build()));
    }

    @GetMapping("/item")
    public DataItem getItem(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam String collectionId,
            @RequestParam String itemId) {
        return wix(apiKey, siteId).cms().getDataItem(collectionId, itemId);
    }

    @PostMapping("/items")
    public DataItem insertItem(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam String collectionId,
            @RequestBody Map<String, Object> data) {
        return wix(apiKey, siteId).cms().insertDataItem(collectionId, data);
    }

    @DeleteMapping("/item")
    public void removeItem(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam String collectionId,
            @RequestParam String itemId) {
        wix(apiKey, siteId).cms().removeDataItem(collectionId, itemId);
    }

    private WixClient wix(String apiKey, String siteId) {
        return provider.get(apiKey, siteId);
    }
}
