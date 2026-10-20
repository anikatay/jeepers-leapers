package com.neueda.leap.controller;

import com.neueda.leap.analysis.AnalysisRateLimiter;
import com.neueda.leap.analysis.TooManyAnalysesException;
import com.neueda.leap.analysis.TradeAnalysisContext;
import com.neueda.leap.analysis.TradeAnalysisException;
import com.neueda.leap.analysis.TradeAnalysisResult;
import com.neueda.leap.analysis.TradeAnalysisService;
import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.service.TradeService;
import com.neueda.leap.util.JwtUtil;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs through the real SecurityConfig with tokens signed by JwtUtil, as the login endpoint issues them. */
@WebMvcTest(TradeController.class)
@Import({SecurityConfig.class, JwtUtil.class})
@TestPropertySource(properties = "jwt.shared-secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcy1sb25n")
class TradeAnalysisEndpointTest {

    private static final UUID USER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID TRADE_ID = UUID.fromString("c3000000-0000-0000-0000-000000000006");
    private static final String ANALYSIS_URL = "/api/trades/" + TRADE_ID + "/analysis";
    private static final String CONTEXT_URL = ANALYSIS_URL + "/context";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private TradeService tradeService;
    @MockBean
    private TradeAnalysisService tradeAnalysisService;
    @MockBean
    private AnalysisRateLimiter rateLimiter;

    @Test
    void requiresALoginToken() throws Exception {
        mvc.perform(post(ANALYSIS_URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(CONTEXT_URL)).andExpect(status().isUnauthorized());

        verifyNoInteractions(tradeAnalysisService, rateLimiter);
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() throws Exception {
        JwtUtil otherIssuer = new JwtUtil();
        ReflectionTestUtils.setField(otherIssuer, "secret", "YW5vdGhlci1zZWNyZXQtdGhhdC1pcy1hbHNvLTMyLWJ5dGVzLWxvbmc=");
        String forged = otherIssuer.generateToken("mallory@example.com", "ROLE_CUSTOMER", USER_ID, UUID.randomUUID());

        mvc.perform(post(ANALYSIS_URL).header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tradeAnalysisService);
    }

    @Test
    void leavesTheRestOfTheApiOpen() throws Exception {
        mvc.perform(get("/api/trades/account/" + UUID.randomUUID())).andExpect(status().isNoContent());
    }

    @Test
    void returnsTheAnalysisOfTheCallersOwnTrade() throws Exception {
        when(tradeAnalysisService.analyze(TRADE_ID, USER_ID)).thenReturn(new TradeAnalysisResult(
                TradeAnalysisResult.Verdict.GOOD, "You sold at a gain.",
                List.of("Took profit"), List.of(), List.of("Keep sizes similar")));

        mvc.perform(withToken(post(ANALYSIS_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("GOOD"))
                .andExpect(jsonPath("$.summary").value("You sold at a gain."))
                .andExpect(jsonPath("$.strengths[0]").value("Took profit"))
                .andExpect(jsonPath("$.concerns").isEmpty())
                .andExpect(jsonPath("$.suggestions[0]").value("Keep sizes similar"));
    }

    @Test
    void rateLimitsByTheLoggedInUser() throws Exception {
        when(tradeAnalysisService.analyze(TRADE_ID, USER_ID)).thenReturn(new TradeAnalysisResult(
                TradeAnalysisResult.Verdict.TOO_EARLY, "Too early.", List.of(), List.of(), List.of()));

        mvc.perform(withToken(post(ANALYSIS_URL)).header("X-Real-IP", "203.0.113.7")).andExpect(status().isOk());

        verify(rateLimiter).acquire(USER_ID.toString());
    }

    @Test
    void returns404ForAnUnknownTradeOrSomeoneElses() throws Exception {
        // The service reports another account's trade exactly like a missing one
        when(tradeAnalysisService.analyze(TRADE_ID, USER_ID)).thenThrow(new ObjectNotFoundException("Trade not found: " + TRADE_ID));
        when(tradeAnalysisService.buildContext(TRADE_ID, USER_ID)).thenThrow(new ObjectNotFoundException("Trade not found: " + TRADE_ID));

        mvc.perform(withToken(post(ANALYSIS_URL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(withToken(get(CONTEXT_URL))).andExpect(status().isNotFound());
    }

    @Test
    void returns502WithoutLeakingTheUpstreamError() throws Exception {
        when(tradeAnalysisService.analyze(TRADE_ID, USER_ID))
                .thenThrow(new TradeAnalysisException("OpenAI returned HTTP 401: Incorrect API key provided: sk-abc"));

        mvc.perform(withToken(post(ANALYSIS_URL)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ANALYSIS_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("sk-abc"))));
    }

    @Test
    void returns429WithRetryAfterAndSkipsTheAnalysis() throws Exception {
        doThrow(new TooManyAnalysesException("Too many trade analyses; try again in 30 seconds", 30)).when(rateLimiter).acquire(anyString());

        mvc.perform(withToken(post(ANALYSIS_URL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ANALYSES"));

        verifyNoInteractions(tradeAnalysisService);
    }

    @Test
    void returnsTheContextWithoutCallingOpenAiOrCountingAgainstTheLimit() throws Exception {
        TradeAnalysisContext context = new TradeAnalysisContext(
                new TradeAnalysisContext.Trade("TSLA", "Tesla Inc.", "NASDAQ", "SELL", new BigDecimal("3"),
                        new BigDecimal("256.50"), new BigDecimal("769.50"),
                        OffsetDateTime.parse("2026-10-02T21:52:26Z"), 13),
                null,
                new TradeAnalysisContext.PriceNow(new BigDecimal("254.75"), new BigDecimal("-0.68")),
                new TradeAnalysisContext.Portfolio("USD", new BigDecimal("35428.00"), new BigDecimal("60.99"),
                        new BigDecimal("58089.75"), new BigDecimal("1.32"), List.of()),
                null,
                List.of());
        when(tradeAnalysisService.buildContext(TRADE_ID, USER_ID)).thenReturn(context);

        mvc.perform(withToken(get(CONTEXT_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade.ticker").value("TSLA"))
                .andExpect(jsonPath("$.trade.executedAt").value("2026-10-02T21:52:26Z"))
                .andExpect(jsonPath("$.outcome").doesNotExist())
                .andExpect(jsonPath("$.portfolio.cashPct").value(60.99));

        verifyNoInteractions(rateLimiter);
        verify(tradeAnalysisService, never()).analyze(any(), any());
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
        return request.header("Authorization",
                "Bearer " + jwtUtil.generateToken("joanna@example.com", "ROLE_CUSTOMER", USER_ID, UUID.randomUUID()));
    }
}
