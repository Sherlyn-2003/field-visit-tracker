package com.sherlyn.fieldvisit.service;

import com.sherlyn.fieldvisit.exception.DuplicateLocationException;
import com.sherlyn.fieldvisit.exception.InvalidVisitException;
import com.sherlyn.fieldvisit.exception.LocationNotFoundException;
import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.LocationStatus;
import com.sherlyn.fieldvisit.model.Priority;
import com.sherlyn.fieldvisit.model.Visit;
import com.sherlyn.fieldvisit.model.VisitStatus;
import com.sherlyn.fieldvisit.report.ZoneSummary;
import com.sherlyn.fieldvisit.repository.InMemoryFieldVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitPlannerServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private InMemoryFieldVisitRepository repo;
    private VisitPlannerService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFieldVisitRepository();
        Clock fixed = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new VisitPlannerService(repo, fixed);

        repo.saveLocation(new Location("L1", "Koramangala Branch", "South", Priority.HIGH, 7));
        repo.saveLocation(new Location("L2", "Jayanagar Store", "South", Priority.MEDIUM, 14));
        repo.saveLocation(new Location("L3", "Hebbal Office", "North", Priority.LOW, 30));
        repo.saveLocation(new Location("L4", "Yelahanka Depot", "North", Priority.HIGH, 7));
    }

    private void visited(String locationId, LocalDate date) {
        repo.saveVisit(new Visit(UUID.randomUUID().toString(), locationId, date, "Priya", ""));
    }

    // ---------- status classification ----------

    @Test
    void neverVisitedWhenNoVisitsExist() {
        LocationStatus s = service.statusOf("L1");
        assertEquals(VisitStatus.NEVER_VISITED, s.status());
        assertTrue(s.lastVisit().isEmpty());
        assertTrue(s.nextDue().isEmpty());
        assertEquals(0, s.daysOverdue());
    }

    @Test
    void overdueWhenDueDateHasPassed() {
        visited("L1", LocalDate.of(2026, 9, 28)); // due Oct 5, today Oct 8
        LocationStatus s = service.statusOf("L1");
        assertEquals(VisitStatus.OVERDUE, s.status());
        assertEquals(LocalDate.of(2026, 10, 5), s.nextDue().orElseThrow());
        assertEquals(3, s.daysOverdue());
    }

    @Test
    void dueSoonWhenWithinWindow() {
        visited("L1", LocalDate.of(2026, 10, 3)); // due Oct 10, 2 days away
        LocationStatus s = service.statusOf("L1");
        assertEquals(VisitStatus.DUE_SOON, s.status());
        assertEquals(0, s.daysOverdue());
    }

    @Test
    void dueSoonOnTheDueDateItself() {
        visited("L1", LocalDate.of(2026, 10, 1)); // due Oct 8 = today
        assertEquals(VisitStatus.DUE_SOON, service.statusOf("L1").status());
    }

    @Test
    void onTrackWhenDueDateIsBeyondWindow() {
        visited("L1", LocalDate.of(2026, 10, 5)); // due Oct 12, 4 days away
        assertEquals(VisitStatus.ON_TRACK, service.statusOf("L1").status());
    }

    @Test
    void usesTheLatestVisitWhenThereAreSeveral() {
        visited("L1", LocalDate.of(2026, 9, 1));
        visited("L1", LocalDate.of(2026, 10, 6));
        LocationStatus s = service.statusOf("L1");
        assertEquals(LocalDate.of(2026, 10, 6), s.lastVisit().orElseThrow());
        assertEquals(VisitStatus.ON_TRACK, s.status());
    }

    // ---------- reports ----------

    @Test
    void overdueListIsSortedByPriorityThenMostOverdue() {
        visited("L2", LocalDate.of(2026, 9, 1));  // MEDIUM, due Sep 15 -> 23 days overdue
        visited("L1", LocalDate.of(2026, 9, 28)); // HIGH,   due Oct 5  ->  3 days overdue
        visited("L4", LocalDate.of(2026, 9, 20)); // HIGH,   due Sep 27 -> 11 days overdue

        List<String> ids = service.overdueLocations().stream()
                .map(s -> s.location().id())
                .toList();

        assertEquals(List.of("L4", "L1", "L2"), ids);
    }

    @Test
    void zoneSummaryCountsEachStatusAndCoverage() {
        visited("L1", LocalDate.of(2026, 9, 28)); // South: OVERDUE
        visited("L2", LocalDate.of(2026, 10, 1)); // South: ON_TRACK (due Oct 15)
        visited("L4", LocalDate.of(2026, 10, 2)); // North: DUE_SOON (due Oct 9); L3 never visited

        Map<String, ZoneSummary> summaries = service.zoneSummaries();
        assertEquals(List.of("North", "South"), List.copyOf(summaries.keySet()));

        ZoneSummary south = summaries.get("South");
        assertEquals(2, south.totalLocations());
        assertEquals(1, south.overdue());
        assertEquals(1, south.onTrack());
        assertEquals(50.0, south.coveragePercent());

        ZoneSummary north = summaries.get("North");
        assertEquals(1, north.neverVisited());
        assertEquals(1, north.dueSoon());
        assertEquals(50.0, north.coveragePercent());
    }

    @Test
    void routeVisitsOverdueFirstThenNeverVisitedThenByPriority() {
        repo.saveLocation(new Location("L5", "BTM Kiosk", "South", Priority.LOW, 30));
        visited("L5", LocalDate.of(2026, 9, 1));  // OVERDUE (due Oct 1)
        visited("L2", LocalDate.of(2026, 10, 1)); // ON_TRACK
        // L1 never visited

        List<String> ids = service.routeForZone("south").stream()
                .map(s -> s.location().id())
                .toList();

        assertEquals(List.of("L5", "L1", "L2"), ids);
    }

    @Test
    void routeForUnknownZoneFails() {
        assertThrows(IllegalArgumentException.class, () -> service.routeForZone("Mars"));
    }

    // ---------- recording visits ----------

    @Test
    void recordVisitStoresVisitAndMovesDueDateForward() {
        Visit visit = service.recordVisit("L1", TODAY, "  Arun ", "Stock checked");

        assertEquals(1, repo.findAllVisits().size());
        assertEquals("Arun", visit.fieldWorker());
        LocationStatus s = service.statusOf("L1");
        assertEquals(VisitStatus.ON_TRACK, s.status());
        assertEquals(TODAY.plusDays(7), s.nextDue().orElseThrow());
    }

    @Test
    void recordVisitRejectsFutureDate() {
        assertThrows(InvalidVisitException.class,
                () -> service.recordVisit("L1", TODAY.plusDays(1), "Arun", ""));
    }

    @Test
    void recordVisitRejectsUnknownLocation() {
        assertThrows(LocationNotFoundException.class,
                () -> service.recordVisit("L999", TODAY, "Arun", ""));
    }

    @Test
    void recordVisitRejectsBlankWorker() {
        assertThrows(InvalidVisitException.class,
                () -> service.recordVisit("L1", TODAY, "   ", ""));
    }

    // ---------- adding locations ----------

    @Test
    void addLocationRejectsDuplicateId() {
        assertThrows(DuplicateLocationException.class,
                () -> service.addLocation("L1", "Another", "South", Priority.LOW, 10));
    }

    @Test
    void addLocationRejectsInvalidFrequency() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addLocation("L9", "Bad", "South", Priority.LOW, 0));
    }

    @Test
    void addLocationSavesAndAppearsInListing() {
        service.addLocation("L9", "Whitefield Hub", "East", Priority.MEDIUM, 14);
        assertEquals(5, service.allLocations().size());
        assertTrue(service.zones().contains("East"));
    }
}
