package com.sherlyn.fieldvisit.repository;

/** Unchecked wrapper for I/O or SQL failures so callers are not forced to handle checked exceptions. */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
