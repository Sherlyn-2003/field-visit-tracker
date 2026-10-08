package com.sherlyn.fieldvisit.model;

/**
 * Where a location stands against its visit schedule.
 * Declared in order of urgency so that {@link #compareTo} sorts the most urgent first.
 */
public enum VisitStatus {
    OVERDUE,
    NEVER_VISITED,
    DUE_SOON,
    ON_TRACK
}
