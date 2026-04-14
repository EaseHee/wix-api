package com.wix.api.module.contacts;

import java.util.List;
import java.util.Map;

import com.wix.api.common.PagingRequest;
import com.wix.api.http.RetryHandler;
import com.wix.api.module.AbstractWixService;
import com.wix.api.module.contacts.dto.*;
import org.springframework.web.client.RestClient;

public class ContactsService extends AbstractWixService {

    public ContactsService(RestClient restClient, RetryHandler retryHandler) {
        super(restClient, retryHandler);
    }

    public Contact getContact(String contactId) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.get()
                    .uri("/contacts/v4/contacts/{contactId}", contactId)
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "contact", Contact.class);
        });
    }

    public ContactList listContacts(PagingRequest paging) {
        return retryHandler.executeWithRetry(() ->
                restClient.post()
                        .uri("/contacts/v4/contacts/query")
                        .body(Map.of(
                                "query", Map.of("paging", Map.of("limit", paging.getLimit(), "offset", paging.getOffset()))
                        ))
                        .retrieve()
                        .body(ContactList.class)
        );
    }

    public ContactList queryContacts(QueryContactsRequest request) {
        return retryHandler.executeWithRetry(() ->
                restClient.post()
                        .uri("/contacts/v4/contacts/query")
                        .body(Map.of("query", request))
                        .retrieve()
                        .body(ContactList.class)
        );
    }

    public Contact createContact(ContactInfo contactInfo) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.post()
                    .uri("/contacts/v4/contacts")
                    .body(Map.of("info", contactInfo))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "contact", Contact.class);
        });
    }

    public Contact updateContact(String contactId, String revision, ContactInfo contactInfo) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.patch()
                    .uri("/contacts/v4/contacts/{contactId}", contactId)
                    .body(Map.of("info", contactInfo, "revision", revision))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "contact", Contact.class);
        });
    }

    public void deleteContact(String contactId) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.delete()
                        .uri("/contacts/v4/contacts/{contactId}", contactId)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public LabelList listLabels(PagingRequest paging) {
        return retryHandler.executeWithRetry(() ->
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/contacts/v4/labels")
                                .queryParam("paging.limit", paging.getLimit())
                                .queryParam("paging.offset", paging.getOffset())
                                .build())
                        .retrieve()
                        .body(LabelList.class)
        );
    }

    public Label createLabel(String displayName) {
        return retryHandler.executeWithRetry(() -> {
            Map<?, ?> response = restClient.post()
                    .uri("/contacts/v4/labels")
                    .body(Map.of("displayName", displayName))
                    .retrieve()
                    .body(Map.class);
            return extractFromMap(response, "label", Label.class);
        });
    }

    public void deleteLabel(String labelKey) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.delete()
                        .uri("/contacts/v4/labels/{labelKey}", labelKey)
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public void labelContact(String contactId, List<String> labelKeys) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.post()
                        .uri("/contacts/v4/contacts/{contactId}/labels", contactId)
                        .body(Map.of("labelKeys", labelKeys))
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public void unlabelContact(String contactId, List<String> labelKeys) {
        retryHandler.executeWithRetryNoReturn(() ->
                restClient.post()
                        .uri("/contacts/v4/contacts/{contactId}/unlabel", contactId)
                        .body(Map.of("labelKeys", labelKeys))
                        .retrieve()
                        .toBodilessEntity()
        );
    }
}
