package com.neueda.leap.controller;

import com.neueda.leap.hunter.HunterCatalog;
import com.neueda.leap.hunter.HunterRun;
import com.neueda.leap.hunter.HunterService;
import com.neueda.leap.hunter.spec.HunterSpec;
import com.neueda.leap.model.HunterSpecSummary;
import com.neueda.leap.service.AuthorizationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Watchlist Hunter (docs/specs/watchlist-hunter.md, section 6). Every call needs a login token. */
@RestController
@RequestMapping("/api/hunter")
public class HunterController {

    public record DraftRequest(String prompt) {}

    private final HunterService hunterService;
    private final AuthorizationService authorizationService;

    public HunterController(HunterService hunterService, AuthorizationService authorizationService) {
        this.hunterService = hunterService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/catalog")
    public HunterCatalog catalog() {
        return hunterService.catalog();
    }

    @PostMapping("/specs/draft")
    public HunterSpec draft(@RequestBody DraftRequest request, @AuthenticationPrincipal Jwt jwt) {
        return hunterService.draft(userId(jwt), request.prompt());
    }

    @GetMapping("/specs")
    public List<HunterSpecSummary> listSpecs(@AuthenticationPrincipal Jwt jwt) {
        return hunterService.listSpecs(userId(jwt));
    }

    @PostMapping("/specs")
    public ResponseEntity<HunterSpec> createSpec(@RequestBody HunterSpec spec, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hunterService.createSpec(userId(jwt), spec));
    }

    @GetMapping("/specs/{specId}")
    public HunterSpec getSpec(@PathVariable UUID specId, @AuthenticationPrincipal Jwt jwt) {
        return hunterService.getSpec(userId(jwt), specId);
    }

    @PutMapping("/specs/{specId}")
    public HunterSpec updateSpec(@PathVariable UUID specId, @RequestBody HunterSpec spec,
            @AuthenticationPrincipal Jwt jwt) {
        return hunterService.updateSpec(userId(jwt), specId, spec);
    }

    @DeleteMapping("/specs/{specId}")
    public ResponseEntity<Void> deleteSpec(@PathVariable UUID specId, @AuthenticationPrincipal Jwt jwt) {
        hunterService.deleteSpec(userId(jwt), specId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/specs/{specId}/runs")
    public HunterRun run(@PathVariable UUID specId, @AuthenticationPrincipal Jwt jwt) {
        return hunterService.run(userId(jwt), specId);
    }

    @GetMapping("/specs/{specId}/runs/latest")
    public HunterRun latestRun(@PathVariable UUID specId, @AuthenticationPrincipal Jwt jwt) {
        return hunterService.latestRun(userId(jwt), specId);
    }

    private UUID userId(Jwt jwt) {
        return authorizationService.getAuthenticatedUserId(jwt);
    }
}
