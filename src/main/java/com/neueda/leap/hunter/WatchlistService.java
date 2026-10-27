package com.neueda.leap.hunter;

import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.hunter.catalog.GicsSector;
import com.neueda.leap.hunter.catalog.Universe;
import com.neueda.leap.mapper.HunterSpecMapper;
import com.neueda.leap.mapper.WatchlistMapper;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A list of tickers per user (section 7 of the spec). It never calls the model. */
@Service
public class WatchlistService {

    public record Item(
            String ticker,
            String companyName,
            GicsSector gicsSector,
            OffsetDateTime addedAt,
            SourceSpec sourceSpec) {}

    public record SourceSpec(UUID id, String name) {}

    /** created is false when the ticker was already on the list. */
    public record Added(Item item, boolean created) {}

    private final WatchlistMapper watchlistMapper;
    private final HunterSpecMapper specMapper;
    private final Universe universe;
    private final Clock clock;

    public WatchlistService(WatchlistMapper watchlistMapper, HunterSpecMapper specMapper, Universe universe,
            Clock clock) {
        this.watchlistMapper = watchlistMapper;
        this.specMapper = specMapper;
        this.universe = universe;
        this.clock = clock;
    }

    /** Newest first; name and sector are null for a ticker that has since left the universe list. */
    public List<Item> list(UUID userId) {
        return watchlistMapper.findAll(userId).stream()
                .map(row -> {
                    Optional<Universe.Company> company = universe.find(row.getTicker());
                    return new Item(row.getTicker(),
                            company.map(Universe.Company::name).orElse(null),
                            company.map(Universe.Company::sector).orElse(null),
                            row.getAddedAt(),
                            row.getSourceSpecId() == null ? null
                                    : new SourceSpec(row.getSourceSpecId(), row.getSourceSpecName()));
                })
                .toList();
    }

    public Added add(UUID userId, String ticker, UUID sourceSpecId) {
        Universe.Company company = universe.find(ticker)
                .orElseThrow(() -> new ObjectNotFoundException("Not in the S&P 500 list: " + ticker));
        String symbol = company.ticker();
        if (watchlistMapper.exists(userId, symbol)) {
            return new Added(find(userId, symbol), false);
        }
        String sourceName = null;
        if (sourceSpecId != null) {
            var spec = specMapper.findOwned(sourceSpecId, userId);
            if (spec == null) {
                throw new ObjectNotFoundException("Hunter not found: " + sourceSpecId);
            }
            sourceName = spec.getName();
        }
        if (watchlistMapper.countByUser(userId) >= HunterLimits.MAX_WATCHLIST_ITEMS) {
            throw new LimitReachedException(
                    "Your watchlist can hold at most " + HunterLimits.MAX_WATCHLIST_ITEMS + " stocks");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (watchlistMapper.insert(userId, symbol, now, sourceSpecId) == 0) {
            // Added by a concurrent request in the meantime
            return new Added(find(userId, symbol), false);
        }
        return new Added(new Item(symbol, company.name(), company.sector(), now,
                sourceSpecId == null ? null : new SourceSpec(sourceSpecId, sourceName)), true);
    }

    /** Removing a ticker that is not on the list is not an error. */
    public void remove(UUID userId, String ticker) {
        watchlistMapper.delete(userId, Universe.normalize(ticker));
    }

    private Item find(UUID userId, String ticker) {
        return list(userId).stream().filter(item -> item.ticker().equals(ticker)).findFirst()
                .orElseThrow(() -> new ObjectNotFoundException("Not on the watchlist: " + ticker));
    }
}
