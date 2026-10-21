package com.neueda.leap.client;

import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.FxRateResponse;
import com.neueda.leap.dto.response.MarketInstrumentMetadata;
import com.neueda.leap.dto.response.MarketSectorCatalog;
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

	public MarketSectorCatalog fetchSectorCatalog() {
		try {
			MarketSectorCatalog response = restClient.get()
					.uri(uri -> uri.path("/sectors").build())
					.retrieve()
					.body(MarketSectorCatalog.class);

			if (response == null || response.sectors() == null || response.sectors().isEmpty()) {
				throw new MarketDataUnavailableException(
						"Market service returned an empty sector catalog");
			}
			return response;
		} catch (RestClientException e) {
			throw new MarketDataUnavailableException(
					"Market sector catalog service failed", e);
		}
	}

	public MarketInstrumentMetadata lookupInstrument(String ticker) {
		try {
			MarketInstrumentMetadata response = restClient.get()
					.uri("/lookup/{ticker}", ticker)
					.retrieve()
					.body(MarketInstrumentMetadata.class);

			if (response == null) {
				throw new MarketDataUnavailableException(
						"Market lookup returned empty response for " + ticker);
			}
			return response;
		} catch (HttpClientErrorException e) {
			int status = e.getStatusCode().value();
			if (status == 404) {
				throw new ObjectNotFoundException(
						"Ticker does not exist or is not tradable: " + ticker);
			}
			if (status == 400 || status == 422) {
				throw new ObjectInvalidException(
						"Ticker cannot be traded: " + ticker);
			}
			throw new MarketDataUnavailableException(
					"Market lookup failed for " + ticker, e);
		} catch (RestClientException e) {
			throw new MarketDataUnavailableException(
					"Market lookup failed for " + ticker, e);
		}
	}

	public FxRateResponse fetchExchangeRate(String baseCurrency, String quoteCurrency) {
		try {
			FxRateResponse response = restClient.get()
					.uri(uri -> uri.path("/fx/rate")
							.queryParam("base", baseCurrency)
							.queryParam("quote", quoteCurrency)
							.build())
					.retrieve()
					.body(FxRateResponse.class);

			if (response == null) {
				throw new MarketDataUnavailableException(
						"Market service returned an empty FX response");
			}
			return response;
		} catch (HttpClientErrorException.BadRequest e) {
			throw new ObjectInvalidException(
					"Unsupported FX conversion: " + baseCurrency + "/" + quoteCurrency);
		} catch (RestClientException e) {
			throw new MarketDataUnavailableException(
					"Market FX service failed for " + baseCurrency + "/" + quoteCurrency, e);
		}
	}

	public void subscribeLiveTicker(String ticker) {
		try {
			restClient.post()
					.uri("/stream/subscribe/{ticker}", ticker)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			throw new MarketDataUnavailableException(
					"Market data service failed to subscribe to " + ticker, e);
		}
	}

	public void unsubscribeLiveTicker(String ticker) {
		try {
			restClient.delete()
					.uri("/stream/subscribe/{ticker}", ticker)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			throw new MarketDataUnavailableException(
					"Market data service failed to unsubscribe from " + ticker, e);
		}
	}
}