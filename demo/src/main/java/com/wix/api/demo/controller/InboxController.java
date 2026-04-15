package com.wix.api.demo.controller;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.wix.api.WixClient;
import com.wix.api.common.CursorPagingRequest;
import com.wix.api.common.PagingRequest;
import com.wix.api.demo.WixClientProvider;
import com.wix.api.module.contacts.dto.*;
import com.wix.api.module.inbox.dto.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxController {

    private static final Logger log = LoggerFactory.getLogger(InboxController.class);
    private final WixClientProvider provider;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final InboxCache cache = new InboxCache();

    /**
     * 대화 목록: 연락처 기반 조회 + 캐시 적용.
     * conversation/메시지 프리뷰를 캐시하여 반복 호출 시 API 호출을 대폭 절감.
     * 첫 호출: contact당 2회(conversation+message) → 재호출: 캐시 히트로 0회.
     */
    @GetMapping("/conversations")
    public Map<String, Object> listConversations(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam(defaultValue = "5") int scanPages) {
        WixClient wix = provider.get(apiKey, siteId);

        List<Map<String, Object>> allConversations = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        int pageSize = 100;
        int totalContacts = 0;

        for (int page = 0; page < scanPages; page++) {
            ContactList contactList = wix.contacts().queryContacts(
                    QueryContactsRequest.builder()
                            .sort(List.of(Map.of("fieldName", "createdDate", "order", "DESC")))
                            .paging(PagingRequest.builder().limit(pageSize).offset(page * pageSize).build())
                            .build());
            if (contactList.getContacts() == null || contactList.getContacts().isEmpty()) break;

            if (page == 0 && contactList.getPagingMetadata() != null) {
                Integer t = contactList.getPagingMetadata().getTotal();
                if (t != null) totalContacts = t;
            }

            List<CompletableFuture<BuildResult>> futures = contactList.getContacts().stream()
                    .map(contact -> CompletableFuture.supplyAsync(
                            () -> enrichFromContact(wix, contact), executor))
                    .toList();

            Set<String> seenIds = allConversations.stream()
                    .map(c -> (String) c.get("conversationId"))
                    .collect(Collectors.toSet());

            for (CompletableFuture<BuildResult> future : futures) {
                BuildResult result = future.join();
                if (result.summary != null) {
                    if (seenIds.add((String) result.summary.get("conversationId"))) {
                        allConversations.add(result.summary);
                    }
                } else if (result.error != null) {
                    errors.add(result.error);
                }
            }

            if (!contactList.getPagingMetadata().isHasNext()) break;
        }

        // 메시지 있는 대화 우선, 날짜 내림차순
        allConversations.sort((a, b) -> {
            boolean aHas = (boolean) a.get("hasMessages");
            boolean bHas = (boolean) b.get("hasMessages");
            if (aHas != bHas) return aHas ? -1 : 1;
            return ((String) b.getOrDefault("lastDate", "")).compareTo((String) a.getOrDefault("lastDate", ""));
        });

        long activeCount = allConversations.stream().filter(c -> (boolean) c.get("hasMessages")).count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversations", allConversations);
        response.put("activeCount", activeCount);
        response.put("totalScanned", Math.min(scanPages * pageSize, totalContacts));
        response.put("totalContacts", totalContacts);
        if (!errors.isEmpty()) {
            response.put("errors", errors);
            response.put("errorCount", errors.size());
        }
        return response;
    }

    /**
     * 특정 연락처의 대화 조회를 진단.
     * conversation 존재 여부, 메시지 유무, visibility별 메시지 수를 확인.
     */
    @GetMapping("/diagnose/{contactId}")
    public Map<String, Object> diagnoseContact(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @PathVariable String contactId) {
        WixClient wix = provider.get(apiKey, siteId);
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Contact 존재 확인
        try {
            Contact contact = wix.contacts().getContact(contactId);
            if (contact != null) {
                result.put("contactExists", true);
                result.put("contactName", extractName(contact));
                result.put("contactCreatedDate", contact.getCreatedDate());
                result.put("contactLastActivity", contact.getLastActivity());
                result.put("contactSource", contact.getSource());
            } else {
                result.put("contactExists", false);
            }
        } catch (Exception e) {
            result.put("contactExists", false);
            result.put("contactError", e.getMessage());
        }

        // 2. Conversation 조회
        try {
            Conversation conv = wix.inbox().getOrCreateConversation(contactId);
            if (conv != null && conv.getId() != null) {
                result.put("conversationExists", true);
                result.put("conversationId", conv.getId());
                result.put("conversationChannels", conv.getChannels());
                result.put("conversationCreatedDate", conv.getCreatedDate());
                result.put("participant", conv.getParticipant());

                // 3. visibility별 메시지 수 비교
                for (String visibility : List.of("BUSINESS", "BUSINESS_AND_PARTICIPANT")) {
                    try {
                        MessageList ml = wix.inbox().listMessages(
                                conv.getId(), visibility,
                                CursorPagingRequest.builder().limit(5).build());
                        int count = (ml != null && ml.getMessages() != null) ? ml.getMessages().size() : 0;
                        boolean hasMore = ml != null && ml.getPagingMetadata() != null
                                && ml.getPagingMetadata().getCursors() != null
                                && ml.getPagingMetadata().getCursors().getNext() != null;

                        Map<String, Object> visResult = new LinkedHashMap<>();
                        visResult.put("messageCount", hasMore ? count + "+" : String.valueOf(count));
                        if (count > 0) {
                            List<Map<String, Object>> msgSummaries = new ArrayList<>();
                            for (Message msg : ml.getMessages()) {
                                Map<String, Object> ms = new LinkedHashMap<>();
                                ms.put("id", msg.getId());
                                ms.put("direction", msg.getDirection());
                                ms.put("contentType", msg.getContent() != null
                                        ? msg.getContent().getOrDefault("contentType", "UNKNOWN") : "UNKNOWN");
                                ms.put("previewText", msg.getContent() != null
                                        ? msg.getContent().getOrDefault("previewText", "") : "");
                                ms.put("createdDate", msg.getCreatedDate());
                                ms.put("visibility", msg.getVisibility());
                                ms.put("sourceChannel", msg.getSourceChannel());
                                msgSummaries.add(ms);
                            }
                            visResult.put("messages", msgSummaries);
                        }
                        result.put("visibility_" + visibility, visResult);
                    } catch (Exception e) {
                        result.put("visibility_" + visibility + "_error", e.getMessage());
                    }
                }
            } else {
                result.put("conversationExists", false);
            }
        } catch (Exception e) {
            result.put("conversationExists", false);
            result.put("conversationError", e.getMessage());
        }

        return result;
    }

    /**
     * conversationId로 직접 진단: conversation 조회, 메시지 조회, 에러 상세 보고.
     * 여러 conversationId를 콤마로 구분하여 일괄 비교 가능.
     * 예: /api/inbox/diagnose/conversations?ids=aaa,bbb,ccc
     */
    @GetMapping("/diagnose/conversations")
    public Map<String, Object> diagnoseConversations(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam String ids) {
        WixClient wix = provider.get(apiKey, siteId);
        String[] convIds = ids.split(",");
        List<Map<String, Object>> results = new ArrayList<>();

        for (String convId : convIds) {
            convId = convId.trim();
            Map<String, Object> diag = new LinkedHashMap<>();
            diag.put("conversationId", convId);

            // 1. Conversation 직접 조회
            try {
                Conversation conv = wix.inbox().getConversation(convId);
                if (conv != null) {
                    diag.put("conversationFound", true);
                    diag.put("channels", conv.getChannels());
                    diag.put("createdDate", conv.getCreatedDate());
                    diag.put("participant", conv.getParticipant());
                    diag.put("participantDisplayData", conv.getParticipantDisplayData());
                    diag.put("businessDisplayData", conv.getBusinessDisplayData());

                    // participant에서 contactId/visitorId 추출
                    if (conv.getParticipant() != null) {
                        diag.put("participantType",
                                conv.getParticipant().containsKey("contactId") ? "CONTACT" :
                                conv.getParticipant().containsKey("visitorId") ? "VISITOR" :
                                conv.getParticipant().containsKey("memberId") ? "MEMBER" : "UNKNOWN");
                    }
                } else {
                    diag.put("conversationFound", false);
                    diag.put("conversationError", "getConversation returned null");
                }
            } catch (Exception e) {
                diag.put("conversationFound", false);
                diag.put("conversationError", e.getMessage());
            }

            // 2. 메시지 조회 (visibility별)
            for (String vis : List.of("BUSINESS", "BUSINESS_AND_PARTICIPANT")) {
                try {
                    MessageList ml = wix.inbox().listMessages(convId, vis,
                            CursorPagingRequest.builder().limit(5).build());
                    int count = (ml != null && ml.getMessages() != null) ? ml.getMessages().size() : 0;
                    boolean hasMore = ml != null && ml.getPagingMetadata() != null
                            && ml.getPagingMetadata().getCursors() != null
                            && ml.getPagingMetadata().getCursors().getNext() != null;

                    Map<String, Object> visResult = new LinkedHashMap<>();
                    visResult.put("count", hasMore ? count + "+" : String.valueOf(count));
                    if (count > 0) {
                        List<Map<String, Object>> msgList = new ArrayList<>();
                        for (Message msg : ml.getMessages()) {
                            Map<String, Object> ms = new LinkedHashMap<>();
                            ms.put("id", msg.getId());
                            ms.put("direction", msg.getDirection());
                            ms.put("visibility", msg.getVisibility());
                            ms.put("contentType", msg.getContent() != null
                                    ? msg.getContent().getOrDefault("contentType", "UNKNOWN") : "UNKNOWN");
                            ms.put("previewText", msg.getContent() != null
                                    ? msg.getContent().getOrDefault("previewText", "") : "");
                            ms.put("createdDate", msg.getCreatedDate());
                            ms.put("sourceChannel", msg.getSourceChannel());
                            ms.put("sender", msg.getSender());
                            msgList.add(ms);
                        }
                        visResult.put("messages", msgList);
                    }
                    diag.put("messages_" + vis, visResult);
                } catch (Exception e) {
                    diag.put("messages_" + vis + "_error", e.getMessage());
                }
            }

            results.add(diag);
        }

        return Map.of("diagnoses", results);
    }

    /**
     * 대화 상세: conversation 메타 + contact 상세 + 전체 메시지 스레드 통합 조회.
     * contactId가 없으면 conversation의 participant에서 자동 추출.
     * contact 조회와 메시지 조회를 병렬 실행하여 지연 시간 최소화.
     */
    @GetMapping("/conversations/{conversationId}/detail")
    public Map<String, Object> conversationDetail(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @PathVariable String conversationId,
            @RequestParam(required = false) String contactId,
            @RequestParam(defaultValue = "50") int messageLimit) {
        WixClient wix = provider.get(apiKey, siteId);

        // 1. Conversation 정보 (contactId 추출을 위해 선행)
        Conversation conv = wix.inbox().getConversation(conversationId);
        Map<String, Object> convInfo = new LinkedHashMap<>();
        if (conv != null) {
            convInfo.put("id", conv.getId());
            convInfo.put("channels", conv.getChannels());
            convInfo.put("createdDate", conv.getCreatedDate());
            convInfo.put("participant", conv.getParticipant());
            convInfo.put("participantDisplayData", conv.getParticipantDisplayData());
            convInfo.put("businessDisplayData", conv.getBusinessDisplayData());

            if ((contactId == null || contactId.isBlank())) {
                contactId = extractContactId(conv);
            }
        }

        // 2. Contact 조회 + 메시지 조회를 병렬 실행
        final String finalContactId = contactId;

        CompletableFuture<Map<String, Object>> contactFuture = CompletableFuture.supplyAsync(() -> {
            Map<String, Object> info = new LinkedHashMap<>();
            if (finalContactId == null || finalContactId.isBlank()) return info;
            try {
                Contact contact = cache.getContact(finalContactId);
                if (contact == null) {
                    contact = wix.contacts().getContact(finalContactId);
                    if (contact != null) cache.putContact(finalContactId, contact);
                }
                if (contact != null) {
                    info.put("id", contact.getId());
                    info.put("revision", contact.getRevision());
                    info.put("createdDate", contact.getCreatedDate());
                    info.put("updatedDate", contact.getUpdatedDate());
                    info.put("source", contact.getSource());
                    info.put("lastActivity", contact.getLastActivity());
                    info.put("picture", contact.getPicture());
                    info.put("primaryInfo", contact.getPrimaryInfo());
                    if (contact.getInfo() != null) {
                        ContactInfo ci = contact.getInfo();
                        info.put("name", extractName(contact));
                        if (ci.getEmails() != null && ci.getEmails().getItems() != null) {
                            info.put("emails", ci.getEmails().getItems().stream()
                                    .map(e -> Map.of("email", e.getEmail(),
                                            "tag", e.getTag() != null ? e.getTag() : "",
                                            "primary", e.isPrimary()))
                                    .toList());
                        }
                        if (ci.getPhones() != null && ci.getPhones().getItems() != null) {
                            info.put("phones", ci.getPhones().getItems().stream()
                                    .map(p -> Map.of("phone", p.getPhone(),
                                            "tag", p.getTag() != null ? p.getTag() : "",
                                            "primary", p.isPrimary()))
                                    .toList());
                        }
                        if (ci.getAddresses() != null && ci.getAddresses().getItems() != null) {
                            info.put("addresses", ci.getAddresses().getItems());
                        }
                        if (ci.getCompany() != null) info.put("company", ci.getCompany());
                        if (ci.getJobTitle() != null) info.put("jobTitle", ci.getJobTitle());
                        if (ci.getLabelKeys() != null && ci.getLabelKeys().getItems() != null) {
                            info.put("labelKeys", ci.getLabelKeys().getItems());
                        }
                        if (ci.getExtendedFields() != null) {
                            info.put("extendedFields", ci.getExtendedFields());
                        }
                    }
                }
            } catch (Exception e) {
                info.put("error", e.getMessage());
            }
            return info;
        }, executor);

        CompletableFuture<List<Map<String, Object>>> messagesFuture = CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> allMessages = new ArrayList<>();
            String msgCursor = null;
            int fetched = 0;
            int maxPages = 10;
            int page = 0;
            while (fetched < messageLimit && page < maxPages) {
                int batchSize = Math.min(50, messageLimit - fetched);
                MessageList ml = wix.inbox().listMessages(conversationId,
                        "BUSINESS_AND_PARTICIPANT", "ASC",
                        CursorPagingRequest.builder().limit(batchSize).cursor(msgCursor).build());
                if (ml == null || ml.getMessages() == null || ml.getMessages().isEmpty()) break;
                for (Message msg : ml.getMessages()) {
                    allMessages.add(parseMessageFull(msg));
                    fetched++;
                }
                if (ml.getPagingMetadata() != null && ml.getPagingMetadata().getCursors() != null
                        && ml.getPagingMetadata().getCursors().getNext() != null) {
                    msgCursor = ml.getPagingMetadata().getCursors().getNext();
                } else {
                    break;
                }
                page++;
            }
            return allMessages;
        }, executor);

        Map<String, Object> contactInfo = contactFuture.join();
        List<Map<String, Object>> allMessages = messagesFuture.join();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversation", convInfo);
        result.put("contact", contactInfo);
        result.put("messages", allMessages);
        result.put("messageCount", allMessages.size());
        result.put("hasMoreMessages", allMessages.size() >= messageLimit);
        return result;
    }

    /**
     * 대화의 메시지 목록.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Map<String, Object> listMessages(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "BUSINESS_AND_PARTICIPANT") String visibility,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) String cursor) {
        WixClient wix = provider.get(apiKey, siteId);
        MessageList ml = wix.inbox().listMessages(conversationId, visibility, sortOrder,
                CursorPagingRequest.builder().limit(limit).cursor(cursor).build());

        List<Map<String, Object>> messages = new ArrayList<>();
        if (ml != null && ml.getMessages() != null) {
            for (Message msg : ml.getMessages()) {
                messages.add(parseMessageFull(msg));
            }
        }

        String nextCursor = null;
        if (ml != null && ml.getPagingMetadata() != null
                && ml.getPagingMetadata().getCursors() != null) {
            nextCursor = ml.getPagingMetadata().getCursors().getNext();
        }

        return Map.of(
                "messages", messages,
                "total", messages.size(),
                "nextCursor", nextCursor != null ? nextCursor : ""
        );
    }

    /**
     * 메시지 발송 (비즈니스 → 참여자)
     */
    @PostMapping("/conversations/{conversationId}/messages/send")
    public Map<String, Object> sendMessage(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @PathVariable String conversationId,
            @RequestBody SendMessageRequest request) {
        WixClient wix = provider.get(apiKey, siteId);
        Message msg = wix.inbox().sendMessage(conversationId, request);
        if (msg != null) {
            cache.invalidateConversation(conversationId);
            return Map.of("success", true, "message", parseMessageFull(msg));
        }
        return Map.of("success", false);
    }

    // ── 내부 ──

    private record BuildResult(Map<String, Object> summary, Map<String, Object> error) {
        static BuildResult ok(Map<String, Object> summary) { return new BuildResult(summary, null); }
        static BuildResult fail(String contactId, String contactName, String reason) {
            return new BuildResult(null, Map.of(
                    "contactId", contactId, "contactName", contactName, "reason", reason));
        }
    }

    private BuildResult enrichFromContact(WixClient wix, Contact contact) {
        String contactName = extractName(contact);
        try {
            // 1. Conversation (캐시 우선)
            Conversation conv = cache.getConversation(contact.getId());
            if (conv == null) {
                conv = wix.inbox().getOrCreateConversation(contact.getId());
                if (conv != null) cache.putConversation(contact.getId(), conv);
            }
            if (conv == null || conv.getId() == null) {
                return BuildResult.fail(contact.getId(), contactName, "conversation is null");
            }

            // 2. 메시지 프리뷰 (캐시 우선)
            String lastPreview = "", lastDate = "", lastChannel = "", lastContentType = "", lastDirection = "";
            boolean hasMessages = false;

            Map<String, Object> cachedPreview = cache.getMessagePreview(conv.getId());
            if (cachedPreview != null) {
                hasMessages = (boolean) cachedPreview.getOrDefault("hasMessages", false);
                lastPreview = (String) cachedPreview.getOrDefault("lastPreview", "");
                lastDate = (String) cachedPreview.getOrDefault("lastDate", "");
                lastChannel = (String) cachedPreview.getOrDefault("lastChannel", "");
                lastContentType = (String) cachedPreview.getOrDefault("lastContentType", "");
                lastDirection = (String) cachedPreview.getOrDefault("lastDirection", "");
            } else {
                MessageList msgs = wix.inbox().listMessages(
                        conv.getId(), "BUSINESS_AND_PARTICIPANT",
                        CursorPagingRequest.builder().limit(1).build());

                if (msgs != null && msgs.getMessages() != null && !msgs.getMessages().isEmpty()) {
                    hasMessages = true;
                    Message latest = msgs.getMessages().get(0);
                    Map<String, Object> content = latest.getContent() != null ? latest.getContent() : Map.of();
                    lastContentType = String.valueOf(content.getOrDefault("contentType", ""));
                    lastPreview = String.valueOf(content.getOrDefault("previewText", ""));
                    lastDate = latest.getCreatedDate() != null ? latest.getCreatedDate() : "";
                    lastChannel = latest.getSourceChannel() != null ? latest.getSourceChannel() : "";
                    lastDirection = latest.getDirection() != null ? latest.getDirection() : "";
                }

                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("hasMessages", hasMessages);
                preview.put("lastPreview", lastPreview);
                preview.put("lastDate", lastDate);
                preview.put("lastChannel", lastChannel);
                preview.put("lastContentType", lastContentType);
                preview.put("lastDirection", lastDirection);
                cache.putMessagePreview(conv.getId(), preview);
            }

            String email = extractPrimaryEmail(contact);
            String phone = extractPrimaryPhone(contact);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("conversationId", conv.getId());
            row.put("contactId", contact.getId());
            row.put("contactName", contactName);
            row.put("initials", makeInitials(contactName));
            row.put("email", email);
            row.put("phone", phone);
            row.put("channels", conv.getChannels() != null ? conv.getChannels() : List.of());
            row.put("lastPreview", lastPreview);
            row.put("lastDate", lastDate);
            row.put("lastChannel", lastChannel);
            row.put("lastContentType", lastContentType);
            row.put("lastDirection", lastDirection);
            row.put("hasMessages", hasMessages);
            return BuildResult.ok(row);
        } catch (Exception e) {
            log.warn("Failed to build summary for contact {} ({}): {}",
                    contact.getId(), contactName, e.getMessage());
            return BuildResult.fail(contact.getId(), contactName, e.getMessage());
        }
    }

    private String extractContactId(Conversation conv) {
        if (conv.getParticipant() != null) {
            Object cid = conv.getParticipant().get("contactId");
            return cid != null ? String.valueOf(cid) : null;
        }
        return null;
    }

    private Map<String, Object> parseMessageFull(Message msg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", msg.getId());
        row.put("sequence", msg.getSequence());
        row.put("createdDate", msg.getCreatedDate());
        row.put("direction", msg.getDirection());
        row.put("directionLabel", "PARTICIPANT_TO_BUSINESS".equals(msg.getDirection()) ? "수신" : "발신");
        row.put("visibility", msg.getVisibility());
        row.put("sourceChannel", msg.getSourceChannel());
        row.put("sender", msg.getSender());
        row.put("silent", msg.isSilent());
        row.put("badges", msg.getBadges());

        Map<String, Object> content = msg.getContent() != null ? msg.getContent() : Map.of();
        String contentType = String.valueOf(content.getOrDefault("contentType", "UNKNOWN"));
        row.put("contentType", contentType);
        row.put("previewText", content.getOrDefault("previewText", ""));

        switch (contentType) {
            case "BASIC" -> {
                row.put("basicText", extractBasicText(content));
                row.put("basicItems", extractBasicItems(content));
            }
            case "MINIMAL" -> row.put("minimalData", content.getOrDefault("minimal", Map.of()));
            case "FORM" -> {
                Object form = content.getOrDefault("form", Map.of());
                row.put("formData", form);
                row.put("formFields", extractFormFields(form));
            }
            case "TEMPLATE" -> row.put("templateData", content.getOrDefault("template", Map.of()));
            case "SYSTEM" -> row.put("systemData", content.getOrDefault("system", Map.of()));
        }

        row.put("rawContent", content);
        return row;
    }

    @SuppressWarnings("unchecked")
    private String extractBasicText(Map<String, Object> content) {
        Object basic = content.get("basic");
        if (basic instanceof Map) {
            Object items = ((Map<?, ?>) basic).get("items");
            if (items instanceof List) {
                return ((List<?>) items).stream()
                        .filter(i -> i instanceof Map)
                        .map(i -> {
                            Object txt = ((Map<?, ?>) i).get("text");
                            return txt != null ? String.valueOf(txt) : "";
                        })
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining("\n"));
            }
        }
        return String.valueOf(content.getOrDefault("previewText", ""));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractBasicItems(Map<String, Object> content) {
        Object basic = content.get("basic");
        if (basic instanceof Map) {
            Object items = ((Map<?, ?>) basic).get("items");
            if (items instanceof List) {
                return ((List<?>) items).stream()
                        .filter(i -> i instanceof Map)
                        .map(i -> {
                            Map<?, ?> item = (Map<?, ?>) i;
                            Map<String, Object> parsed = new LinkedHashMap<>();
                            if (item.containsKey("text")) parsed.put("type", "text");
                            else if (item.containsKey("image")) parsed.put("type", "image");
                            else if (item.containsKey("file")) parsed.put("type", "file");
                            else parsed.put("type", "unknown");
                            parsed.putAll((Map<String, Object>) item);
                            return parsed;
                        })
                        .toList();
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractFormFields(Object form) {
        if (!(form instanceof Map)) return List.of();
        Object fields = ((Map<?, ?>) form).get("fields");
        if (!(fields instanceof List)) return List.of();
        return ((List<?>) fields).stream()
                .filter(f -> f instanceof Map)
                .map(f -> {
                    Map<?, ?> field = (Map<?, ?>) f;
                    Map<String, String> parsed = new LinkedHashMap<>();
                    Object nameVal = field.containsKey("fieldName") ? field.get("fieldName") : field.get("name");
                    Object valueVal = field.containsKey("fieldValue") ? field.get("fieldValue") : field.get("value");
                    parsed.put("name", nameVal != null ? String.valueOf(nameVal) : "");
                    parsed.put("value", valueVal != null ? String.valueOf(valueVal) : "");
                    return parsed;
                })
                .toList();
    }

    private String extractName(Contact contact) {
        if (contact.getInfo() != null && contact.getInfo().getName() != null) {
            String first = contact.getInfo().getName().getFirst();
            String last = contact.getInfo().getName().getLast();
            return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        }
        return contact.getId();
    }

    private String extractPrimaryEmail(Contact contact) {
        if (contact.getInfo() != null && contact.getInfo().getEmails() != null
                && contact.getInfo().getEmails().getItems() != null) {
            return contact.getInfo().getEmails().getItems().stream()
                    .filter(ContactInfo.Email::isPrimary)
                    .map(ContactInfo.Email::getEmail)
                    .findFirst()
                    .orElseGet(() -> contact.getInfo().getEmails().getItems().isEmpty()
                            ? "" : contact.getInfo().getEmails().getItems().get(0).getEmail());
        }
        return "";
    }

    private String extractPrimaryPhone(Contact contact) {
        if (contact.getInfo() != null && contact.getInfo().getPhones() != null
                && contact.getInfo().getPhones().getItems() != null) {
            return contact.getInfo().getPhones().getItems().stream()
                    .filter(ContactInfo.Phone::isPrimary)
                    .map(ContactInfo.Phone::getPhone)
                    .findFirst()
                    .orElseGet(() -> contact.getInfo().getPhones().getItems().isEmpty()
                            ? "" : contact.getInfo().getPhones().getItems().get(0).getPhone());
        }
        return "";
    }

    private String makeInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
