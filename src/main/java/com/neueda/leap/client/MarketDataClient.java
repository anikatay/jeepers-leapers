package com.neueda.leap.client;

import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.exception.ObjectInvalidException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.Duration;

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
			return restClient.get()
					.uri(uri -> uri.path("/history")
							.queryParam("ticker", ticker)
							.queryParam("period", period)
							.build())
					.retrieve()
					.body(HistoryResponse.class);
		}catch (HttpClientErrorException.NotFound e) {
            throw new ObjectNotFoundException("No market data for " + ticker);
		} catch (HttpClientErrorException.BadRequest e) {
			throw new ObjectInvalidException("Invalid market data request for " + ticker);
        } catch (RestClientException e) {
            throw new MarketDataUnavailableException("Market data service failed for " + ticker, e);
        }
	}
}
