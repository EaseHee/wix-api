package com.wix.api.demo.controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wix.api.module.contacts.dto.Contact;
import com.wix.api.module.inbox.dto.Conversation;

public class InboxCache {

    private record CacheEntry<T>(T value, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    private final ConcurrentHashMap<String, CacheEntry<Contact>> contactCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<Conversation>> conversationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<Map<String, Object>>> messagePreviewCache = new ConcurrentHashMap<>();

    private static final long CONTACT_TTL_MS = 5 * 60 * 1000;       // 5분
    private static final long CONVERSATION_TTL_MS = 5 * 60 * 1000;  // 5분
    private static final long MESSAGE_PREVIEW_TTL_MS = 60 * 1000;    // 1분

    public Contact getContact(String contactId) {
        CacheEntry<Contact> entry = contactCache.get(contactId);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }
        if (entry != null) contactCache.remove(contactId);
        return null;
    }

    public void putContact(String contactId, Contact contact) {
        contactCache.put(contactId, new CacheEntry<>(contact, System.currentTimeMillis() + CONTACT_TTL_MS));
    }

    public Map<String, Object> getMessagePreview(String conversationId) {
        CacheEntry<Map<String, Object>> entry = messagePreviewCache.get(conversationId);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }
        if (entry != null) messagePreviewCache.remove(conversationId);
        return null;
    }

    public void putMessagePreview(String conversationId, Map<String, Object> preview) {
        messagePreviewCache.put(conversationId, new CacheEntry<>(preview, System.currentTimeMillis() + MESSAGE_PREVIEW_TTL_MS));
    }

    public Conversation getConversation(String contactId) {
        CacheEntry<Conversation> entry = conversationCache.get(contactId);
        if (entry != null && !entry.isExpired()) {
            return entry.value();
        }
        if (entry != null) conversationCache.remove(contactId);
        return null;
    }

    public void putConversation(String contactId, Conversation conv) {
        conversationCache.put(contactId, new CacheEntry<>(conv, System.currentTimeMillis() + CONVERSATION_TTL_MS));
    }

    public void invalidateConversation(String conversationId) {
        messagePreviewCache.remove(conversationId);
    }
}
