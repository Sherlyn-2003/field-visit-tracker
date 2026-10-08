package com.sherlyn.fieldvisit;

import com.sherlyn.fieldvisit.cli.ConsoleApp;
import com.sherlyn.fieldvisit.repository.CsvFieldVisitRepository;
import com.sherlyn.fieldvisit.repository.FieldVisitRepository;
import com.sherlyn.fieldvisit.repository.JdbcFieldVisitRepository;
import com.sherlyn.fieldvisit.service.VisitPlannerService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry point. Wires the chosen storage (CSV by default, MySQL with --jdbc)
 * into the service and starts the console menu.
 *
 * <pre>
 *   java -jar target/field-visit-tracker.jar
 *   java -jar target/field-visit-tracker.jar --data=data --today=2026-10-08
 *   java -jar target/field-visit-tracker.jar --jdbc=jdbc:mysql://localhost:3306/fieldvisit --user=root --password=secret
 * </pre>
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Map<String, String> opts;
        try {
            opts = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printHelp();
            System.exit(1);
            return;
        }
        if (opts.containsKey("help")) {
            printHelp();
            return;
        }

        Clock clock = opts.containsKey("today") ? fixedClock(opts.get("today")) : Clock.systemDefaultZone();
        FieldVisitRepository repository = createRepository(opts);
        VisitPlannerService service = new VisitPlannerService(repository, clock);

        new ConsoleApp(service, System.in, System.out).run();
    }

    private static FieldVisitRepository createRepository(Map<String, String> opts) {
        if (opts.containsKey("jdbc")) {
            JdbcFieldVisitRepository jdbc = new JdbcFieldVisitRepository(
                    opts.get("jdbc"),
                    opts.getOrDefault("user", "root"),
                    opts.getOrDefault("password", ""));
            jdbc.initSchema();
            System.out.println("Storage: MySQL via JDBC (" + opts.get("jdbc") + ")");
            return jdbc;
        }
        Path dataDir = Path.of(opts.getOrDefault("data", "data"));
        System.out.println("Storage: CSV files in " + dataDir.toAbsolutePath());
        return new CsvFieldVisitRepository(dataDir);
    }

    private static Clock fixedClock(String isoDate) {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(LocalDate.parse(isoDate).atStartOfDay(zone).toInstant(), zone);
    }

    /** Turns {@code --key=value} and {@code --flag} arguments into a map. */
    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq < 0) {
                opts.put(body, "true");
            } else {
                opts.put(body.substring(0, eq), body.substring(eq + 1));
            }
        }
        return opts;
    }

    private static void printHelp() {
        System.out.println("""
                Field Visit Tracker

                Options:
                  --data=<dir>        Folder containing locations.csv and visits.csv (default: data)
                  --jdbc=<url>        Use MySQL instead of CSV, e.g. jdbc:mysql://localhost:3306/fieldvisit
                  --user=<name>       Database user (default: root)
                  --password=<pw>     Database password (default: empty)
                  --today=YYYY-MM-DD  Pretend today is this date (useful for demos and screenshots)
                  --help              Show this message
                """);
    }
}
