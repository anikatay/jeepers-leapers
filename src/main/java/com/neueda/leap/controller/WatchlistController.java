package com.neueda.leap.controller;

import com.neueda.leap.hunter.WatchlistService;
import com.neueda.leap.service.AuthorizationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The caller's watchlist (docs/specs/watchlist-hunter.md, section 7). */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    public record AddRequest(UUID sourceSpecId) {}

    private final WatchlistService watchlistService;
    private final AuthorizationService authorizationService;

    public WatchlistController(WatchlistService watchlistService, AuthorizationService authorizationService) {
        this.watchlistService = watchlistService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public List<WatchlistService.Item> list(@AuthenticationPrincipal Jwt jwt) {
        return watchlistService.list(userId(jwt));
    }

    /** 201 when added, 200 when the ticker was already there. */
    @PutMapping("/{ticker}")
    public ResponseEntity<WatchlistService.Item> add(@PathVariable String ticker,
            @RequestBody(required = false) AddRequest request, @AuthenticationPrincipal Jwt jwt) {
        WatchlistService.Added added = watchlistService.add(userId(jwt), ticker,
                request == null ? null : request.sourceSpecId());
        return ResponseEntity.status(added.created() ? HttpStatus.CREATED : HttpStatus.OK).body(added.item());
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<Void> remove(@PathVariable String ticker, @AuthenticationPrincipal Jwt jwt) {
        watchlistService.remove(userId(jwt), ticker);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return authorizationService.getAuthenticatedUserId(jwt);
    }
}
