package com.sherlyn.fieldvisit.repository;

import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.Priority;
import com.sherlyn.fieldvisit.model.Visit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvFieldVisitRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void startsEmptyWhenFilesDoNotExist() {
        CsvFieldVisitRepository repo = new CsvFieldVisitRepository(tempDir);
        assertTrue(repo.findAllLocations().isEmpty());
        assertTrue(repo.findAllVisits().isEmpty());
    }

    @Test
    void savesToDiskAndReloadsInANewInstance() {
        CsvFieldVisitRepository first = new CsvFieldVisitRepository(tempDir);
        Location location = new Location("L001", "Koramangala Branch", "South", Priority.HIGH, 7);
        Visit visit = new Visit("V001", "L001", LocalDate.of(2026, 10, 1), "Priya",
                "Met manager, \"urgent\" restock needed");
        first.saveLocation(location);
        first.saveVisit(visit);

        assertTrue(Files.exists(tempDir.resolve("locations.csv")));
        assertTrue(Files.exists(tempDir.resolve("visits.csv")));

        CsvFieldVisitRepository second = new CsvFieldVisitRepository(tempDir);
        assertEquals(List.of(location), second.findAllLocations());
        assertEquals(List.of(visit), second.findAllVisits()); // commas and quotes survive the round-trip
    }

    @Test
    void savingSameLocationIdReplacesIt() {
        CsvFieldVisitRepository repo = new CsvFieldVisitRepository(tempDir);
        repo.saveLocation(new Location("L001", "Old Name", "South", Priority.LOW, 30));
        repo.saveLocation(new Location("L001", "New Name", "South", Priority.HIGH, 7));

        CsvFieldVisitRepository reloaded = new CsvFieldVisitRepository(tempDir);
        assertEquals(1, reloaded.findAllLocations().size());
        assertEquals("New Name", reloaded.findLocationById("L001").orElseThrow().name());
    }

    @Test
    void sampleDataShippedWithProjectLoadsCleanly() {
        CsvFieldVisitRepository repo = new CsvFieldVisitRepository(Path.of("data"));
        assertEquals(40, repo.findAllLocations().size());
        long zones = repo.findAllLocations().stream().map(Location::zone).distinct().count();
        assertEquals(5, zones);
        assertTrue(repo.findAllVisits().size() > 30);
    }
}
