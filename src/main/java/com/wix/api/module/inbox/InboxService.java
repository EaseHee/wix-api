package com.wix.api.module.inbox;

import java.util.Map;

import com.wix.api.common.CursorPagingRequest;
import com.wix.api.http.RetryHandler;
import com.wix.api.module.AbstractWixService;
import com.wix.api.module.inbox.dto.*;
import org.springframework.web.client.RestClient;

public class InboxService extends AbstractWixService {

    public InboxService(RestClient restClient, RetryHandler retryHandler) {
        super(restClient, retryHandler);
    }

    public Conversation getConversation(String conversationId) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.get()
                    .uri("/inbox/v2/conversations/{conversationId}", conversationId)
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "conversation", Conversation.class);
        });
    }

    public Conversation getOrCreateConversation(String contactId) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.post()
                    .uri("/inbox/v2/conversations")
                    .body(Map.of("participantId", Map.of("contactId", contactId)))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "conversation", Conversation.class);
        });
    }

    /**
     * 대화의 메시지 목록 조회.
     *
     * @param conversationId 대화 ID (필수)
     * @param visibility     BUSINESS | BUSINESS_AND_PARTICIPANT (ALL/PARTICIPANT은 미지원)
     * @param sortOrder      ASC | DESC (sequence 기준, 기본 DESC = 최신순)
     * @param paging         cursor 기반 페이징
     */
    public MessageList listMessages(String conversationId, String visibility,
                                     String sortOrder, CursorPagingRequest paging) {
        return retryHandler.executeWithRetry(() ->
                restClient.get()
                        .uri(uriBuilder -> {
                            uriBuilder.path("/inbox/v2/messages")
                                    .queryParam("conversationId", conversationId)
                                    .queryParam("visibility", visibility)
                                    .queryParam("paging.limit", paging.getLimit());
                            if (paging.getCursor() != null) {
                                uriBuilder.queryParam("paging.cursor", paging.getCursor());
                            }
                            if (sortOrder != null) {
                                uriBuilder.queryParam("sorting.fieldName", "sequence");
                                uriBuilder.queryParam("sorting.order", sortOrder);
                            }
                            return uriBuilder.build();
                        })
                        .retrieve()
                        .body(MessageList.class)
        );
    }

    public MessageList listMessages(String conversationId, String visibility, CursorPagingRequest paging) {
        return listMessages(conversationId, visibility, null, paging);
    }

    public MessageList listMessages(String conversationId, CursorPagingRequest paging) {
        return listMessages(conversationId, "BUSINESS", null, paging);
    }

    public Message sendMessage(String conversationId, SendMessageRequest request) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.post()
                    .uri("/inbox/v2/conversations/{conversationId}/messages/send", conversationId)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "message", Message.class);
        });
    }
}
