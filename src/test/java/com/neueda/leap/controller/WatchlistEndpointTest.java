package com.neueda.leap.controller;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.hunter.LimitReachedException;
import com.neueda.leap.hunter.WatchlistService;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.service.AuthorizationService;
import com.neueda.leap.util.JwtUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
@Import({SecurityConfig.class, JwtUtil.class})
@TestPropertySource(properties = "jwt.shared-secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcy1sb25n")
class WatchlistEndpointTest {

    private static final UUID USER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000003");
    private static final UUID SPEC_ID = UUID.fromString("e5000000-0000-0000-0000-000000000002");
    private static final WatchlistService.Item NVDA = new WatchlistService.Item("NVDA", "Nvidia",
            GicsSector.INFORMATION_TECHNOLOGY, OffsetDateTime.parse("2026-10-23T12:00:00Z"),
            new WatchlistService.SourceSpec(SPEC_ID, "AI infrastructure"));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private WatchlistService watchlistService;
    @MockBean
    private AuthorizationService authorizationService;

    @BeforeEach
    void readTheUserFromTheTokensSubject() {
        when(authorizationService.getAuthenticatedUserId(any()))
                .thenAnswer(invocation -> UUID.fromString(invocation.<Jwt>getArgument(0).getSubject()));
    }

    @Test
    void requiresALoginToken() throws Exception {
        mvc.perform(get("/api/watchlist")).andExpect(status().isUnauthorized());

        verifyNoInteractions(watchlistService);
    }

    @Test
    void returns201WhenAddedAnd200WhenAlreadyThere() throws Exception {
        when(watchlistService.add(USER_ID, "NVDA", SPEC_ID)).thenReturn(new WatchlistService.Added(NVDA, true));
        when(watchlistService.add(USER_ID, "NVDA", null)).thenReturn(new WatchlistService.Added(NVDA, false));

        mvc.perform(withToken(put("/api/watchlist/NVDA")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceSpecId\":\"" + SPEC_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("NVDA"))
                .andExpect(jsonPath("$.gicsSector").value("Information Technology"))
                .andExpect(jsonPath("$.sourceSpec.name").value("AI infrastructure"));
        mvc.perform(withToken(put("/api/watchlist/NVDA"))).andExpect(status().isOk());
    }

    @Test
    void returns404OutsideTheUniverse() throws Exception {
        when(watchlistService.add(USER_ID, "TSM", null)).thenThrow(new ObjectNotFoundException("Not in the S&P 500 list"));

        mvc.perform(withToken(put("/api/watchlist/TSM")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returns409WhenFull() throws Exception {
        when(watchlistService.add(USER_ID, "NVDA", null)).thenThrow(new LimitReachedException("at most 100 stocks"));

        mvc.perform(withToken(put("/api/watchlist/NVDA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_REACHED"));
    }

    @Test
    void listsAndRemoves() throws Exception {
        when(watchlistService.list(USER_ID)).thenReturn(List.of(NVDA));

        mvc.perform(withToken(get("/api/watchlist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Nvidia"));
        mvc.perform(withToken(delete("/api/watchlist/NVDA"))).andExpect(status().isNoContent());

        verify(watchlistService).remove(USER_ID, "NVDA");
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
        return request.header("Authorization",
                "Bearer " + jwtUtil.generateToken("joanna@example.com", "ROLE_CUSTOMER", USER_ID, UUID.randomUUID()));
    }
}
