package com.sherlyn.fieldvisit.repository;

import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.Visit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps everything in collections. Used directly in unit tests and as the
 * base class for {@link CsvFieldVisitRepository}, which adds file persistence.
 */
public class InMemoryFieldVisitRepository implements FieldVisitRepository {

    /** LinkedHashMap keeps insertion order so listings are stable. */
    protected final Map<String, Location> locations = new LinkedHashMap<>();
    protected final List<Visit> visits = new ArrayList<>();

    @Override
    public List<Location> findAllLocations() {
        return new ArrayList<>(locations.values());
    }

    @Override
    public Optional<Location> findLocationById(String id) {
        return Optional.ofNullable(locations.get(id));
    }

    @Override
    public void saveLocation(Location location) {
        locations.put(location.id(), location);
    }

    @Override
    public List<Visit> findAllVisits() {
        return new ArrayList<>(visits);
    }

    @Override
    public List<Visit> findVisitsByLocation(String locationId) {
        return visits.stream()
                .filter(v -> v.locationId().equals(locationId))
                .toList();
    }

    @Override
    public void saveVisit(Visit visit) {
        visits.add(visit);
    }
}
