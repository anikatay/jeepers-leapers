package com.neueda.leap.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.neueda.leap.exception.ObjectNotProcessedException;

@Component
public class AnalyticsEtlClient {

    private final RestClient restClient;

    public AnalyticsEtlClient(
            RestClient.Builder builder,
            @Value("${analytics.etl.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofMinutes(5));

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public EtlRefreshResult triggerRefresh() {
        try {
            EtlRefreshResult result = restClient.post()
                    .uri("/refresh")
                    .retrieve()
                    .body(EtlRefreshResult.class);
            if (result == null) {
                throw new ObjectNotProcessedException(
                        "Analytics ETL returned an empty refresh result");
            }
            return result;
        } catch (RestClientException e) {
            throw new ObjectNotProcessedException(
                    "Analytics ETL could not be reached");
        }
    }
}