package com.sherlyn.fieldvisit.repository;

import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.Priority;
import com.sherlyn.fieldvisit.model.Visit;
import com.sherlyn.fieldvisit.util.CsvUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * File-backed repository. Loads {@code locations.csv} and {@code visits.csv}
 * from a data directory on start-up and rewrites the relevant file after every save.
 */
public class CsvFieldVisitRepository extends InMemoryFieldVisitRepository {

    static final String LOCATIONS_HEADER = "id,name,zone,priority,visit_frequency_days";
    static final String VISITS_HEADER = "id,location_id,visit_date,field_worker,notes";

    private final Path dataDir;
    private final Path locationsFile;
    private final Path visitsFile;

    public CsvFieldVisitRepository(Path dataDir) {
        this.dataDir = dataDir;
        this.locationsFile = dataDir.resolve("locations.csv");
        this.visitsFile = dataDir.resolve("visits.csv");
        load();
    }

    public Path dataDir() {
        return dataDir;
    }

    @Override
    public void saveLocation(Location location) {
        super.saveLocation(location);
        writeLocations();
    }

    @Override
    public void saveVisit(Visit visit) {
        super.saveVisit(visit);
        writeVisits();
    }

    // ---------- loading ----------

    private void load() {
        for (String line : readDataLines(locationsFile)) {
            List<String> f = CsvUtil.parseLine(line);
            if (f.size() < 5) {
                throw new RepositoryException("Malformed location row: " + line, null);
            }
            Location location = new Location(f.get(0), f.get(1), f.get(2),
                    Priority.fromString(f.get(3)), Integer.parseInt(f.get(4).trim()));
            super.saveLocation(location);
        }
        for (String line : readDataLines(visitsFile)) {
            List<String> f = CsvUtil.parseLine(line);
            if (f.size() < 4) {
                throw new RepositoryException("Malformed visit row: " + line, null);
            }
            String notes = f.size() > 4 ? f.get(4) : "";
            Visit visit = new Visit(f.get(0), f.get(1), LocalDate.parse(f.get(2).trim()), f.get(3), notes);
            super.saveVisit(visit);
        }
    }

    /** Returns all non-blank lines after the header, or an empty list when the file does not exist yet. */
    private static List<String> readDataLines(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.stream()
                    .skip(1)                       // header row
                    .filter(l -> !l.isBlank())
                    .toList();
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + file, e);
        }
    }

    // ---------- writing ----------

    private void writeLocations() {
        List<String> lines = new ArrayList<>();
        lines.add(LOCATIONS_HEADER);
        for (Location l : locations.values()) {
            lines.add(CsvUtil.toLine(l.id(), l.name(), l.zone(), l.priority().name(),
                    String.valueOf(l.visitFrequencyDays())));
        }
        writeAll(locationsFile, lines);
    }

    private void writeVisits() {
        List<String> lines = new ArrayList<>();
        lines.add(VISITS_HEADER);
        for (Visit v : visits) {
            lines.add(CsvUtil.toLine(v.id(), v.locationId(), v.visitDate().toString(), v.fieldWorker(), v.notes()));
        }
        writeAll(visitsFile, lines);
    }

    private void writeAll(Path file, List<String> lines) {
        try {
            Files.createDirectories(dataDir);
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RepositoryException("Could not write " + file, e);
        }
    }
}
