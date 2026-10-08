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
import com.sherlyn.fieldvisit.repository.FieldVisitRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * All business rules live here: when a visit is due, which locations are overdue,
 * how a zone is doing, and in what order a field worker should visit locations.
 * The clock is injected so tests can run against a fixed "today".
 */
public class VisitPlannerService {

    /** A location whose next visit is due within this many days is flagged DUE_SOON. */
    public static final int DUE_SOON_WINDOW_DAYS = 3;

    /** Most urgent first, then by business priority, then earliest due date, then id for stable output. */
    private static final Comparator<LocationStatus> ROUTE_ORDER =
            Comparator.comparing(LocationStatus::status)
                    .thenComparing(s -> s.location().priority().rank())
                    .thenComparing(s -> s.nextDue().orElse(LocalDate.MAX))
                    .thenComparing(s -> s.location().id());

    private final FieldVisitRepository repository;
    private final Clock clock;

    public VisitPlannerService(FieldVisitRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public VisitPlannerService(FieldVisitRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------- locations ----------

    public List<Location> allLocations() {
        return repository.findAllLocations().stream()
                .sorted(Comparator.comparing(Location::id))
                .toList();
    }

    public Location getLocation(String id) {
        return repository.findLocationById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));
    }

    public Location addLocation(String id, String name, String zone, Priority priority, int visitFrequencyDays) {
        Location location = new Location(id, name, zone, priority, visitFrequencyDays); // validates fields
        if (repository.findLocationById(location.id()).isPresent()) {
            throw new DuplicateLocationException(location.id());
        }
        repository.saveLocation(location);
        return location;
    }

    public SortedSet<String> zones() {
        return repository.findAllLocations().stream()
                .map(Location::zone)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    // ---------- visits ----------

    public Visit recordVisit(String locationId, LocalDate visitDate, String fieldWorker, String notes) {
        Location location = getLocation(locationId);
        if (visitDate == null) {
            throw new InvalidVisitException("Visit date is required");
        }
        if (visitDate.isAfter(today())) {
            throw new InvalidVisitException("Visit date " + visitDate + " cannot be in the future");
        }
        if (fieldWorker == null || fieldWorker.isBlank()) {
            throw new InvalidVisitException("Field worker name is required");
        }
        Visit visit = new Visit(UUID.randomUUID().toString(), location.id(), visitDate, fieldWorker, notes);
        repository.saveVisit(visit);
        return visit;
    }

    /** Visits for one location, most recent first. */
    public List<Visit> visitHistory(String locationId) {
        getLocation(locationId); // throws if unknown
        return repository.findVisitsByLocation(locationId).stream()
                .sorted(Comparator.comparing(Visit::visitDate).reversed())
                .toList();
    }

    // ---------- reporting ----------

    /** Status of every location, ordered by id. */
    public List<LocationStatus> statusReport() {
        Map<String, LocalDate> lastVisits = latestVisitDates();
        return allLocations().stream()
                .map(l -> toStatus(l, lastVisits.get(l.id())))
                .toList();
    }

    public LocationStatus statusOf(String locationId) {
        Location location = getLocation(locationId);
        LocalDate lastVisit = repository.findVisitsByLocation(locationId).stream()
                .map(Visit::visitDate)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return toStatus(location, lastVisit);
    }

    /** Overdue locations, highest priority first, then the most overdue. */
    public List<LocationStatus> overdueLocations() {
        return statusReport().stream()
                .filter(s -> s.status() == VisitStatus.OVERDUE)
                .sorted(Comparator.comparing((LocationStatus s) -> s.location().priority().rank())
                        .thenComparing(Comparator.comparingLong(LocationStatus::daysOverdue).reversed()))
                .toList();
    }

    /** One summary row per zone, zones in alphabetical order. */
    public Map<String, ZoneSummary> zoneSummaries() {
        Map<String, List<LocationStatus>> byZone = statusReport().stream()
                .collect(Collectors.groupingBy(s -> s.location().zone(), TreeMap::new, Collectors.toList()));

        Map<String, ZoneSummary> result = new TreeMap<>();
        byZone.forEach((zone, statuses) -> result.put(zone, summarise(zone, statuses)));
        return result;
    }

    /** Suggested visiting order for one zone: overdue first, then by priority and due date. */
    public List<LocationStatus> routeForZone(String zone) {
        String wanted = zone == null ? "" : zone.trim();
        List<LocationStatus> route = statusReport().stream()
                .filter(s -> s.location().zone().equalsIgnoreCase(wanted))
                .sorted(ROUTE_ORDER)
                .toList();
        if (route.isEmpty()) {
            throw new IllegalArgumentException("No locations found in zone '" + zone + "'. Known zones: " + zones());
        }
        return route;
    }

    // ---------- helpers ----------

    /** locationId -> most recent visit date, computed in a single pass over all visits. */
    private Map<String, LocalDate> latestVisitDates() {
        return repository.findAllVisits().stream()
                .collect(Collectors.toMap(
                        Visit::locationId,
                        Visit::visitDate,
                        (a, b) -> a.isAfter(b) ? a : b));
    }

    private LocationStatus toStatus(Location location, LocalDate lastVisit) {
        if (lastVisit == null) {
            return new LocationStatus(location, Optional.empty(), Optional.empty(), VisitStatus.NEVER_VISITED, 0);
        }
        LocalDate nextDue = lastVisit.plusDays(location.visitFrequencyDays());
        long daysUntilDue = ChronoUnit.DAYS.between(today(), nextDue);

        VisitStatus status;
        long daysOverdue = 0;
        if (daysUntilDue < 0) {
            status = VisitStatus.OVERDUE;
            daysOverdue = -daysUntilDue;
        } else if (daysUntilDue <= DUE_SOON_WINDOW_DAYS) {
            status = VisitStatus.DUE_SOON;
        } else {
            status = VisitStatus.ON_TRACK;
        }
        return new LocationStatus(location, Optional.of(lastVisit), Optional.of(nextDue), status, daysOverdue);
    }

    private static ZoneSummary summarise(String zone, List<LocationStatus> statuses) {
        Map<VisitStatus, Long> counts = statuses.stream()
                .collect(Collectors.groupingBy(LocationStatus::status, Collectors.counting()));

        long total = statuses.size();
        long overdue = counts.getOrDefault(VisitStatus.OVERDUE, 0L);
        long neverVisited = counts.getOrDefault(VisitStatus.NEVER_VISITED, 0L);
        long dueSoon = counts.getOrDefault(VisitStatus.DUE_SOON, 0L);
        long onTrack = counts.getOrDefault(VisitStatus.ON_TRACK, 0L);
        double coverage = total == 0 ? 0.0 : Math.round((dueSoon + onTrack) * 1000.0 / total) / 10.0;

        return new ZoneSummary(zone, total, overdue, neverVisited, dueSoon, onTrack, coverage);
    }
}
