package com.sherlyn.fieldvisit.exception;

/** Thrown when a visit cannot be recorded because its data breaks a business rule. */
public class InvalidVisitException extends RuntimeException {

    public InvalidVisitException(String message) {
        super(message);
    }
}
