package com.neueda.leap.hunter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Metric;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.hunter.check.CandidateChecks;
import com.neueda.leap.hunter.find.CandidateFinder;
import com.neueda.leap.hunter.score.MatchScorer;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.hunter.spec.HunterSpecValidator;
import com.neueda.leap.hunter.translate.SpecTranslator;
import com.neueda.leap.mapper.HunterRunMapper;
import com.neueda.leap.mapper.HunterSpecMapper;
import com.neueda.leap.mapper.WatchlistMapper;
import com.neueda.leap.model.HunterRunRecord;
import com.neueda.leap.model.HunterSpecRecord;
import com.neueda.leap.model.HunterSpecSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Saved hunters, drafts and runs (sections 4–6 of docs/specs/watchlist-hunter.md). */
@Service
public class HunterService {

    private static final Logger log = LoggerFactory.getLogger(HunterService.class);

    private final HunterSpecMapper specMapper;
    private final HunterRunMapper runMapper;
    private final WatchlistMapper watchlistMapper;
    private final SpecTranslator translator;
    private final CandidateFinder finder;
    private final Universe universe;
    private final HunterModelSettings settings;
    private final HunterRateLimits rateLimits;
    private final ObjectMapper json;
    private final Clock clock;

    public HunterService(HunterSpecMapper specMapper, HunterRunMapper runMapper, WatchlistMapper watchlistMapper,
            SpecTranslator translator, CandidateFinder finder, Universe universe, HunterModelSettings settings,
            HunterRateLimits rateLimits, ObjectMapper json, Clock clock) {
        this.specMapper = specMapper;
        this.runMapper = runMapper;
        this.watchlistMapper = watchlistMapper;
        this.translator = translator;
        this.finder = finder;
        this.universe = universe;
        this.settings = settings;
        this.rateLimits = rateLimits;
        this.json = json;
        this.clock = clock;
    }

    public HunterCatalog catalog() {
        return new HunterCatalog(
                Arrays.stream(Metric.values())
                        .map(m -> new HunterCatalog.MetricInfo(m.id(), m.label(), m.unit(), m.definition()))
                        .toList(),
                Arrays.stream(GicsSector.values()).map(GicsSector::label).toList(),
                settings.model(), settings.knowledgeCutoff(), universe.asOf(), universe.size(),
                new HunterCatalog.Limits(HunterLimits.MAX_PROMPT_LENGTH, HunterLimits.MAX_QUANT_RULES,
                        HunterLimits.MAX_CRITERIA, HunterLimits.MAX_CANDIDATES, HunterLimits.DEFAULT_CANDIDATES,
                        HunterLimits.MAX_SPECS_PER_USER, HunterLimits.MAX_WATCHLIST_ITEMS));
    }

    /** A cached draft costs nothing, so only a model call counts against the rate limit. */
    public HunterSpec draft(UUID userId, String prompt) {
        HunterSpecValidator.requireValidPrompt(prompt);
        var cached = translator.cached(prompt);
        if (cached.isPresent()) {
            log.info("hunter draft cache=hit model={}", settings.model());
            return cached.get();
        }
        rateLimits.drafts().acquire(userId.toString());
        SpecTranslator.Translation translation = translator.translate(prompt);
        var completion = translation.completion();
        log.info("hunter draft cache=miss model={} quantRules={} criteria={} unsupported={} promptTokens={} "
                        + "completionTokens={} reasoningTokens={} latencyMs={}",
                completion.model(), translation.spec().quantRules().size(), translation.spec().criteria().size(),
                translation.spec().unsupported().size(), completion.promptTokens(), completion.completionTokens(),
                completion.reasoningTokens(), completion.latencyMs());
        return translation.spec();
    }

    public List<HunterSpecSummary> listSpecs(UUID userId) {
        return specMapper.findSummaries(userId);
    }

    public HunterSpec createSpec(UUID userId, HunterSpec spec) {
        HunterSpecValidator.requireValid(spec);
        if (specMapper.countByUser(userId) >= HunterLimits.MAX_SPECS_PER_USER) {
            throw new LimitReachedException("You can save at most " + HunterLimits.MAX_SPECS_PER_USER + " hunters");
        }
        HunterSpec saved = spec.withId(UUID.randomUUID());
        OffsetDateTime now = OffsetDateTime.now(clock);
        specMapper.insert(new HunterSpecRecord(saved.id(), userId, saved.name(), promptOf(saved), write(saved), now, now));
        return saved;
    }

    public HunterSpec getSpec(UUID userId, UUID specId) {
        return read(requireOwned(userId, specId).getSpecJson(), HunterSpec.class);
    }

    public HunterSpec updateSpec(UUID userId, UUID specId, HunterSpec spec) {
        HunterSpecValidator.requireValid(spec);
        requireOwned(userId, specId);
        HunterSpec saved = spec.withId(specId);
        specMapper.update(new HunterSpecRecord(specId, userId, saved.name(), promptOf(saved), write(saved), null,
                OffsetDateTime.now(clock)));
        return saved;
    }

    public void deleteSpec(UUID userId, UUID specId) {
        if (specMapper.delete(specId, userId) == 0) {
            throw notFound(specId);
        }
    }

    /**
     * Returns the saved run when nothing the model would be told has changed: its knowledge does not
     * change, so asking again would only add cost and variation.
     */
    public HunterRun run(UUID userId, UUID specId) {
        HunterSpec spec = read(requireOwned(userId, specId).getSpecJson(), HunterSpec.class);
        HunterSpecValidator.requireRunnable(spec);
        String hash = specHash(spec, settings.model());

        HunterRunRecord latest = runMapper.findLatest(specId);
        if (latest != null && hash.equals(latest.getSpecHash())) {
            log.info("hunter run specId={} reused=true", specId);
            return read(latest.getResultJson(), HunterRun.class).asReturned(true, watchlistTickers(userId));
        }

        rateLimits.runs().acquire(userId.toString());
        CandidateFinder.Result found = finder.find(spec);
        CandidateChecks.Outcome outcome = CandidateChecks.apply(spec, found.candidates(), universe);
        List<HunterRun.Match> matches = MatchScorer.rank(outcome.kept(), spec.candidateLimit());

        HunterRun run = new HunterRun(UUID.randomUUID(), specId, OffsetDateTime.now(clock),
                new HunterRun.Source(settings.model(), settings.knowledgeCutoff()), matches, outcome.droppedCount(),
                HunterRun.DISCLAIMER.formatted(settings.cutoffMonth()), false);
        var completion = found.completion();
        runMapper.insert(new HunterRunRecord(run.runId(), specId, hash, run.ranAt(), write(run), completion.model(),
                matches.size(), completion.promptTokens(), completion.completionTokens(), completion.latencyMs()));
        runMapper.deleteAllButLatest(specId, HunterLimits.RUNS_KEPT_PER_SPEC);

        log.info("hunter run specId={} reused=false returned={} kept={} droppedNotInUniverse={} droppedByRules={} "
                        + "sourceGuardRemovals={} adviceGuardRemovals={} model={} promptTokens={} completionTokens={} "
                        + "reasoningTokens={} latencyMs={}",
                specId, found.candidates().size(), matches.size(), outcome.droppedNotInUniverse(),
                outcome.droppedByRules(), outcome.sourceGuardRemovals(), outcome.adviceGuardRemovals(),
                completion.model(), completion.promptTokens(), completion.completionTokens(),
                completion.reasoningTokens(), completion.latencyMs());
        return run.asReturned(false, watchlistTickers(userId));
    }

    public HunterRun latestRun(UUID userId, UUID specId) {
        requireOwned(userId, specId);
        HunterRunRecord latest = runMapper.findLatest(specId);
        if (latest == null) {
            throw new ObjectNotFoundException("Hunter " + specId + " has not been run yet");
        }
        return read(latest.getResultJson(), HunterRun.class).asReturned(false, watchlistTickers(userId));
    }

    /**
     * Everything that shapes the model's answer: enabled rules and checks with their IDs, the
     * candidate limit, the model and the prompt version. Names, prompts and disabled rules are left out.
     */
    static String specHash(HunterSpec spec, String model) {
        StringBuilder key = new StringBuilder()
                .append(CandidateFinder.PROMPT_VERSION).append('|').append(model)
                .append("|limit=").append(spec.candidateLimit());
        spec.enabledQuantRules().forEach(rule -> key.append("|q:").append(rule.id()).append(',')
                .append(rule.metric().id()).append(',').append(rule.op().id()).append(',')
                .append(plain(rule.value())).append(',').append(plain(rule.value2())));
        if (spec.sectorRuleEnabled()) {
            key.append("|sector:").append(spec.sectorRule().op().id()).append(',')
                    .append(spec.sectorRule().values().stream().map(GicsSector::label).sorted()
                            .collect(Collectors.joining(",")));
        }
        spec.enabledCriteria().forEach(criterion -> key.append("|c:").append(criterion.id()).append(',')
                .append(criterion.statement().strip().replaceAll("\\s+", " ")));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private HunterSpecRecord requireOwned(UUID userId, UUID specId) {
        HunterSpecRecord record = specMapper.findOwned(specId, userId);
        if (record == null) {
            throw notFound(specId);
        }
        return record;
    }

    private static ObjectNotFoundException notFound(UUID specId) {
        return new ObjectNotFoundException("Hunter not found: " + specId);
    }

    private Set<String> watchlistTickers(UUID userId) {
        return new HashSet<>(watchlistMapper.findTickers(userId));
    }

    private static String promptOf(HunterSpec spec) {
        return spec.prompt() == null ? "" : spec.prompt();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + value.getClass().getSimpleName(), e);
        }
    }

    private <T> T read(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read a stored " + type.getSimpleName(), e);
        }
    }
}
