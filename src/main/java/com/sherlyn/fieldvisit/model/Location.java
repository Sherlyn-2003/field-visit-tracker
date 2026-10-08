package com.sherlyn.fieldvisit.model;

import java.util.Objects;

/**
 * A business location that field workers must visit regularly.
 *
 * @param id                 short unique code, e.g. "L001"
 * @param name               human-readable name, e.g. "Koramangala Branch"
 * @param zone               territory the location belongs to, e.g. "South"
 * @param priority           business priority used for route ordering
 * @param visitFrequencyDays how often (in days) the location should be visited
 */
public record Location(String id, String name, String zone, Priority priority, int visitFrequencyDays) {

    /** Compact constructor: validates every field so an invalid Location can never exist. */
    public Location {
        requireText(id, "id");
        requireText(name, "name");
        requireText(zone, "zone");
        Objects.requireNonNull(priority, "priority must not be null");
        if (visitFrequencyDays <= 0) {
            throw new IllegalArgumentException("visitFrequencyDays must be positive, got " + visitFrequencyDays);
        }
        id = id.trim();
        name = name.trim();
        zone = zone.trim();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
