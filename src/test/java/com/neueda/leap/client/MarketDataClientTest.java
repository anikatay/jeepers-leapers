package com.neueda.leap.client;

import com.neueda.leap.dto.response.HistoryResponse;
import com.neueda.leap.dto.response.PriceBar;
import com.neueda.leap.exception.MarketDataUnavailableException;
import com.neueda.leap.exception.ObjectInvalidException;
import com.neueda.leap.exception.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataClientTest {

    @Mock
    private RestClient.Builder builder;

    @Mock
    private RestClient restClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec uriSpec;

    @Mock
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersSpec headersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private MarketDataClient marketDataClient;

    @BeforeEach
    void setUp() {
        lenient().when(builder.baseUrl("http://mock-market")).thenReturn(builder);
        lenient().when(builder.requestFactory(any())).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(restClient);

        lenient().when(restClient.get()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(any(Function.class))).thenReturn(headersSpec);
        lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);

        marketDataClient = new MarketDataClient(builder, "http://mock-market");
    }

    // Verifies successful REST responses are returned unchanged.
    @Test
    void testFetchHistoryReturnsResponseWhenApiSucceeds() {
        HistoryResponse expected = history("AAPL");
        when(responseSpec.body(HistoryResponse.class)).thenReturn(expected);

        HistoryResponse actual = marketDataClient.fetchHistory("AAPL", "1m");

        assertEquals(expected, actual);
        verify(responseSpec).body(HistoryResponse.class);
    }

    // Verifies null response bodies are mapped to MarketDataUnavailableException.
    @Test
    void testFetchHistoryThrowsWhenResponseBodyIsNull() {
        when(responseSpec.body(HistoryResponse.class)).thenReturn(null);

        MarketDataUnavailableException ex = assertThrows(
                MarketDataUnavailableException.class,
                () -> marketDataClient.fetchHistory("AAPL", "1m")
        );

        assertEquals("Market data service returned empty response for AAPL", ex.getMessage());
    }

    // Verifies HTTP 400 errors are mapped to ObjectInvalidException.
    @Test
    void testFetchHistoryMapsBadRequestToObjectInvalidException() {
        HttpClientErrorException badRequest = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
                "bad request",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(responseSpec.body(HistoryResponse.class)).thenThrow(badRequest);

        ObjectInvalidException ex = assertThrows(
                ObjectInvalidException.class,
                () -> marketDataClient.fetchHistory("AAPL", "bad-period")
        );

        assertEquals("Invalid market data request for AAPL", ex.getMessage());
    }

    // Verifies HTTP 404 errors are mapped to ObjectNotFoundException.
    @Test
    void testFetchHistoryMapsNotFoundToObjectNotFoundException() {
        HttpClientErrorException notFound = HttpClientErrorException.create(
            HttpStatus.NOT_FOUND,
                "not found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(responseSpec.body(HistoryResponse.class)).thenThrow(notFound);

        ObjectNotFoundException ex = assertThrows(
                ObjectNotFoundException.class,
                () -> marketDataClient.fetchHistory("AAPL", "1m")
        );

        assertEquals("No market data for AAPL", ex.getMessage());
    }

    // Verifies non-HTTP REST failures are mapped to MarketDataUnavailableException with cause.
    @Test
    void testFetchHistoryMapsOtherRestErrorsToMarketDataUnavailableException() {
        RestClientException restError = new RestClientException("connection refused");
        when(responseSpec.body(HistoryResponse.class)).thenThrow(restError);

        MarketDataUnavailableException ex = assertThrows(
                MarketDataUnavailableException.class,
                () -> marketDataClient.fetchHistory("AAPL", "1m")
        );

        assertEquals("Market data service failed for AAPL", ex.getMessage());
        assertInstanceOf(RestClientException.class, ex.getCause());
    }

    // Verifies 5xx HTTP server errors are mapped to MarketDataUnavailableException.
    @Test
    void testFetchHistoryMapsServerErrorToMarketDataUnavailableException() {
        HttpServerErrorException serverError = new HttpServerErrorException(
                HttpStatusCode.valueOf(503),
                "service unavailable"
        );
        when(responseSpec.body(HistoryResponse.class)).thenThrow(serverError);

        MarketDataUnavailableException ex = assertThrows(
                MarketDataUnavailableException.class,
                () -> marketDataClient.fetchHistory("AAPL", "1m")
        );

        assertEquals("Market data service failed for AAPL", ex.getMessage());
        assertInstanceOf(HttpServerErrorException.class, ex.getCause());
    }

    // Verifies errors before response-body extraction are also mapped to MarketDataUnavailableException.
    @Test
    void testFetchHistoryMapsUriBuildingErrorsToMarketDataUnavailableException() {
        RestClientException uriError = new RestClientException("uri build failed");
        when(uriSpec.uri(any(Function.class))).thenThrow(uriError);

        MarketDataUnavailableException ex = assertThrows(
                MarketDataUnavailableException.class,
                () -> marketDataClient.fetchHistory("AAPL", "1m")
        );

        assertEquals("Market data service failed for AAPL", ex.getMessage());
        assertInstanceOf(RestClientException.class, ex.getCause());
    }

    private HistoryResponse history(String ticker) {
        PriceBar bar = new PriceBar(
                LocalDate.of(2026, 1, 31),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(102),
                BigDecimal.valueOf(98),
                BigDecimal.valueOf(101),
                1_000L
        );
        return new HistoryResponse(ticker, "1m", List.of(bar));
    }
}
