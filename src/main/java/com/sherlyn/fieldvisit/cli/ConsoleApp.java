package com.sherlyn.fieldvisit.cli;

import com.sherlyn.fieldvisit.exception.DuplicateLocationException;
import com.sherlyn.fieldvisit.exception.InvalidVisitException;
import com.sherlyn.fieldvisit.exception.LocationNotFoundException;
import com.sherlyn.fieldvisit.model.Location;
import com.sherlyn.fieldvisit.model.LocationStatus;
import com.sherlyn.fieldvisit.model.Priority;
import com.sherlyn.fieldvisit.model.Visit;
import com.sherlyn.fieldvisit.report.ZoneSummary;
import com.sherlyn.fieldvisit.repository.RepositoryException;
import com.sherlyn.fieldvisit.service.VisitPlannerService;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * Menu-driven text interface. Input and output streams are injected so the
 * whole menu can be driven from a test with scripted input.
 */
public class ConsoleApp {

    private static final String ROW_FORMAT = "%-6s %-30s %-8s %-8s %-11s %-11s %-14s %s%n";

    private final VisitPlannerService service;
    private final Scanner in;
    private final PrintStream out;

    public ConsoleApp(VisitPlannerService service, InputStream input, PrintStream out) {
        this.service = service;
        this.in = new Scanner(input, StandardCharsets.UTF_8);
        this.out = out;
    }

    public void run() {
        out.println();
        out.println("=== Field Visit Tracker ===   (today: " + service.today() + ")");
        boolean running = true;
        while (running) {
            printMenu();
            String choice = prompt("Choose an option");
            try {
                switch (choice) {
                    case "1" -> showAllLocations();
                    case "2" -> showOverdue();
                    case "3" -> recordVisit();
                    case "4" -> showZoneSummary();
                    case "5" -> planRoute();
                    case "6" -> addLocation();
                    case "7" -> showHistory();
                    case "0" -> running = false;
                    default -> out.println("Unknown option '" + choice + "'. Please enter 0-7.");
                }
            } catch (LocationNotFoundException | InvalidVisitException | DuplicateLocationException
                     | IllegalArgumentException | DateTimeParseException e) {
                out.println("! " + e.getMessage());
            } catch (RepositoryException e) {
                out.println("! Storage error: " + e.getMessage());
            }
        }
        out.println("Goodbye.");
    }

    // ---------- menu actions ----------

    private void printMenu() {
        out.println();
        out.println("1. List all locations with status");
        out.println("2. Show overdue locations");
        out.println("3. Record a visit");
        out.println("4. Zone coverage summary");
        out.println("5. Plan route for a zone");
        out.println("6. Add a new location");
        out.println("7. Visit history for a location");
        out.println("0. Exit");
    }

    private void showAllLocations() {
        printStatusTable(service.statusReport());
    }

    private void showOverdue() {
        List<LocationStatus> overdue = service.overdueLocations();
        if (overdue.isEmpty()) {
            out.println("No overdue locations. Everything is on schedule.");
            return;
        }
        out.println("Overdue locations (highest priority first):");
        printStatusTable(overdue);
    }

    private void recordVisit() {
        String locationId = prompt("Location id");
        Location location = service.getLocation(locationId);
        out.println("  -> " + location.name() + " [" + location.zone() + ", " + location.priority() + "]");

        String dateText = prompt("Visit date (YYYY-MM-DD, blank = today)");
        LocalDate date = dateText.isBlank() ? service.today() : LocalDate.parse(dateText);
        String worker = prompt("Field worker name");
        String notes = prompt("Notes (optional)");

        Visit visit = service.recordVisit(locationId, date, worker, notes);
        LocationStatus status = service.statusOf(locationId);
        out.println("Recorded visit to " + location.name() + " on " + visit.visitDate()
                + " by " + visit.fieldWorker() + ". Next visit due: "
                + status.nextDue().map(LocalDate::toString).orElse("-"));
    }

    private void showZoneSummary() {
        out.printf("%-10s %6s %8s %8s %8s %8s %10s%n",
                "Zone", "Total", "Overdue", "Never", "DueSoon", "OnTrack", "Coverage");
        out.println("-".repeat(64));
        for (ZoneSummary z : service.zoneSummaries().values()) {
            out.printf("%-10s %6d %8d %8d %8d %8d %9.1f%%%n",
                    z.zone(), z.totalLocations(), z.overdue(), z.neverVisited(),
                    z.dueSoon(), z.onTrack(), z.coveragePercent());
        }
    }

    private void planRoute() {
        out.println("Zones: " + String.join(", ", service.zones()));
        String zone = prompt("Zone");
        List<LocationStatus> route = service.routeForZone(zone);
        out.println("Suggested route for zone '" + zone + "' on " + service.today() + ":");
        int stop = 1;
        for (LocationStatus s : route) {
            Location l = s.location();
            String due = s.nextDue().map(d -> "due " + d).orElse("no visit yet");
            String overdue = s.daysOverdue() > 0 ? " (" + s.daysOverdue() + " days overdue)" : "";
            out.printf("%2d. %-5s %-30s %-7s %-14s %s%s%n",
                    stop++, l.id(), truncate(l.name(), 30), l.priority(), s.status(), due, overdue);
        }
    }

    private void addLocation() {
        String id = prompt("New location id (e.g. L041)");
        String name = prompt("Name");
        String zone = prompt("Zone");
        Priority priority = Priority.fromString(prompt("Priority (HIGH / MEDIUM / LOW)"));
        int frequency = Integer.parseInt(prompt("Visit frequency in days"));
        Location saved = service.addLocation(id, name, zone, priority, frequency);
        out.println("Added " + saved.id() + " - " + saved.name() + " (" + saved.zone() + ", "
                + saved.priority() + ", every " + saved.visitFrequencyDays() + " days)");
    }

    private void showHistory() {
        String locationId = prompt("Location id");
        Location location = service.getLocation(locationId);
        List<Visit> history = service.visitHistory(locationId);
        out.println("Visit history for " + location.name() + " (" + history.size() + " visit(s)):");
        if (history.isEmpty()) {
            out.println("  No visits recorded yet.");
        }
        for (Visit v : history) {
            out.printf("  %s  %-15s %s%n", v.visitDate(), v.fieldWorker(), v.notes());
        }
    }

    // ---------- helpers ----------

    private void printStatusTable(List<LocationStatus> rows) {
        out.printf(ROW_FORMAT, "ID", "Name", "Zone", "Priority", "Last Visit", "Next Due", "Status", "Overdue(d)");
        out.println("-".repeat(104));
        for (LocationStatus s : rows) {
            Location l = s.location();
            out.printf(ROW_FORMAT,
                    l.id(),
                    truncate(l.name(), 30),
                    l.zone(),
                    l.priority(),
                    s.lastVisit().map(LocalDate::toString).orElse("-"),
                    s.nextDue().map(LocalDate::toString).orElse("-"),
                    s.status(),
                    s.daysOverdue() > 0 ? String.valueOf(s.daysOverdue()) : "");
        }
        out.println(rows.size() + " location(s)");
    }

    private String prompt(String label) {
        out.print(label + ": ");
        out.flush();
        if (!in.hasNextLine()) {
            return "0"; // input finished (e.g. piped file) -> behave like "Exit"
        }
        return in.nextLine().trim();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }
}
