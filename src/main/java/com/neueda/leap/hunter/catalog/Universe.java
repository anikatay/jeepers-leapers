package com.neueda.leap.hunter.catalog;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The S&P 500 list in src/main/resources/hunter/sp500.csv. It is made by hand from Wikipedia's
 * constituents table and never downloaded by the app; matches are checked against it.
 */
@Component
public class Universe {

    public record Company(String ticker, String name, GicsSector sector) {}

    private static final String RESOURCE = "hunter/sp500.csv";

    private final Map<String, Company> companies;
    private final String asOf;

    public Universe() {
        this(new ClassPathResource(RESOURCE));
    }

    Universe(Resource resource) {
        Map<String, Company> loaded = new LinkedHashMap<>();
        String loadedAsOf = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> header = parseLine(reader.readLine());
            int ticker = header.indexOf("ticker");
            int name = header.indexOf("name");
            int sector = header.indexOf("gics_sector");
            int asOfColumn = header.indexOf("as_of");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseLine(line);
                GicsSector gicsSector = GicsSector.fromLabel(fields.get(sector));
                if (gicsSector == null) {
                    throw new IllegalStateException("Unknown sector in " + RESOURCE + ": " + line);
                }
                loaded.put(fields.get(ticker), new Company(fields.get(ticker), fields.get(name), gicsSector));
                loadedAsOf = fields.get(asOfColumn);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
        this.companies = Collections.unmodifiableMap(loaded);
        this.asOf = loadedAsOf;
    }

    /** Accepts either ticker form: BRK.B and brk-b both find BRK-B. */
    public Optional<Company> find(String ticker) {
        return ticker == null ? Optional.empty() : Optional.ofNullable(companies.get(normalize(ticker)));
    }

    public static String normalize(String ticker) {
        return ticker.strip().toUpperCase(Locale.ROOT).replace('.', '-');
    }

    public int size() {
        return companies.size();
    }

    public String asOf() {
        return asOf;
    }

    /** One CSV line; fields may be quoted, with "" for a quote inside. */
    static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
