package com.sherlyn.fieldvisit.model;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A computed, read-only view of one location's schedule position on a given day.
 *
 * @param location    the location being reported on
 * @param lastVisit   most recent visit date, empty if never visited
 * @param nextDue     date the next visit is due, empty if never visited
 * @param status      urgency bucket
 * @param daysOverdue how many days past the due date (0 when not overdue)
 */
public record LocationStatus(Location location,
                             Optional<LocalDate> lastVisit,
                             Optional<LocalDate> nextDue,
                             VisitStatus status,
                             long daysOverdue) {
}
