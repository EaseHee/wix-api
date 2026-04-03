package com.wix.api.module.members;

import java.util.Map;

import com.wix.api.common.PagingRequest;
import com.wix.api.http.RetryHandler;
import com.wix.api.module.AbstractWixService;
import com.wix.api.module.members.dto.*;
import org.springframework.web.client.RestClient;

public class MembersService extends AbstractWixService {

    public MembersService(RestClient restClient, RetryHandler retryHandler) {
        super(restClient, retryHandler);
    }

    public Member getMember(String memberId) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.get()
                    .uri("/members/v1/members/{memberId}", memberId)
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "member", Member.class);
        });
    }

    public MemberList listMembers(PagingRequest paging) {
        return retryHandler.executeWithRetry(() ->
                restClient.post()
                        .uri("/members/v1/members/query")
                        .body(Map.of(
                                "paging", Map.of("limit", paging.getLimit(), "offset", paging.getOffset())
                        ))
                        .retrieve()
                        .body(MemberList.class)
        );
    }

    public MemberList queryMembers(QueryMembersRequest request) {
        return retryHandler.executeWithRetry(() ->
                restClient.post()
                        .uri("/members/v1/members/query")
                        .body(request)
                        .retrieve()
                        .body(MemberList.class)
        );
    }

    public Member createMember(Member member) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.post()
                    .uri("/members/v1/members")
                    .body(Map.of("member", member))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "member", Member.class);
        });
    }

    public Member updateMember(String memberId, Member member) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.patch()
                    .uri("/members/v1/members/{memberId}", memberId)
                    .body(Map.of("member", member))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "member", Member.class);
        });
    }

    public void deleteMember(String memberId) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.delete()
                        .uri("/members/v1/members/{memberId}", memberId)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public void approveMember(String memberId) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.post()
                        .uri("/members/v1/members/{memberId}/approve", memberId)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public void blockMember(String memberId) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.post()
                        .uri("/members/v1/members/{memberId}/block", memberId)
                        .retrieve()
                        .toBodilessEntity()
        );
    }
}
