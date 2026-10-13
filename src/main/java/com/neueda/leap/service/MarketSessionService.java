package com.neueda.leap.service;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.neueda.leap.exception.MarketClosedException;
import com.neueda.leap.exception.ObjectNotFoundException;
import com.neueda.leap.mapper.ExchangeMapper;
import com.neueda.leap.model.Exchange;
import com.neueda.leap.model.Instrument;

@Service
public class MarketSessionService {

    private static final Set<String> SUPPORTED_US_EXCHANGES = Set.of("NYSE", "NASDAQ");
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);

    private final ExchangeMapper exchangeMapper;
    private final Clock clock;

    public MarketSessionService(ExchangeMapper exchangeMapper, Clock clock) {
        this.exchangeMapper = exchangeMapper;
        this.clock = clock;
    }

    public void assertMarketOpen(Instrument instrument) {
        Exchange exchange = exchangeMapper.findById(instrument.getExchange())
                .orElseThrow(() ->
                        new ObjectNotFoundException(
                                "Exchange not found: " + instrument.getExchange()));

        String exchangeId = exchange.getExchangeId().toUpperCase(Locale.ROOT);
        if("CRYPTO".equals(exchangeId)){
            return;
        }
        if (!SUPPORTED_US_EXCHANGES.contains(exchangeId)
                || !"USA".equalsIgnoreCase(exchange.getRegion())) {
            throw new MarketClosedException(
                    "Unable to make trade at the moment; trading schedule is unavailable for "
                            + exchange.getExchangeId());
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(exchange.getTimezone());
        } catch (DateTimeException e) {
            throw new MarketClosedException(
                    "Unable to make trade at the moment; exchange timezone is invalid");
        }
        ZonedDateTime now = clock.instant().atZone(zone);

        if (isWeekend(now) || isHoliday(exchange, now)
                || !isWithinRegularSession(exchange, now)) {
            throw new MarketClosedException(
                    "Unable to make trade at the moment; market is closed");
        }
    }

    private boolean isWeekend(ZonedDateTime now) {
        return now.getDayOfWeek() == DayOfWeek.SATURDAY
                || now.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private boolean isWithinRegularSession(
            Exchange exchange,
            ZonedDateTime now) {
        LocalTime current = now.toLocalTime();
        return !current.isBefore(REGULAR_OPEN) && current.isBefore(REGULAR_CLOSE);
    }

    private boolean isHoliday(
            Exchange exchange,
            ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        int year = date.getYear();

        return isObservedFixedHoliday(date, Month.JANUARY, 1)
                || date.equals(nthWeekday(year, Month.JANUARY, DayOfWeek.MONDAY, 3))
                || date.equals(nthWeekday(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3))
                || date.equals(easterSunday(year).minusDays(2))
                || date.equals(lastWeekday(year, Month.MAY, DayOfWeek.MONDAY))
                || (year >= 2022 && isObservedFixedHoliday(date, Month.JUNE, 19))
                || isObservedFixedHoliday(date, Month.JULY, 4)
                || date.equals(nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1))
                || date.equals(nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4))
                || isObservedFixedHoliday(date, Month.DECEMBER, 25);
    }

    private boolean isObservedFixedHoliday(LocalDate date, Month month, int dayOfMonth) {
        for (int year = date.getYear() - 1; year <= date.getYear() + 1; year++) {
            LocalDate holiday = LocalDate.of(year, month, dayOfMonth);
            LocalDate observed = switch (holiday.getDayOfWeek()) {
                case SATURDAY -> holiday.minusDays(1);
                case SUNDAY -> holiday.plusDays(1);
                default -> holiday;
            };
            if (date.equals(observed)) {
                return true;
            }
        }
        return false;
    }

    private LocalDate nthWeekday(
            int year,
            Month month,
            DayOfWeek dayOfWeek,
            int occurrence) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(occurrence, dayOfWeek));
    }

    private LocalDate lastWeekday(
            int year,
            Month month,
            DayOfWeek dayOfWeek) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.lastInMonth(dayOfWeek));
    }

    private LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}