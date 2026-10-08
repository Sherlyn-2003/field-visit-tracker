package com.sherlyn.fieldvisit.repository;

import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.Visit;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction for locations and visits.
 * The service layer depends only on this interface, so the storage can be
 * swapped between CSV files, an in-memory map (tests) or a MySQL database.
 */
public interface FieldVisitRepository {

    List<Location> findAllLocations();

    Optional<Location> findLocationById(String id);

    /** Inserts the location, or replaces the existing one with the same id. */
    void saveLocation(Location location);

    List<Visit> findAllVisits();

    List<Visit> findVisitsByLocation(String locationId);

    void saveVisit(Visit visit);
}
