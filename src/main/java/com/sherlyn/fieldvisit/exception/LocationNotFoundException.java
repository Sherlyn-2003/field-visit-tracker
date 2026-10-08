package com.sherlyn.fieldvisit.exception;

/** Thrown when an operation refers to a location id that does not exist. */
public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(String locationId) {
        super("No location found with id '" + locationId + "'");
    }
}
