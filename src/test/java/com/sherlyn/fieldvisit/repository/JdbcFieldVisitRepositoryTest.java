package com.sherlyn.fieldvisit.repository;

import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.Priority;
import com.sherlyn.fieldvisit.model.Visit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real JDBC code against an in-memory H2 database running in MySQL
 * compatibility mode, so the tests need no MySQL server.
 */
class JdbcFieldVisitRepositoryTest {

    private static final String URL = "jdbc:h2:mem:fieldvisit;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private JdbcFieldVisitRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        repo = new JdbcFieldVisitRepository(URL, "sa", "");
        repo.initSchema();
        try (Connection con = DriverManager.getConnection(URL, "sa", "");
             Statement st = con.createStatement()) {
            st.execute("DELETE FROM visits");
            st.execute("DELETE FROM locations");
        }
    }

    @Test
    void savesAndFindsLocations() {
        Location l = new Location("L001", "Koramangala Branch", "South", Priority.HIGH, 7);
        repo.saveLocation(l);

        assertEquals(List.of(l), repo.findAllLocations());
        assertEquals(l, repo.findLocationById("L001").orElseThrow());
        assertTrue(repo.findLocationById("L999").isEmpty());
    }

    @Test
    void savingExistingIdUpdatesTheRow() {
        repo.saveLocation(new Location("L001", "Old Name", "South", Priority.LOW, 30));
        repo.saveLocation(new Location("L001", "New Name", "North", Priority.HIGH, 7));

        List<Location> all = repo.findAllLocations();
        assertEquals(1, all.size());
        assertEquals("New Name", all.get(0).name());
        assertEquals("North", all.get(0).zone());
        assertEquals(7, all.get(0).visitFrequencyDays());
    }

    @Test
    void savesAndFindsVisitsByLocation() {
        repo.saveLocation(new Location("L001", "Koramangala Branch", "South", Priority.HIGH, 7));
        repo.saveLocation(new Location("L002", "Jayanagar Store", "South", Priority.MEDIUM, 14));
        Visit v1 = new Visit("V1", "L001", LocalDate.of(2026, 10, 1), "Priya", "ok");
        Visit v2 = new Visit("V2", "L001", LocalDate.of(2026, 10, 5), "Arun", "");
        Visit v3 = new Visit("V3", "L002", LocalDate.of(2026, 10, 3), "Kavya", "stock, low");
        repo.saveVisit(v1);
        repo.saveVisit(v2);
        repo.saveVisit(v3);

        assertEquals(List.of(v1, v3, v2), repo.findAllVisits());          // ordered by date
        assertEquals(List.of(v1, v2), repo.findVisitsByLocation("L001"));
        assertEquals(List.of(v3), repo.findVisitsByLocation("L002"));
    }
}
