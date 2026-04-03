package com.wix.api.module.seo;

import com.wix.api.http.RetryHandler;
import com.wix.api.module.AbstractWixService;
import com.wix.api.module.seo.dto.RobotsTxt;
import org.springframework.web.client.RestClient;

public class SeoService extends AbstractWixService {

    public SeoService(RestClient restClient, RetryHandler retryHandler) {
        super(restClient, retryHandler);
    }

    public RobotsTxt getRobotsTxt() {
        return retryHandler.executeWithRetry(() ->
                restClient.get()
                        .uri("/promote-seo-robots-server/v2/robots")
                        .retrieve()
                        .body(RobotsTxt.class)
        );
    }

    public RobotsTxt updateRobotsTxt(RobotsTxt robotsTxt) {
        return retryHandler.executeWithRetry(() ->
                restClient.put()
                        .uri("/promote-seo-robots-server/v2/robots")
                        .body(robotsTxt)
                        .retrieve()
                        .body(RobotsTxt.class)
        );
    }
}
