package com.wix.api.demo.controller;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.wix.api.WixClient;
import com.wix.api.common.CursorPagingRequest;
import com.wix.api.common.PagingRequest;
import com.wix.api.demo.WixClientProvider;
import com.wix.api.module.contacts.dto.Contact;
import com.wix.api.module.contacts.dto.ContactList;
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
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    /**
     * 대화 목록: 연락처 기반으로 대화를 발견하고, 최근 메시지 포함하여 반환.
     * visibility=BUSINESS → BASIC, MINIMAL, FORM, SYSTEM 등 모든 유형 포함.
     */
    /**
     * 전체 연락처를 페이지별로 순회하여 대화(메시지 있는 것)를 수집.
     * scanPages: 순회할 페이지 수 (각 100건), 최신 메시지 날짜 내림차순 정렬.
     */
    @GetMapping("/conversations")
    public Map<String, Object> listConversations(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @RequestParam(defaultValue = "5") int scanPages) {
        WixClient wix = provider.get(apiKey, siteId);

        List<Map<String, Object>> allConversations = new ArrayList<>();
        int pageSize = 100;
        int totalContacts = 0;

        for (int page = 0; page < scanPages; page++) {
            ContactList contactList = wix.contacts().listContacts(
                    PagingRequest.builder().limit(pageSize).offset(page * pageSize).build());
            if (contactList.getContacts() == null || contactList.getContacts().isEmpty()) break;

            if (page == 0 && contactList.getPagingMetadata() != null) {
                Integer t = contactList.getPagingMetadata().getTotal();
                if (t != null) totalContacts = t;
            }

            List<CompletableFuture<Map<String, Object>>> futures = contactList.getContacts().stream()
                    .map(contact -> CompletableFuture.supplyAsync(
                            () -> buildSummary(wix, contact), executor))
                    .toList();

            Set<String> seenIds = allConversations.stream()
                    .map(c -> (String) c.get("conversationId"))
                    .collect(Collectors.toSet());
            futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .filter(c -> seenIds.add((String) c.get("conversationId"))) // 중복 제거
                    .forEach(allConversations::add);

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

        return Map.of(
                "conversations", allConversations,
                "activeCount", activeCount,
                "totalScanned", Math.min(scanPages * pageSize, totalContacts),
                "totalContacts", totalContacts
        );
    }

    /**
     * 대화의 메시지 목록: 모든 유형 (BASIC, MINIMAL, FORM, TEMPLATE, SYSTEM) 포함.
     */
    /**
     * 대화 상세: conversation 메타 + contact 상세 + 최근 메시지를 통합 조회.
     */
    @GetMapping("/conversations/{conversationId}/detail")
    public Map<String, Object> conversationDetail(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @PathVariable String conversationId,
            @RequestParam(required = false) String contactId) {
        WixClient wix = provider.get(apiKey, siteId);

        // 1. Conversation 정보
        Conversation conv = wix.inbox().getConversation(conversationId);
        Map<String, Object> convInfo = new LinkedHashMap<>();
        if (conv != null) {
            convInfo.put("id", conv.getId());
            convInfo.put("channels", conv.getChannels());
            convInfo.put("participant", conv.getParticipant());
            convInfo.put("participantDisplayData", conv.getParticipantDisplayData());
            convInfo.put("businessDisplayData", conv.getBusinessDisplayData());
        }

        // 2. Contact 정보 (contactId가 전달된 경우)
        Map<String, Object> contactInfo = new LinkedHashMap<>();
        if (contactId != null && !contactId.isBlank()) {
            try {
                Contact contact = wix.contacts().getContact(contactId);
                if (contact != null) {
                    contactInfo.put("id", contact.getId());
                    contactInfo.put("revision", contact.getRevision());
                    contactInfo.put("createdDate", contact.getCreatedDate());
                    contactInfo.put("updatedDate", contact.getUpdatedDate());
                    contactInfo.put("info", contact.getInfo());
                    contactInfo.put("primaryInfo", contact.getPrimaryInfo());
                    contactInfo.put("source", contact.getSource());
                    contactInfo.put("lastActivity", contact.getLastActivity());
                    contactInfo.put("picture", contact.getPicture());
                }
            } catch (Exception e) {
                contactInfo.put("error", e.getMessage());
            }
        }

        // 3. 최근 메시지 5건 요약
        MessageList msgs = wix.inbox().listMessages(conversationId, "BUSINESS",
                CursorPagingRequest.builder().limit(5).build());
        int messageCount = 0;
        String lastDate = "";
        if (msgs != null && msgs.getMessages() != null) {
            messageCount = msgs.getMessages().size();
            if (!msgs.getMessages().isEmpty()) {
                lastDate = msgs.getMessages().get(0).getCreatedDate();
            }
            if (msgs.getPagingMetadata() != null && msgs.getPagingMetadata().getCursors() != null
                    && msgs.getPagingMetadata().getCursors().getNext() != null) {
                messageCount = -1; // 5건 초과
            }
        }

        return Map.of(
                "conversation", convInfo,
                "contact", contactInfo,
                "messageCount", messageCount == -1 ? "5+" : String.valueOf(messageCount),
                "lastMessageDate", lastDate
        );
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Map<String, Object> listMessages(
            @RequestHeader(value = "X-Wix-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Wix-Site-Id", required = false) String siteId,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "BUSINESS") String visibility,
            @RequestParam(required = false) String cursor) {
        WixClient wix = provider.get(apiKey, siteId);
        MessageList ml = wix.inbox().listMessages(conversationId, visibility,
                CursorPagingRequest.builder().limit(limit).cursor(cursor).build());

        List<Map<String, Object>> messages = new ArrayList<>();
        if (ml != null && ml.getMessages() != null) {
            for (Message msg : ml.getMessages()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", msg.getId());
                row.put("createdDate", msg.getCreatedDate());
                row.put("direction", msg.getDirection());
                row.put("visibility", msg.getVisibility());
                row.put("sourceChannel", msg.getSourceChannel());
                row.put("sender", msg.getSender());

                // content 파싱 — 유형별 분류
                Map<String, Object> content = msg.getContent() != null ? msg.getContent() : Map.of();
                String contentType = String.valueOf(content.getOrDefault("contentType", "UNKNOWN"));
                row.put("contentType", contentType);
                row.put("previewText", content.getOrDefault("previewText", ""));

                switch (contentType) {
                    case "BASIC" -> row.put("basicText", extractBasicText(content));
                    case "MINIMAL" -> {
                        row.put("minimalText", content.getOrDefault("minimal", Map.of()));
                    }
                    case "FORM" -> row.put("formData", content.getOrDefault("form", Map.of()));
                    case "TEMPLATE" -> row.put("templateData", content.getOrDefault("template", Map.of()));
                }

                row.put("silent", false);
                row.put("badges", msg.getBadges());
                messages.add(row);
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

    private Map<String, Object> buildSummary(WixClient wix, Contact contact) {
        try {
            Conversation conv = wix.inbox().getOrCreateConversation(contact.getId());
            if (conv == null || conv.getId() == null) return null;

            MessageList msgs = wix.inbox().listMessages(
                    conv.getId(), "BUSINESS",
                    CursorPagingRequest.builder().limit(1).build());

            String lastPreview = "", lastDate = "", lastChannel = "", lastContentType = "";
            boolean hasMessages = false;

            if (msgs != null && msgs.getMessages() != null && !msgs.getMessages().isEmpty()) {
                hasMessages = true;
                Message latest = msgs.getMessages().get(0);
                Map<String, Object> content = latest.getContent() != null ? latest.getContent() : Map.of();
                lastContentType = String.valueOf(content.getOrDefault("contentType", ""));
                lastPreview = String.valueOf(content.getOrDefault("previewText", ""));
                lastDate = latest.getCreatedDate() != null ? latest.getCreatedDate() : "";
                lastChannel = latest.getSourceChannel() != null ? latest.getSourceChannel() : "";
            }

            String name = extractName(contact);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("conversationId", conv.getId());
            row.put("contactId", contact.getId());
            row.put("contactName", name);
            row.put("initials", makeInitials(name));
            row.put("channels", conv.getChannels() != null ? conv.getChannels() : List.of());
            row.put("lastPreview", lastPreview);
            row.put("lastDate", lastDate);
            row.put("lastChannel", lastChannel);
            row.put("lastContentType", lastContentType);
            row.put("hasMessages", hasMessages);
            return row;
        } catch (Exception e) {
            return null;
        }
    }

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
                        .collect(Collectors.joining("\n"));
            }
        }
        return String.valueOf(content.getOrDefault("previewText", ""));
    }

    private String extractName(Contact contact) {
        if (contact.getInfo() != null && contact.getInfo().getName() != null) {
            String first = contact.getInfo().getName().getFirst();
            String last = contact.getInfo().getName().getLast();
            return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        }
        return contact.getId();
    }

    private String makeInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
