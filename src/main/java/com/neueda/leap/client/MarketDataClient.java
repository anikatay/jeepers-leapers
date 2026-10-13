package com.neueda.leap.client;

import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectInvalidException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component 
public class MarketDataClient {
	private final RestClient restClient;

	public MarketDataClient(RestClient.Builder builder, @Value("${market.url}") String baseUrl){
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(15));

		this.restClient = builder
				.baseUrl(baseUrl)
				.requestFactory(factory)
				.build();
	}

	public HistoryResponse fetchHistory(String ticker, String period){
		try{
			HistoryResponse response = restClient.get()
					.uri(uri -> uri.path("/history")
							.queryParam("ticker", ticker)
							.queryParam("period", period)
							.build())
					.retrieve()
					.body(HistoryResponse.class);

			if (response == null) {
				throw new MarketDataUnavailableException("Market data service returned empty response for " + ticker);
			}
			return response;
		}catch (HttpClientErrorException.NotFound e) {
			throw new ObjectNotFoundException("No market data for " + ticker);
		} catch (HttpClientErrorException.BadRequest e) {
			throw new ObjectInvalidException("Invalid market data request for " + ticker);
		} catch (RestClientException e) {
			throw new MarketDataUnavailableException("Market data service failed for " + ticker, e);
		}
	}

	public Map<String, HistoryResponse> fetchHistoryBatch(Set<String> tickers, String period) {
		if (tickers == null || tickers.isEmpty()) {
			throw new ObjectInvalidException("Tickers cannot be empty");
		}

		String encodedTickers = tickers.stream().map(String::trim).collect(Collectors.joining(","));
		try {
			Map<String, HistoryResponse> response = restClient.get()
					.uri(uri -> uri.path("/history/batch")
							.queryParam("tickers", encodedTickers)
							.queryParam("period", period)
							.build())
					.retrieve()
					.body(new ParameterizedTypeReference<Map<String, HistoryResponse>>() {});

			if (response == null || response.isEmpty()) {
				throw new MarketDataUnavailableException("Market data service returned empty batch response");
			}
			return response;
		} catch (HttpClientErrorException.BadRequest e) {
			throw new ObjectInvalidException("Invalid market data batch request");
		} catch (RestClientException e) {
			throw new MarketDataUnavailableException("Market data service failed for batch request", e);
		}
	}

	public void subscribeLiveTicker(String ticker) {
		restClient.post()
				.uri("/stream/subscribe/{ticker}", ticker)
				.retrieve()
				.toBodilessEntity();
	}

	public void unsubscribeLiveTicker(String ticker) {
		restClient.delete()
				.uri("/stream/subscribe/{ticker}", ticker)
				.retrieve()
				.toBodilessEntity();
	}
}