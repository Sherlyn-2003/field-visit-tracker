package com.sherlyn.fieldvisit.repository;

import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.Priority;
import com.sherlyn.fieldvisit.model.Visit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JDBC implementation backed by MySQL (or any SQL database with the same schema).
 * Uses PreparedStatements for every query and try-with-resources so connections are always closed.
 */
public class JdbcFieldVisitRepository implements FieldVisitRepository {

    private static final String SELECT_LOCATIONS =
            "SELECT id, name, zone, priority, visit_frequency_days FROM locations";
    private static final String SELECT_VISITS =
            "SELECT id, location_id, visit_date, field_worker, notes FROM visits";

    private final String url;
    private final String user;
    private final String password;

    public JdbcFieldVisitRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Creates the tables if they do not exist, using src/main/resources/schema.sql. */
    public void initSchema() {
        String script = readSchema();
        try (Connection con = connect(); Statement st = con.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) {
                    st.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not initialise schema", e);
        }
    }

    /** Reads schema.sql from the classpath, dropping "--" comment lines so they cannot confuse the splitter. */
    private static String readSchema() {
        try (InputStream in = JdbcFieldVisitRepository.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new RepositoryException("schema.sql not found on classpath", null);
            }
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return raw.lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RepositoryException("Could not read schema.sql", e);
        }
    }

    // ---------- locations ----------

    @Override
    public List<Location> findAllLocations() {
        List<Location> result = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(SELECT_LOCATIONS + " ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapLocation(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load locations", e);
        }
        return result;
    }

    @Override
    public Optional<Location> findLocationById(String id) {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(SELECT_LOCATIONS + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapLocation(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load location " + id, e);
        }
    }

    @Override
    public void saveLocation(Location l) {
        try (Connection con = connect()) {
            // Try an UPDATE first; if no row changed, INSERT. Portable across MySQL and H2.
            try (PreparedStatement update = con.prepareStatement(
                    "UPDATE locations SET name = ?, zone = ?, priority = ?, visit_frequency_days = ? WHERE id = ?")) {
                update.setString(1, l.name());
                update.setString(2, l.zone());
                update.setString(3, l.priority().name());
                update.setInt(4, l.visitFrequencyDays());
                update.setString(5, l.id());
                if (update.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement insert = con.prepareStatement(
                    "INSERT INTO locations (id, name, zone, priority, visit_frequency_days) VALUES (?, ?, ?, ?, ?)")) {
                insert.setString(1, l.id());
                insert.setString(2, l.name());
                insert.setString(3, l.zone());
                insert.setString(4, l.priority().name());
                insert.setInt(5, l.visitFrequencyDays());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not save location " + l.id(), e);
        }
    }

    // ---------- visits ----------

    @Override
    public List<Visit> findAllVisits() {
        List<Visit> result = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(SELECT_VISITS + " ORDER BY visit_date, id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapVisit(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load visits", e);
        }
        return result;
    }

    @Override
    public List<Visit> findVisitsByLocation(String locationId) {
        List<Visit> result = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(SELECT_VISITS + " WHERE location_id = ? ORDER BY visit_date")) {
            ps.setString(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapVisit(rs));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load visits for " + locationId, e);
        }
        return result;
    }

    @Override
    public void saveVisit(Visit v) {
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO visits (id, location_id, visit_date, field_worker, notes) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, v.id());
            ps.setString(2, v.locationId());
            ps.setDate(3, Date.valueOf(v.visitDate()));
            ps.setString(4, v.fieldWorker());
            ps.setString(5, v.notes());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Could not save visit " + v.id(), e);
        }
    }

    // ---------- row mappers ----------

    private static Location mapLocation(ResultSet rs) throws SQLException {
        return new Location(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("zone"),
                Priority.fromString(rs.getString("priority")),
                rs.getInt("visit_frequency_days"));
    }

    private static Visit mapVisit(ResultSet rs) throws SQLException {
        return new Visit(
                rs.getString("id"),
                rs.getString("location_id"),
                rs.getDate("visit_date").toLocalDate(),
                rs.getString("field_worker"),
                rs.getString("notes"));
    }
}
