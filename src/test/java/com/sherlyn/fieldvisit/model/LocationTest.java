package com.sherlyn.fieldvisit.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationTest {

    @Test
    void trimsTextFields() {
        Location l = new Location(" L001 ", " Hebbal Branch ", " North ", Priority.HIGH, 7);
        assertEquals("L001", l.id());
        assertEquals("Hebbal Branch", l.name());
        assertEquals("North", l.zone());
    }

    @Test
    void rejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Location("  ", "Hebbal Branch", "North", Priority.HIGH, 7));
    }

    @Test
    void rejectsNonPositiveFrequency() {
        assertThrows(IllegalArgumentException.class,
                () -> new Location("L001", "Hebbal Branch", "North", Priority.HIGH, 0));
    }

    @Test
    void rejectsNullPriority() {
        assertThrows(NullPointerException.class,
                () -> new Location("L001", "Hebbal Branch", "North", null, 7));
    }

    @Test
    void priorityParsesCaseInsensitively() {
        assertEquals(Priority.HIGH, Priority.fromString("high"));
        assertEquals(Priority.LOW, Priority.fromString(" Low "));
        assertThrows(IllegalArgumentException.class, () -> Priority.fromString("urgent"));
    }

    @Test
    void visitNormalisesNotesAndWorker() {
        Visit v = new Visit("V1", "L001", LocalDate.of(2026, 10, 1), " Priya ", null);
        assertEquals("Priya", v.fieldWorker());
        assertEquals("", v.notes());
    }
}
