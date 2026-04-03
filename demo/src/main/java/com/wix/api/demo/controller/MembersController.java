package com.wix.api.demo.controller;

import com.wix.api.WixClient;
import com.wix.api.common.PagingRequest;
import com.wix.api.demo.WixClientProvider;
import com.wix.api.module.members.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MembersController {

    private final WixClientProvider provider;

    @GetMapping
    public MemberList list(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return wix(apiKey, siteId).members().listMembers(
                PagingRequest.builder().limit(limit).offset(offset).build());
    }

    @GetMapping("/{memberId}")
    public Member get(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @PathVariable String memberId) {
        return wix(apiKey, siteId).members().getMember(memberId);
    }

    @PostMapping("/query")
    public MemberList query(
            @RequestHeader(value="X-Wix-Api-Key",required=false) String apiKey,
            @RequestHeader(value="X-Wix-Site-Id",required=false) String siteId,
            @RequestBody QueryMembersRequest request) {
        return wix(apiKey, siteId).members().queryMembers(request);
    }

    private WixClient wix(String apiKey, String siteId) {
        return provider.get(apiKey, siteId);
    }
}
