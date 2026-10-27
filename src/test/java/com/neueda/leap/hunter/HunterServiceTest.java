package com.neueda.leap.hunter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.ai.ModelCallException;
import com.neueda.leap.ai.OpenAiJsonClient;
import com.neueda.leap.analysis.AnalysisRateLimiter;
import com.neueda.leap.analysis.TooManyAnalysesException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.hunter.find.CandidateFinder;
import com.neueda.leap.hunter.find.CandidateFinder.Candidate;
import com.neueda.leap.hunter.find.CandidateFinder.Estimate;
import com.neueda.leap.hunter.find.CandidateFinder.Judgement;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpecInvalidException;
import com.neueda.leap.hunter.translate.SpecTranslator;
import com.neueda.leap.mapper.HunterRunMapper;
import com.neueda.leap.mapper.HunterSpecMapper;
import com.neueda.leap.mapper.WatchlistMapper;
import com.neueda.leap.model.HunterRunRecord;
import com.neueda.leap.model.HunterSpecRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HunterServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPEC_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private static final OpenAiJsonClient.Completion COMPLETION =
            new OpenAiJsonClient.Completion("{}", "test-model-2026", 900, 1500, 600, 12000);

    private final HunterSpecMapper specMapper = mock(HunterSpecMapper.class);
    private final HunterRunMapper runMapper = mock(HunterRunMapper.class);
    private final WatchlistMapper watchlistMapper = mock(WatchlistMapper.class);
    private final SpecTranslator translator = mock(SpecTranslator.class);
    private final CandidateFinder finder = mock(CandidateFinder.class);
    private final HunterRateLimits rateLimits = new HunterRateLimits(
            new AnalysisRateLimiter(100, 5, CLOCK), new AnalysisRateLimiter(100, 3, CLOCK));

    private final HunterService service = new HunterService(specMapper, runMapper, watchlistMapper, translator, finder,
            new Universe(), HunterFixtures.SETTINGS, rateLimits, JSON, CLOCK);

    @BeforeEach
    void setUp() throws Exception {
        storeSpec(HunterFixtures.aiInfrastructure().withId(SPEC_ID));
        when(watchlistMapper.findTickers(USER_ID)).thenReturn(List.of("NVDA"));
        when(finder.find(any())).thenReturn(new CandidateFinder.Result(List.of(
                candidate("ANET", "64", CheckVerdict.PARTIAL),
                candidate("NVDA", "75", CheckVerdict.MET),
                candidate("MADEUP", "90", CheckVerdict.MET)), COMPLETION));
    }

    @Test
    void savesANewHunterWithAnId() {
        when(specMapper.countByUser(USER_ID)).thenReturn(3);

        HunterSpec saved = service.createSpec(USER_ID, HunterFixtures.aiInfrastructure());

        assertNotNull(saved.id());
        ArgumentCaptor<HunterSpecRecord> record = ArgumentCaptor.forClass(HunterSpecRecord.class);
        verify(specMapper).insert(record.capture());
        assertEquals(USER_ID, record.getValue().getUserId());
        assertEquals("AI infrastructure", record.getValue().getName());
        assertTrue(record.getValue().getSpecJson().contains("\"metric\":\"grossMarginPct\""));
    }

    @Test
    void refusesATwentyFirstHunter() {
        when(specMapper.countByUser(USER_ID)).thenReturn(HunterLimits.MAX_SPECS_PER_USER);

        assertThrows(LimitReachedException.class, () -> service.createSpec(USER_ID, HunterFixtures.aiInfrastructure()));
        verify(specMapper, never()).insert(any());
    }

    @Test
    void treatsAnotherUsersHunterAsNotFound() {
        UUID someoneElse = UUID.randomUUID();

        assertThrows(ObjectNotFoundException.class, () -> service.getSpec(someoneElse, SPEC_ID));
        assertThrows(ObjectNotFoundException.class, () -> service.run(someoneElse, SPEC_ID));
        assertThrows(ObjectNotFoundException.class, () -> service.deleteSpec(someoneElse, SPEC_ID));
    }

    @Test
    void runsChecksScoresAndSaves() throws Exception {
        HunterRun run = service.run(USER_ID, SPEC_ID);

        assertEquals(List.of("NVDA", "ANET"), run.matches().stream().map(HunterRun.Match::ticker).toList());
        assertEquals(List.of(100, 50), run.matches().stream().map(HunterRun.Match::matchScore).toList());
        assertEquals(1, run.droppedCount());
        assertFalse(run.reused());
        assertTrue(run.matches().get(0).onWatchlist());
        assertFalse(run.matches().get(1).onWatchlist());
        assertEquals("Nvidia", run.matches().get(0).companyName());
        assertTrue(run.disclaimer().contains("up to May 2026"));
        assertEquals(HunterFixtures.SETTINGS.knowledgeCutoff(), run.source().knowledgeCutoff());

        ArgumentCaptor<HunterRunRecord> record = ArgumentCaptor.forClass(HunterRunRecord.class);
        verify(runMapper).insert(record.capture());
        assertEquals(2, record.getValue().getMatchCount());
        assertEquals("test-model-2026", record.getValue().getModel());
        assertEquals(HunterService.specHash(HunterFixtures.aiInfrastructure().withId(SPEC_ID), "test-model"),
                record.getValue().getSpecHash());
        verify(runMapper).deleteAllButLatest(SPEC_ID, HunterLimits.RUNS_KEPT_PER_SPEC);
    }

    @Test
    void reusesTheSavedRunWhileNothingChanged() throws Exception {
        HunterRun first = service.run(USER_ID, SPEC_ID);
        ArgumentCaptor<HunterRunRecord> saved = ArgumentCaptor.forClass(HunterRunRecord.class);
        verify(runMapper).insert(saved.capture());
        when(runMapper.findLatest(SPEC_ID)).thenReturn(saved.getValue());

        HunterRun again = service.run(USER_ID, SPEC_ID);

        assertTrue(again.reused());
        assertEquals(first.runId(), again.runId());
        assertEquals(first.matches().size(), again.matches().size());
        verify(finder, times(1)).find(any());
    }

    @Test
    void asksAgainAfterAnyRuleOrModelChange() throws Exception {
        HunterSpec spec = HunterFixtures.aiInfrastructure().withId(SPEC_ID);
        String hash = HunterService.specHash(spec, "test-model");

        HunterSpec tighter = HunterFixtures.spec(List.of(HunterFixtures.grossMarginAtLeast("q1", "60")), null,
                spec.criteria()).withId(SPEC_ID);
        HunterSpec renamed = new HunterSpec(spec.specVersion(), SPEC_ID, "Another name", "other words", spec.universe(),
                spec.quantRules(), null, spec.criteria(), List.of(), spec.candidateLimit());

        assertNotEquals(hash, HunterService.specHash(tighter, "test-model"));
        assertNotEquals(hash, HunterService.specHash(spec, "another-model"));
        assertEquals(hash, HunterService.specHash(renamed, "test-model"));
        assertEquals(hash, HunterService.specHash(HunterFixtures.spec(
                List.of(HunterFixtures.grossMarginAtLeast("q1", "50.0")), null, spec.criteria()), "test-model"));

        when(runMapper.findLatest(SPEC_ID)).thenReturn(new HunterRunRecord(UUID.randomUUID(), SPEC_ID, "0".repeat(64),
                null, "{}", "test-model", 0, 0, 0, 0));
        service.run(USER_ID, SPEC_ID);
        verify(finder).find(any());
    }

    @Test
    void countsOnlyRunsThatCallTheModelAgainstTheLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            when(runMapper.findLatest(SPEC_ID)).thenReturn(null);
            service.run(USER_ID, SPEC_ID);
        }

        assertThrows(TooManyAnalysesException.class, () -> service.run(USER_ID, SPEC_ID));
        verify(finder, times(3)).find(any());
    }

    @Test
    void savesNothingWhenTheModelFails() {
        when(finder.find(any())).thenThrow(new ModelCallException("OpenAI returned HTTP 500: boom"));

        assertThrows(ModelCallException.class, () -> service.run(USER_ID, SPEC_ID));
        verify(runMapper, never()).insert(any());
    }

    @Test
    void refusesToRunAHunterWithNothingTurnedOn() throws Exception {
        storeSpec(HunterFixtures.spec(List.of(), null, List.of()).withId(SPEC_ID));

        assertThrows(HunterSpecInvalidException.class, () -> service.run(USER_ID, SPEC_ID));
        verify(finder, never()).find(any());
    }

    @Test
    void draftsFromTheCacheWithoutCountingAgainstTheLimit() {
        HunterSpec draft = HunterFixtures.aiInfrastructure();
        when(translator.cached("AI infra")).thenReturn(Optional.of(draft));

        for (int i = 0; i < 10; i++) {
            assertSame(draft, service.draft(USER_ID, "AI infra"));
        }
        verify(translator, never()).translate(any());
    }

    @Test
    void rateLimitsDraftsThatCallTheModel() {
        when(translator.cached(any())).thenReturn(Optional.empty());
        when(translator.translate(any())).thenReturn(
                new SpecTranslator.Translation(HunterFixtures.aiInfrastructure(), COMPLETION));

        for (int i = 0; i < 5; i++) {
            service.draft(USER_ID, "idea " + i);
        }

        assertThrows(TooManyAnalysesException.class, () -> service.draft(USER_ID, "one more"));
        assertThrows(HunterSpecInvalidException.class, () -> service.draft(UUID.randomUUID(), " "));
    }

    @Test
    void reportsTheLatestRunOrNotFound() throws Exception {
        assertThrows(ObjectNotFoundException.class, () -> service.latestRun(USER_ID, SPEC_ID));

        service.run(USER_ID, SPEC_ID);
        ArgumentCaptor<HunterRunRecord> saved = ArgumentCaptor.forClass(HunterRunRecord.class);
        verify(runMapper).insert(saved.capture());
        when(runMapper.findLatest(SPEC_ID)).thenReturn(saved.getValue());

        HunterRun latest = service.latestRun(USER_ID, SPEC_ID);
        assertFalse(latest.reused());
        assertEquals(2, latest.matches().size());
    }

    private void storeSpec(HunterSpec spec) throws Exception {
        when(specMapper.findOwned(eq(SPEC_ID), eq(USER_ID))).thenReturn(new HunterSpecRecord(SPEC_ID, USER_ID,
                spec.name(), spec.prompt(), JSON.writeValueAsString(spec), null, null));
        when(specMapper.delete(any(), any())).thenReturn(0);
        when(runMapper.deleteAllButLatest(any(), anyInt())).thenReturn(0);
    }

    private static Candidate candidate(String ticker, String grossMargin, CheckVerdict verdict) {
        return new Candidate(ticker, List.of(new Estimate("q1", new BigDecimal(grossMargin), "FY2025")),
                List.of(new Judgement("s1", verdict, "Sells hardware used in AI data centers.")),
                List.of("Widely used in AI data centers."));
    }
}
