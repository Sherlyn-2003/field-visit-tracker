package com.sherlyn.fieldvisit.model;

/**
 * Business priority of a location. The rank is used for sorting:
 * lower rank = more important = visited first.
 */
public enum Priority {
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    private final int rank;

    Priority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** Case-insensitive parse so CSV / console input like "high" also works. */
    public static Priority fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Priority must not be blank");
        }
        try {
            return Priority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown priority '" + value.trim() + "'. Use HIGH, MEDIUM or LOW");
        }
    }
}
