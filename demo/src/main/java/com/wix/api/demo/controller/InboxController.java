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
    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private record MessagePage(List<Message> messages, String nextCursor) {
        static final MessagePage EMPTY = new MessagePage(List.of(), null);
    }

    /**
     * 대화 목록: 연락처 기반 조회 + 캐시 적용. page/pageSize 기반 페이지네이션.
     * 한 번 호출당 pageSize 만큼의 contact만 enrichment 처리 → 첫 화면 응답을 빠르게.
     * 캐시 히트 시에는 API 0회로 즉시 응답.
     */
    @GetMapping("/conversations")
    public Map<String, Object> listConversations(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        WixClient wix = provider.get(apiKey, siteId);

        int safeLimit = Math.min(Math.max(pageSize, 1), 100);
        int offset = Math.max(page, 0) * safeLimit;

        ContactList contactList = wix.contacts().queryContacts(
                QueryContactsRequest.builder()
                        .sort(List.of(Map.of("fieldName", "createdDate", "order", "DESC")))
                        .paging(PagingRequest.builder().limit(safeLimit).offset(offset).build())
                        .build());

        List<Contact> contacts = contactList != null && contactList.getContacts() != null
                ? contactList.getContacts() : List.of();

        int totalContacts = 0;
        boolean hasMore = false;
        if (contactList != null && contactList.getPagingMetadata() != null) {
            Integer t = contactList.getPagingMetadata().getTotal();
            if (t != null) totalContacts = t;
            hasMore = contactList.getPagingMetadata().isHasNext();
        }

        List<CompletableFuture<BuildResult>> futures = contacts.stream()
                .map(contact -> CompletableFuture.supplyAsync(
                        () -> enrichFromContact(wix, contact), executor))
                .toList();

        List<Map<String, Object>> conversations = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (CompletableFuture<BuildResult> future : futures) {
            BuildResult result = future.join();
            if (result.summary != null) {
                if (seenIds.add((String) result.summary.get("conversationId"))) {
                    conversations.add(result.summary);
                }
            } else if (result.error != null) {
                errors.add(result.error);
            }
        }

        // 메시지 있는 대화 우선, 날짜 내림차순 (현재 페이지 내 정렬)
        conversations.sort((a, b) -> {
            boolean aHas = (boolean) a.get("hasMessages");
            boolean bHas = (boolean) b.get("hasMessages");
            if (aHas != bHas) return aHas ? -1 : 1;
            return ((String) b.getOrDefault("lastDate", "")).compareTo((String) a.getOrDefault("lastDate", ""));
        });

        long activeCount = conversations.stream().filter(c -> (boolean) c.get("hasMessages")).count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversations", conversations);
        response.put("activeCount", activeCount);
        response.put("page", Math.max(page, 0));
        response.put("pageSize", safeLimit);
        response.put("offset", offset);
        response.put("returned", conversations.size());
        response.put("totalContacts", totalContacts);
        response.put("hasMore", hasMore);
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
            @RequestParam(defaultValue = "50") int messageLimit,
            @RequestParam(defaultValue = "false") boolean includeRaw) {
        WixClient wix = provider.get(apiKey, siteId);

        // 1. Conversation 정보 (contactId 추출을 위해 선행)
        Conversation conv = wix.inbox().getConversation(conversationId);
        Map<String, Object> convInfo = new LinkedHashMap<>();
        if (conv != null) {
            convInfo.put("id", conv.getId());
            convInfo.put("channels", conv.getChannels());
            convInfo.put("channelLabels", channelLabels(conv.getChannels()));
            convInfo.put("createdDate", conv.getCreatedDate());
            convInfo.put("participant", conv.getParticipant());
            convInfo.put("participantContactId", extractContactId(conv));
            convInfo.put("participantType", resolveParticipantType(conv));
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

        // BUSINESS_AND_PARTICIPANT(양쪽 가시) + BUSINESS(비즈니스 전용: 폼 알림/시스템 이벤트 등)을
        // 병렬로 모두 가져온 뒤 id 기준 dedupe + 최신순(DESC) 정렬.
        // 한쪽만 보면 Form/Notification/Unknown 타입 메시지가 채팅 내역에서 누락된다.
        Map<String, String> fetchErrors = new ConcurrentHashMap<>();
        CompletableFuture<List<Message>> visBothFuture = CompletableFuture.supplyAsync(
                () -> fetchAllMessages(wix, conversationId, "BUSINESS_AND_PARTICIPANT", messageLimit, fetchErrors), executor);
        CompletableFuture<List<Message>> visBusinessFuture = CompletableFuture.supplyAsync(
                () -> fetchAllMessages(wix, conversationId, "BUSINESS", messageLimit, fetchErrors), executor);

        CompletableFuture<List<Map<String, Object>>> messagesFuture = visBothFuture.thenCombine(
                visBusinessFuture, (a, b) -> {
                    LinkedHashMap<String, Message> dedup = new LinkedHashMap<>();
                    for (Message m : a) if (m.getId() != null) dedup.putIfAbsent(m.getId(), m);
                    for (Message m : b) if (m.getId() != null) dedup.putIfAbsent(m.getId(), m);
                    return dedup.values().stream()
                            .sorted(Comparator.comparing(
                                    (Message m) -> m.getCreatedDate() != null ? m.getCreatedDate() : "",
                                    Comparator.naturalOrder()).reversed())
                            .limit(messageLimit)
                            .map(m -> parseMessageFull(m, includeRaw))
                            .toList();
                });

        Map<String, Object> contactInfo = contactFuture.join();
        List<Map<String, Object>> allMessages = messagesFuture.join();

        // contentType 분포 집계 (진단용 — Form/Unknown 등 어떤 타입이 들어왔는지 가시화)
        Map<String, Long> typeBreakdown = allMessages.stream()
                .collect(Collectors.groupingBy(
                        m -> String.valueOf(m.getOrDefault("contentType", "UNKNOWN")),
                        Collectors.counting()));

        // 메시지 스레드에서 평탄화 메타 산출
        String lastActivityDate = "";
        for (Map<String, Object> m : allMessages) {
            String d = (String) m.getOrDefault("createdDate", "");
            if (d != null && d.compareTo(lastActivityDate) > 0) lastActivityDate = d;
        }
        if (!convInfo.isEmpty()) {
            convInfo.put("lastActivityDate", lastActivityDate);
            convInfo.put("messageCount", allMessages.size());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversation", convInfo);
        result.put("contact", contactInfo);
        result.put("messages", allMessages);
        result.put("messageCount", allMessages.size());
        result.put("typeBreakdown", typeBreakdown);
        result.put("hasMoreMessages", allMessages.size() >= messageLimit);
        if (!fetchErrors.isEmpty()) result.put("fetchErrors", fetchErrors);
        return result;
    }

    /**
     * 대화의 메시지 목록.
     * visibility=ALL 이면 BUSINESS + BUSINESS_AND_PARTICIPANT 모두 조회하여 머지.
     * Form 알림이나 시스템/Unknown 타입 메시지는 BUSINESS 전용으로 도착하므로 ALL 사용 시에만 보인다.
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public Map<String, Object> listMessages(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "ALL") String visibility,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) String cursor) {
        WixClient wix = provider.get(apiKey, siteId);

        if ("ALL".equalsIgnoreCase(visibility)) {
            // 단일 페이지 페이지네이션: 두 visibility를 limit 개씩만 호출, dedupe 후 limit 반환.
            // 다음 페이지는 양쪽 cursor를 base64 JSON으로 합쳐 nextCursor에 담음.
            Map<String, String> cursors = decodeCombinedCursor(cursor);
            boolean firstPage = (cursor == null || cursor.isBlank());
            String bothCursor = cursors.get("both");
            String bizCursor = cursors.get("biz");
            boolean callBoth = firstPage || (bothCursor != null && !bothCursor.isBlank());
            boolean callBiz = firstPage || (bizCursor != null && !bizCursor.isBlank());

            Map<String, String> errors = new ConcurrentHashMap<>();
            CompletableFuture<MessagePage> bothF = callBoth
                    ? CompletableFuture.supplyAsync(() -> fetchOnePage(
                            wix, conversationId, "BUSINESS_AND_PARTICIPANT", sortOrder, limit, bothCursor, errors), executor)
                    : CompletableFuture.completedFuture(MessagePage.EMPTY);
            CompletableFuture<MessagePage> bizF = callBiz
                    ? CompletableFuture.supplyAsync(() -> fetchOnePage(
                            wix, conversationId, "BUSINESS", sortOrder, limit, bizCursor, errors), executor)
                    : CompletableFuture.completedFuture(MessagePage.EMPTY);

            MessagePage bothPage = bothF.join();
            MessagePage bizPage = bizF.join();

            LinkedHashMap<String, Message> dedup = new LinkedHashMap<>();
            for (Message m : bothPage.messages) if (m.getId() != null) dedup.putIfAbsent(m.getId(), m);
            for (Message m : bizPage.messages) if (m.getId() != null) dedup.putIfAbsent(m.getId(), m);

            boolean asc = "ASC".equalsIgnoreCase(sortOrder);
            Comparator<Message> cmp = Comparator.comparing(
                    (Message m) -> m.getCreatedDate() != null ? m.getCreatedDate() : "",
                    Comparator.naturalOrder());
            List<Map<String, Object>> messages = dedup.values().stream()
                    .sorted(asc ? cmp : cmp.reversed())
                    .limit(limit)
                    .map(this::parseMessageFull)
                    .toList();
            Map<String, Long> typeBreakdown = messages.stream()
                    .collect(Collectors.groupingBy(
                            m -> String.valueOf(m.getOrDefault("contentType", "UNKNOWN")),
                            Collectors.counting()));

            String nextCombined = encodeCombinedCursor(bothPage.nextCursor, bizPage.nextCursor);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("messages", messages);
            resp.put("total", messages.size());
            resp.put("typeBreakdown", typeBreakdown);
            resp.put("nextCursor", nextCombined);
            if (!errors.isEmpty()) resp.put("fetchErrors", errors);
            return resp;
        }

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

    private List<Message> fetchAllMessages(WixClient wix, String conversationId,
                                            String visibility, int limit) {
        return fetchAllMessages(wix, conversationId, visibility, limit, null);
    }

    /**
     * 한 페이지만 호출. cursor가 null/blank면 처음부터, 아니면 해당 cursor부터.
     * 실패해도 errors에 기록하고 빈 페이지 반환.
     */
    private MessagePage fetchOnePage(WixClient wix, String conversationId,
                                      String visibility, String sortOrder,
                                      int limit, String cursor,
                                      Map<String, String> errors) {
        try {
            MessageList ml = wix.inbox().listMessages(conversationId, visibility, sortOrder,
                    CursorPagingRequest.builder().limit(limit)
                            .cursor(cursor != null && !cursor.isBlank() ? cursor : null).build());
            if (ml == null) return MessagePage.EMPTY;
            String next = null;
            if (ml.getPagingMetadata() != null && ml.getPagingMetadata().getCursors() != null) {
                next = ml.getPagingMetadata().getCursors().getNext();
            }
            return new MessagePage(ml.getMessages() != null ? ml.getMessages() : List.of(), next);
        } catch (Exception e) {
            log.warn("fetchOnePage failed conv={} vis={}: {}", conversationId, visibility, e.getMessage());
            if (errors != null) errors.put(visibility, e.getMessage());
            return MessagePage.EMPTY;
        }
    }

    /**
     * 두 visibility cursor를 합쳐 base64(JSON) 단일 토큰으로 인코딩.
     * 양쪽 모두 null/blank면 빈 문자열(다음 페이지 없음).
     */
    private String encodeCombinedCursor(String bothCursor, String bizCursor) {
        Map<String, String> m = new LinkedHashMap<>();
        if (bothCursor != null && !bothCursor.isBlank()) m.put("both", bothCursor);
        if (bizCursor != null && !bizCursor.isBlank()) m.put("biz", bizCursor);
        if (m.isEmpty()) return "";
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(jsonMapper.writeValueAsBytes(m));
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> decodeCombinedCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return Map.of();
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            return jsonMapper.readValue(bytes, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 지정한 visibility의 모든 메시지를 limit 만큼 cursor 페이지네이션으로 수집.
     * 최신순(DESC)으로 가져와 messageLimit 도달 시점에 끊어도 가장 최근 메시지를 포함하도록 한다.
     * errors가 주어지면 한 visibility 호출이 실패하더라도 다른 쪽 결과는 살리고 사유만 기록.
     */
    private List<Message> fetchAllMessages(WixClient wix, String conversationId,
                                            String visibility, int limit,
                                            Map<String, String> errors) {
        List<Message> out = new ArrayList<>();
        String cursor = null;
        int maxPages = 50;
        int page = 0;
        while (out.size() < limit && page < maxPages) {
            int batchSize = Math.min(50, limit - out.size());
            MessageList ml;
            try {
                ml = wix.inbox().listMessages(conversationId, visibility, "DESC",
                        CursorPagingRequest.builder().limit(batchSize).cursor(cursor).build());
            } catch (Exception e) {
                log.warn("listMessages failed conv={} vis={}: {}", conversationId, visibility, e.getMessage());
                if (errors != null) errors.put(visibility, e.getMessage());
                break;
            }
            if (ml == null || ml.getMessages() == null || ml.getMessages().isEmpty()) break;
            out.addAll(ml.getMessages());
            if (ml.getPagingMetadata() != null && ml.getPagingMetadata().getCursors() != null
                    && ml.getPagingMetadata().getCursors().getNext() != null) {
                cursor = ml.getPagingMetadata().getCursors().getNext();
            } else {
                break;
            }
            page++;
        }
        return out;
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
            String lastSenderName = "", lastSenderType = "";
            int attachmentCount = 0;
            boolean hasMessages = false, hasAttachment = false, unreadHint = false;
            List<Map<String, Object>> formSummary = List.of();
            List<String> badges = List.of();

            Map<String, Object> cachedPreview = cache.getMessagePreview(conv.getId());
            if (cachedPreview != null) {
                hasMessages = (boolean) cachedPreview.getOrDefault("hasMessages", false);
                lastPreview = (String) cachedPreview.getOrDefault("lastPreview", "");
                lastDate = (String) cachedPreview.getOrDefault("lastDate", "");
                lastChannel = (String) cachedPreview.getOrDefault("lastChannel", "");
                lastContentType = (String) cachedPreview.getOrDefault("lastContentType", "");
                lastDirection = (String) cachedPreview.getOrDefault("lastDirection", "");
                lastSenderName = (String) cachedPreview.getOrDefault("lastSenderName", "");
                lastSenderType = (String) cachedPreview.getOrDefault("lastSenderType", "");
                attachmentCount = (int) cachedPreview.getOrDefault("attachmentCount", 0);
                hasAttachment = (boolean) cachedPreview.getOrDefault("hasAttachment", false);
                unreadHint = (boolean) cachedPreview.getOrDefault("unreadHint", false);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fs = (List<Map<String, Object>>) cachedPreview.getOrDefault("formSummary", List.of());
                formSummary = fs;
                @SuppressWarnings("unchecked")
                List<String> bs = (List<String>) cachedPreview.getOrDefault("badges", List.of());
                badges = bs;
            } else {
                MessageList msgs = wix.inbox().listMessages(
                        conv.getId(), "BUSINESS_AND_PARTICIPANT",
                        CursorPagingRequest.builder().limit(1).build());

                if (msgs != null && msgs.getMessages() != null && !msgs.getMessages().isEmpty()) {
                    hasMessages = true;
                    Message latest = msgs.getMessages().get(0);
                    Map<String, Object> content = latest.getContent() != null ? latest.getContent() : Map.of();
                    lastContentType = String.valueOf(content.getOrDefault("contentType", ""));
                    String pv = String.valueOf(content.getOrDefault("previewText", ""));
                    if ("null".equals(pv)) pv = "";
                    if (pv.isBlank()) {
                        // contentType별 fallback (목록에서 빈 카드 방지)
                        pv = switch (lastContentType) {
                            case "BASIC" -> extractBasicText(content);
                            case "FORM" -> {
                                Object f = content.getOrDefault("form", Map.of());
                                if (f instanceof Map<?,?> fm) {
                                    Object t = fm.get("title");
                                    yield t != null ? String.valueOf(t) : "Form submission";
                                }
                                yield "Form submission";
                            }
                            case "MINIMAL" -> extractMinimalText(content.getOrDefault("minimal", Map.of()));
                            case "TEMPLATE" -> extractTemplateText(content.getOrDefault("template", Map.of()));
                            case "SYSTEM" -> extractSystemText(content.getOrDefault("system", Map.of()));
                            default -> "[" + lastContentType + "]";
                        };
                    }
                    lastPreview = pv;
                    lastDate = latest.getCreatedDate() != null ? latest.getCreatedDate() : "";
                    lastChannel = latest.getSourceChannel() != null ? latest.getSourceChannel() : "";
                    lastDirection = latest.getDirection() != null ? latest.getDirection() : "";

                    Map<String, String> senderInfo = extractSenderInfo(latest.getSender());
                    lastSenderName = senderInfo.getOrDefault("senderName", "");
                    lastSenderType = senderInfo.getOrDefault("senderType", "");

                    List<Map<String, Object>> atts = extractAttachments(content);
                    attachmentCount = atts.size();
                    hasAttachment = attachmentCount > 0;

                    if ("FORM".equals(lastContentType)) {
                        formSummary = extractFormSummary(content.getOrDefault("form", Map.of()));
                    }

                    badges = extractBadges(latest.getBadges());
                    unreadHint = "PARTICIPANT_TO_BUSINESS".equals(lastDirection)
                            && badges.stream().noneMatch(b -> b.toUpperCase().contains("ANSWER"));
                }

                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("hasMessages", hasMessages);
                preview.put("lastPreview", lastPreview);
                preview.put("lastDate", lastDate);
                preview.put("lastChannel", lastChannel);
                preview.put("lastContentType", lastContentType);
                preview.put("lastDirection", lastDirection);
                preview.put("lastSenderName", lastSenderName);
                preview.put("lastSenderType", lastSenderType);
                preview.put("attachmentCount", attachmentCount);
                preview.put("hasAttachment", hasAttachment);
                preview.put("unreadHint", unreadHint);
                preview.put("formSummary", formSummary);
                preview.put("badges", badges);
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
            row.put("lastChannelLabel", channelLabel(lastChannel));
            row.put("lastContentType", lastContentType);
            row.put("lastDirection", lastDirection);
            row.put("lastSenderName", lastSenderName);
            row.put("lastSenderType", lastSenderType);
            row.put("attachmentCount", attachmentCount);
            row.put("hasAttachment", hasAttachment);
            row.put("formSummary", formSummary);
            row.put("badges", badges);
            row.put("unreadHint", unreadHint);
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
        return parseMessageFull(msg, true);
    }

    private Map<String, Object> parseMessageFull(Message msg, boolean includeRaw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", msg.getId());
        row.put("sequence", msg.getSequence());
        row.put("createdDate", msg.getCreatedDate());
        row.put("direction", msg.getDirection());
        row.put("directionLabel", "PARTICIPANT_TO_BUSINESS".equals(msg.getDirection()) ? "수신" : "발신");
        row.put("visibility", msg.getVisibility());
        row.put("sourceChannel", msg.getSourceChannel());
        row.put("sourceChannelLabel", channelLabel(msg.getSourceChannel()));
        row.put("sender", msg.getSender());

        Map<String, String> senderInfo = extractSenderInfo(msg.getSender());
        row.put("senderName", senderInfo.getOrDefault("senderName", ""));
        row.put("senderType", senderInfo.getOrDefault("senderType", ""));
        row.put("senderId", senderInfo.getOrDefault("senderId", ""));

        row.put("silent", msg.isSilent());
        row.put("badges", msg.getBadges());
        row.put("badgeTypes", extractBadges(msg.getBadges()));

        Map<String, Object> content = msg.getContent() != null ? msg.getContent() : Map.of();
        String contentType = String.valueOf(content.getOrDefault("contentType", "UNKNOWN"));
        row.put("contentType", contentType);
        String previewText = String.valueOf(content.getOrDefault("previewText", ""));
        if ("null".equals(previewText)) previewText = "";
        row.put("previewText", previewText);

        List<Map<String, Object>> attachments = extractAttachments(content);
        row.put("attachments", attachments);
        row.put("attachmentCount", attachments.size());
        row.put("hasAttachment", !attachments.isEmpty());

        String displayText = "";
        switch (contentType) {
            case "BASIC" -> {
                String basicText = extractBasicText(content);
                row.put("basicText", basicText);
                row.put("basicItems", extractBasicItems(content));
                displayText = !basicText.isBlank() ? basicText : previewText;
            }
            case "MINIMAL" -> {
                Object minimal = content.getOrDefault("minimal", Map.of());
                row.put("minimalData", minimal);
                displayText = !previewText.isBlank() ? previewText : extractMinimalText(minimal);
            }
            case "FORM" -> {
                Object form = content.getOrDefault("form", Map.of());
                row.put("formData", form);
                row.put("formFields", extractFormFields(form));
                row.put("formSummary", extractFormSummary(form));
                String formTitle = "";
                if (form instanceof Map<?,?> fm) {
                    Object t = fm.get("title");
                    if (t != null) formTitle = String.valueOf(t);
                }
                displayText = !previewText.isBlank() ? previewText : (formTitle.isBlank() ? "Form submission" : formTitle);
            }
            case "TEMPLATE" -> {
                Object template = content.getOrDefault("template", Map.of());
                row.put("templateData", template);
                displayText = !previewText.isBlank() ? previewText : extractTemplateText(template);
            }
            case "SYSTEM" -> {
                Object system = content.getOrDefault("system", Map.of());
                row.put("systemData", system);
                displayText = !previewText.isBlank() ? previewText : extractSystemText(system);
            }
            default -> {
                // UNKNOWN, UNKNOWN_MESSAGE_TYPE, 그 외 새 타입 — content 맵에서 의미 있는 텍스트 탐색
                String fallback = extractUnknownText(content);
                row.put("unknownData", content);
                displayText = !previewText.isBlank() ? previewText
                        : (!fallback.isBlank() ? fallback : "[" + contentType + "]");
            }
        }
        if (displayText == null || displayText.isBlank()) {
            displayText = !attachments.isEmpty()
                    ? "[" + attachments.size() + " attachment" + (attachments.size() > 1 ? "s" : "") + "]"
                    : "[" + contentType + "]";
        }
        row.put("displayText", displayText);

        if (includeRaw) {
            row.put("rawContent", content);
        }
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

    private String resolveParticipantType(Conversation conv) {
        if (conv.getParticipant() == null) return "UNKNOWN";
        if (conv.getParticipant().containsKey("contactId")) return "CONTACT";
        if (conv.getParticipant().containsKey("visitorId")) return "VISITOR";
        if (conv.getParticipant().containsKey("memberId")) return "MEMBER";
        return "UNKNOWN";
    }

    private Map<String, String> extractSenderInfo(Map<String, Object> sender) {
        Map<String, String> info = new LinkedHashMap<>();
        if (sender == null) return info;
        String type = "UNKNOWN", id = "", name = "";
        if (sender.containsKey("contactId")) {
            type = "CONTACT"; id = String.valueOf(sender.get("contactId"));
        } else if (sender.containsKey("memberId")) {
            type = "MEMBER"; id = String.valueOf(sender.get("memberId"));
        } else if (sender.containsKey("visitorId")) {
            type = "VISITOR"; id = String.valueOf(sender.get("visitorId"));
        } else if (sender.containsKey("userId")) {
            type = "BUSINESS"; id = String.valueOf(sender.get("userId"));
        } else if (sender.containsKey("appId")) {
            type = "APP"; id = String.valueOf(sender.get("appId"));
        }
        Object n = sender.get("displayName");
        if (n == null) n = sender.get("name");
        if (n != null) name = String.valueOf(n);
        info.put("senderType", type);
        info.put("senderId", id);
        info.put("senderName", name);
        return info;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAttachments(Map<String, Object> content) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (content == null) return result;

        // BASIC: basic.items 중 image/file 항목
        Object basic = content.get("basic");
        if (basic instanceof Map) {
            Object items = ((Map<?, ?>) basic).get("items");
            if (items instanceof List) {
                for (Object i : (List<?>) items) {
                    if (!(i instanceof Map)) continue;
                    Map<String, Object> item = (Map<String, Object>) i;
                    if (item.containsKey("image")) {
                        result.add(flattenAttachment("image", (Map<String, Object>) item.get("image")));
                    } else if (item.containsKey("file")) {
                        result.add(flattenAttachment("file", (Map<String, Object>) item.get("file")));
                    } else if (item.containsKey("video")) {
                        result.add(flattenAttachment("video", (Map<String, Object>) item.get("video")));
                    }
                }
            }
        }

        // FORM: form.attachments (Wix 스펙상 존재할 경우 평탄화)
        Object form = content.get("form");
        if (form instanceof Map) {
            Object atts = ((Map<?, ?>) form).get("attachments");
            if (atts instanceof List) {
                for (Object a : (List<?>) atts) {
                    if (a instanceof Map) {
                        result.add(flattenAttachment("file", (Map<String, Object>) a));
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Object> flattenAttachment(String type, Map<String, Object> raw) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type);
        if (raw == null) return a;
        Object url = raw.get("url");
        if (url == null) url = raw.get("src");
        a.put("url", url != null ? String.valueOf(url) : "");
        Object name = raw.get("name");
        if (name == null) name = raw.get("fileName");
        a.put("name", name != null ? String.valueOf(name) : "");
        Object size = raw.get("size");
        if (size == null) size = raw.get("fileSize");
        if (size != null) a.put("size", size);
        Object mime = raw.get("mimeType");
        if (mime == null) mime = raw.get("contentType");
        if (mime != null) a.put("mimeType", String.valueOf(mime));
        Object width = raw.get("width");
        if (width != null) a.put("width", width);
        Object height = raw.get("height");
        if (height != null) a.put("height", height);
        return a;
    }

    private static final java.util.regex.Pattern FORM_KEY_FIELD =
            java.util.regex.Pattern.compile("(?i)name|email|mail|phone|tel|mobile|이름|연락처|전화|이메일|문의|inquiry|subject|제목");

    private List<Map<String, Object>> extractFormSummary(Object form) {
        List<Map<String, String>> fields = extractFormFields(form);
        if (fields.isEmpty()) return List.of();
        List<Map<String, Object>> picked = new ArrayList<>();
        for (Map<String, String> f : fields) {
            String name = f.getOrDefault("name", "");
            String value = f.getOrDefault("value", "");
            if (name.isBlank() || value.isBlank()) continue;
            if (FORM_KEY_FIELD.matcher(name).find()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("label", name);
                entry.put("value", value);
                picked.add(entry);
                if (picked.size() >= 3) break;
            }
        }
        // 핵심 필드 매칭이 없으면 앞쪽 2개로 fallback
        if (picked.isEmpty()) {
            for (Map<String, String> f : fields) {
                String name = f.getOrDefault("name", "");
                String value = f.getOrDefault("value", "");
                if (value.isBlank()) continue;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("label", name);
                entry.put("value", value);
                picked.add(entry);
                if (picked.size() >= 2) break;
            }
        }
        return picked;
    }

    private String extractMinimalText(Object minimal) {
        if (!(minimal instanceof Map)) return "";
        Map<?, ?> m = (Map<?, ?>) minimal;
        for (String k : new String[]{"text", "body", "content", "message"}) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "";
    }

    private String extractTemplateText(Object template) {
        if (!(template instanceof Map)) return "";
        Map<?, ?> m = (Map<?, ?>) template;
        Object name = m.get("name");
        if (name != null) return "Template: " + name;
        Object id = m.get("templateId");
        if (id != null) return "Template: " + id;
        return "Template message";
    }

    /**
     * 알 수 없는 contentType의 content 맵에서 흔히 쓰이는 텍스트 키를 광범위하게 탐색.
     * 1단계로 표준 키(text/title/message...) 확인 후, 2단계로 contentType 외 모든 nested map을 재귀 탐색.
     */
    private String extractUnknownText(Map<String, Object> content) {
        if (content == null || content.isEmpty()) return "";
        for (String k : new String[]{"text", "title", "subject", "message", "body", "description", "name"}) {
            Object v = content.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        for (Map.Entry<String, Object> e : content.entrySet()) {
            if ("contentType".equals(e.getKey()) || "previewText".equals(e.getKey())) continue;
            if (e.getValue() instanceof Map<?, ?> sub) {
                for (String k : new String[]{"text", "title", "subject", "message", "body", "description", "name"}) {
                    Object v = sub.get(k);
                    if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
                }
            }
        }
        return "";
    }

    private String extractSystemText(Object system) {
        if (!(system instanceof Map)) return "";
        Map<?, ?> m = (Map<?, ?>) system;
        for (String k : new String[]{"text", "message", "description", "eventType", "type"}) {
            Object v = m.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return String.valueOf(v);
        }
        return "System event";
    }

    private List<String> extractBadges(List<Map<String, Object>> badges) {
        if (badges == null || badges.isEmpty()) return List.of();
        List<String> types = new ArrayList<>();
        for (Map<String, Object> b : badges) {
            if (b == null) continue;
            Object t = b.get("type");
            if (t == null) t = b.get("badgeType");
            if (t == null) t = b.get("name");
            if (t != null) types.add(String.valueOf(t));
        }
        return types;
    }

    private String channelLabel(String channel) {
        if (channel == null || channel.isBlank()) return "";
        return switch (channel) {
            case "CHAT" -> "채팅";
            case "EMAIL" -> "이메일";
            case "SMS" -> "SMS";
            case "FACEBOOK", "FB_MESSENGER" -> "Facebook";
            case "INSTAGRAM" -> "Instagram";
            case "WHATSAPP" -> "WhatsApp";
            case "FORM", "WIX_FORMS" -> "폼";
            case "PHONE_CALL" -> "전화";
            default -> channel;
        };
    }

    private List<String> channelLabels(List<String> channels) {
        if (channels == null) return List.of();
        return channels.stream().map(this::channelLabel).toList();
    }

    private String makeInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
