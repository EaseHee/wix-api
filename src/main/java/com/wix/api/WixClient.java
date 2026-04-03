package com.wix.api;

import com.wix.api.http.RetryHandler;
import com.wix.api.module.analytics.AnalyticsService;
import com.wix.api.module.cms.CmsService;
import com.wix.api.module.contacts.ContactsService;
import com.wix.api.module.inbox.InboxService;
import com.wix.api.module.members.MembersService;
import com.wix.api.module.seo.SeoService;
import org.springframework.web.client.RestClient;

public class WixClient {

    private final InboxService inbox;
    private final CmsService cms;
    private final ContactsService contacts;
    private final MembersService members;
    private final AnalyticsService analytics;
    private final SeoService seo;

    public WixClient(RestClient restClient, RetryHandler retryHandler) {
        this.inbox = new InboxService(restClient, retryHandler);
        this.cms = new CmsService(restClient, retryHandler);
        this.contacts = new ContactsService(restClient, retryHandler);
        this.members = new MembersService(restClient, retryHandler);
        this.analytics = new AnalyticsService(restClient, retryHandler);
        this.seo = new SeoService(restClient, retryHandler);
    }

    public InboxService inbox() {
        return inbox;
    }

    public CmsService cms() {
        return cms;
    }

    public ContactsService contacts() {
        return contacts;
    }

    public MembersService members() {
        return members;
    }

    public AnalyticsService analytics() {
        return analytics;
    }

    public SeoService seo() {
        return seo;
    }

    public static WixClientBuilder builder() {
        return new WixClientBuilder();
    }
}
