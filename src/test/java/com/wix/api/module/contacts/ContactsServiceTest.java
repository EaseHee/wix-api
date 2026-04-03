package com.wix.api.module.contacts;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import com.wix.api.WixClient;
import com.wix.api.common.PagingRequest;
import com.wix.api.module.contacts.dto.ContactList;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContactsServiceTest {

    private MockWebServer mockServer;
    private WixClient wixClient;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        wixClient = WixClient.builder()
                .apiKey("test-api-key")
                .siteId("test-site-id")
                .baseUrl(mockServer.url("/").toString())
                .maxRetries(1)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void shouldListContacts() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "contacts": [
                            {
                              "id": "contact-1",
                              "revision": 1,
                              "primaryInfo": { "email": "test@example.com" }
                            }
                          ],
                          "pagingMetadata": {
                            "count": 1,
                            "offset": 0,
                            "total": 1,
                            "hasNext": false
                          }
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        ContactList result = wixClient.contacts().listContacts(
                PagingRequest.builder().limit(50).offset(0).build());

        assertNotNull(result);
        assertEquals(1, result.getContacts().size());
        assertEquals("contact-1", result.getContacts().get(0).getId());
        assertEquals("test@example.com", result.getContacts().get(0).getPrimaryInfo().get("email"));

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().contains("/contacts/v4/contacts/query"));
        assertEquals("test-api-key", request.getHeader("Authorization"));
        assertEquals("test-site-id", request.getHeader("wix-site-id"));
    }

    @Test
    void shouldGetContact() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                        {
                          "contact": {
                            "id": "contact-123",
                            "revision": 2
                          }
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        var contact = wixClient.contacts().getContact("contact-123");

        assertNotNull(contact);
        assertEquals("contact-123", contact.getId());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/contacts/v4/contacts/contact-123", request.getPath());
    }

    @Test
    void shouldDeleteContact() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        assertDoesNotThrow(() -> wixClient.contacts().deleteContact("contact-123"));

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/contacts/v4/contacts/contact-123", request.getPath());
    }
}
