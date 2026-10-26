package com.neueda.leap.controller;

import com.neueda.leap.ai.ModelCallException;
import com.neueda.leap.analysis.TooManyAnalysesException;
import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.hunter.HunterFixtures;
import com.neueda.leap.hunter.HunterService;
import com.neueda.leap.hunter.LimitReachedException;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpecInvalidException;
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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs through the real SecurityConfig with tokens signed by JwtUtil, as the login endpoint issues them. */
@WebMvcTest(HunterController.class)
@Import({SecurityConfig.class, JwtUtil.class})
@TestPropertySource(properties = "jwt.shared-secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcy1sb25n")
class HunterEndpointTest {

    private static final UUID USER_ID = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID SPEC_ID = UUID.fromString("e5000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private HunterService hunterService;
    @MockBean
    private AuthorizationService authorizationService;

    @BeforeEach
    void readTheUserFromTheTokensSubject() {
        when(authorizationService.getAuthenticatedUserId(any()))
                .thenAnswer(invocation -> UUID.fromString(invocation.<Jwt>getArgument(0).getSubject()));
    }

    @Test
    void requiresALoginToken() throws Exception {
        mvc.perform(get("/api/hunter/catalog")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/hunter/specs/draft").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(hunterService);
    }

    @Test
    void returnsADraftForTheLoggedInUser() throws Exception {
        when(hunterService.draft(USER_ID, "high-margin AI")).thenReturn(HunterFixtures.aiInfrastructure());

        mvc.perform(withToken(post("/api/hunter/specs/draft")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"high-margin AI\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantRules[0].metric").value("grossMarginPct"))
                .andExpect(jsonPath("$.quantRules[0].op").value("gte"))
                .andExpect(jsonPath("$.criteria[0].id").value("s1"))
                .andExpect(content().string(containsString("\"id\":null")))
                .andExpect(content().string(containsString("\"sectorRule\":null")));
    }

    @Test
    void savesAHunterWith201() throws Exception {
        HunterSpec saved = HunterFixtures.aiInfrastructure().withId(SPEC_ID);
        when(hunterService.createSpec(eq(USER_ID), any())).thenReturn(saved);

        mvc.perform(withToken(post("/api/hunter/specs")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"specVersion":1,"id":null,"name":"AI infrastructure","prompt":"p","universe":"SP500",
                                 "quantRules":[{"id":"q1","enabled":true,"sourceText":null,"metric":"grossMarginPct",
                                                "op":"gte","value":50,"value2":null}],
                                 "sectorRule":null,"criteria":[],"unsupported":[],"candidateLimit":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SPEC_ID.toString()));
    }

    @Test
    void reportsEachInvalidFieldWith400() throws Exception {
        when(hunterService.createSpec(eq(USER_ID), any()))
                .thenThrow(HunterSpecInvalidException.of("quantRules[0].metric", "unknown metric", null));

        mvc.perform(withToken(post("/api/hunter/specs")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specVersion\":1,\"quantRules\":[{\"metric\":\"ebitda\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantRules[0].metric"));
    }

    @Test
    void returns404ForAnUnknownHunterOrSomeoneElses() throws Exception {
        when(hunterService.getSpec(USER_ID, SPEC_ID)).thenThrow(new ObjectNotFoundException("Hunter not found"));

        mvc.perform(withToken(get("/api/hunter/specs/" + SPEC_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returns409AtTheLimit() throws Exception {
        when(hunterService.createSpec(eq(USER_ID), any())).thenThrow(new LimitReachedException("at most 20 hunters"));

        mvc.perform(withToken(post("/api/hunter/specs")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_REACHED"));
    }

    @Test
    void returns429WithRetryAfter() throws Exception {
        when(hunterService.run(USER_ID, SPEC_ID)).thenThrow(new TooManyAnalysesException("try again in 41 seconds", 41));

        mvc.perform(withToken(post("/api/hunter/specs/" + SPEC_ID + "/runs")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "41"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ANALYSES"));
    }

    @Test
    void returns502WithoutLeakingTheUpstreamError() throws Exception {
        when(hunterService.run(USER_ID, SPEC_ID))
                .thenThrow(new ModelCallException("OpenAI returned HTTP 401: Incorrect API key provided: sk-abc"));

        mvc.perform(withToken(post("/api/hunter/specs/" + SPEC_ID + "/runs")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ANALYSIS_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("sk-abc"))));
    }

    @Test
    void deletesWith204() throws Exception {
        mvc.perform(withToken(delete("/api/hunter/specs/" + SPEC_ID))).andExpect(status().isNoContent());

        verify(hunterService).deleteSpec(USER_ID, SPEC_ID);
    }

    @Test
    void listsOnlyTheCallersHunters() throws Exception {
        when(hunterService.listSpecs(USER_ID)).thenReturn(List.of());

        mvc.perform(withToken(get("/api/hunter/specs"))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        verify(hunterService).listSpecs(USER_ID);
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
        return request.header("Authorization",
                "Bearer " + jwtUtil.generateToken("joanna@example.com", "ROLE_CUSTOMER", USER_ID, UUID.randomUUID()));
    }
}
