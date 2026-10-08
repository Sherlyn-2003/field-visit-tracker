package com.sherlyn.fieldvisit.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single completed visit to a location by a field worker.
 *
 * @param id          unique identifier (UUID string)
 * @param locationId  the {@link Location#id()} that was visited
 * @param visitDate   the date of the visit
 * @param fieldWorker name of the person who made the visit
 * @param notes       optional free-text remarks (may be empty, never null)
 */
public record Visit(String id, String locationId, LocalDate visitDate, String fieldWorker, String notes) {

    public Visit {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (locationId == null || locationId.isBlank()) {
            throw new IllegalArgumentException("locationId must not be blank");
        }
        Objects.requireNonNull(visitDate, "visitDate must not be null");
        if (fieldWorker == null || fieldWorker.isBlank()) {
            throw new IllegalArgumentException("fieldWorker must not be blank");
        }
        notes = notes == null ? "" : notes.trim();
        fieldWorker = fieldWorker.trim();
    }
}
