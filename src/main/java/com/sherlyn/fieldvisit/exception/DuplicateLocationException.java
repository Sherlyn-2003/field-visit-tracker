package com.sherlyn.fieldvisit.exception;

/** Thrown when a new location is added with an id that is already in use. */
public class DuplicateLocationException extends RuntimeException {

    public DuplicateLocationException(String locationId) {
        super("A location with id '" + locationId + "' already exists");
    }
}
