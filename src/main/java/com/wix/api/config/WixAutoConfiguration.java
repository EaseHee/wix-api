package com.wix.api.config;

import com.wix.api.WixClient;
import com.wix.api.auth.WixCredentials;
import com.wix.api.http.RetryHandler;
import com.wix.api.http.WixRestClientFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnClass(WixClient.class)
@ConditionalOnProperty(prefix = "wix.api", name = "api-key")
public class WixAutoConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "wix.api")
    @ConditionalOnMissingBean
    public WixProperties wixProperties() {
        return new WixProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public WixCredentials wixCredentials(WixProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "wix.api.api-key 설정 필요. 환경 변수 WIX_API_KEY 또는 application.yml에 설정.");
        }
        WixCredentials credentials = WixCredentials.builder()
                .apiKey(properties.getApiKey())
                .accountId(properties.getAccountId())
                .siteId(properties.getSiteId())
                .build();
        credentials.validate();
        return credentials;
    }

    @Bean
    @ConditionalOnMissingBean
    public WixClient wixClient(WixCredentials credentials, WixProperties properties) {
        RestClient restClient = WixRestClientFactory.create(credentials, properties);
        RetryHandler retryHandler = new RetryHandler(
                properties.getMaxRetries(),
                properties.getInitialRetryDelayMs(),
                properties.getRetryMultiplier(),
                properties.getMaxRetryDelayMs()
        );
        return new WixClient(restClient, retryHandler);
    }
}
