# Field Visit Tracker

A console application written in **Core Java 21** that helps a field-operations team keep
40 business locations across 5 zones on their visit schedule. It flags overdue locations,
summarises coverage per zone, suggests a visiting order for a field worker, and records
completed visits.

It is a Java re-implementation of an Excel field-visit planner, rebuilt with proper
object-oriented design, a layered architecture, unit tests and pluggable storage
(CSV files by default, MySQL via JDBC optionally).

## Features

| Menu option | What it does |
|---|---|
| 1. List all locations with status | Every location with last visit, next due date and status (`OVERDUE`, `NEVER_VISITED`, `DUE_SOON`, `ON_TRACK`) |
| 2. Show overdue locations | Only overdue locations, highest priority first, then most overdue |
| 3. Record a visit | Validates the location, date (no future dates) and worker name, then saves |
| 4. Zone coverage summary | Per-zone counts of each status and a coverage percentage |
| 5. Plan route for a zone | Suggested visiting order: overdue, never visited, due soon, on track; ties broken by priority and due date |
| 6. Add a new location | Validates id uniqueness, priority and visit frequency |
| 7. Visit history for a location | All visits for one location, most recent first |

Business rules:

- Next due date = last visit date + the location's visit frequency (HIGH every 7 days, MEDIUM 14, LOW 30 in the sample data).
- A location is `DUE_SOON` when the due date is within the next 3 days (including today).
- Coverage % for a zone = locations that are `ON_TRACK` or `DUE_SOON` / total locations.

## Tech stack

- Java 21 (records, switch expressions, text blocks, streams, `java.time`, `Optional`)
- Maven (build, test, runnable "fat" jar)
- JUnit 5 (30 unit tests)
- JDBC with MySQL driver; H2 in-memory database used in tests
- No frameworks: plain Java so every line is easy to explain

## Project structure

```
src/main/java/com/sherlyn/fieldvisit
├── App.java                      entry point, parses --data / --jdbc / --today arguments
├── cli/ConsoleApp.java           menu-driven text UI (input/output streams injected)
├── model/                        Location, Visit, Priority, VisitStatus, LocationStatus (records + enums)
├── report/ZoneSummary.java       per-zone aggregate record
├── service/VisitPlannerService.java   all business rules (due dates, overdue, summaries, routes)
├── repository/
│   ├── FieldVisitRepository.java      storage interface the service depends on
│   ├── InMemoryFieldVisitRepository   collections only (used in tests)
│   ├── CsvFieldVisitRepository        extends in-memory, persists to locations.csv / visits.csv
│   └── JdbcFieldVisitRepository       MySQL / any JDBC database, PreparedStatements, try-with-resources
├── exception/                    LocationNotFoundException, InvalidVisitException, DuplicateLocationException
└── util/CsvUtil.java             small CSV parser/writer that handles quoted fields
src/main/resources/schema.sql     table definitions for JDBC mode
src/test/java/...                 JUnit 5 tests for service, model, CSV and JDBC repositories
data/locations.csv, visits.csv    sample data: 40 locations, 5 zones, 48 visits
```

Layers: `ConsoleApp` (UI) -> `VisitPlannerService` (business logic) -> `FieldVisitRepository` (storage).
The service never knows whether data comes from a file or a database.

## Build and run

Requirements: JDK 21 and Maven 3.9.

```bash
# compile, run all tests, and build target/field-visit-tracker.jar
mvn package

# run with the sample CSV data in ./data
java -jar target/field-visit-tracker.jar

# freeze "today" so the sample data always shows the same statuses (good for demos)
java -jar target/field-visit-tracker.jar --today=2026-10-08

# run against MySQL instead of CSV (tables are created automatically from schema.sql)
java -jar target/field-visit-tracker.jar --jdbc=jdbc:mysql://localhost:3306/fieldvisit --user=root --password=secret
```

For MySQL mode create the database first: `CREATE DATABASE fieldvisit;`

Run only the tests:

```bash
mvn test
```

## Sample session

```
=== Field Visit Tracker ===   (today: 2026-10-08)

1. List all locations with status
2. Show overdue locations
3. Record a visit
4. Zone coverage summary
5. Plan route for a zone
6. Add a new location
7. Visit history for a location
0. Exit
Choose an option: 4
Zone        Total  Overdue    Never  DueSoon  OnTrack   Coverage
----------------------------------------------------------------
Central         8        2        1        1        4      62.5%
East            8        2        1        4        1      62.5%
North           8        3        1        1        3      50.0%
South           8        2        1        2        3      62.5%
West            8        2        1        2        3      62.5%

Choose an option: 5
Zones: Central, East, North, South, West
Zone: South
Suggested route for zone 'South' on 2026-10-08:
 1. L015  Electronic City Office         HIGH    OVERDUE        due 2026-10-05 (3 days overdue)
 2. L010  Jayanagar Service Centre       LOW     OVERDUE        due 2026-09-29 (9 days overdue)
 3. L014  Bannerghatta Road Clinic       MEDIUM  NEVER_VISITED  no visit yet
 4. L016  HSR Layout Branch              HIGH    DUE_SOON       due 2026-10-10
 5. L012  BTM Layout Dealer Outlet       MEDIUM  DUE_SOON       due 2026-10-09
 6. L011  JP Nagar Warehouse             HIGH    ON_TRACK       due 2026-10-14
 7. L009  Koramangala Retail Store       MEDIUM  ON_TRACK       due 2026-10-16
 8. L013  Banashankari Kiosk             LOW     ON_TRACK       due 2026-10-20

Choose an option: 3
Location id: L010
  -> Jayanagar Service Centre [South, LOW]
Visit date (YYYY-MM-DD, blank = today):
Field worker name: Priya Nair
Notes (optional): Restock done, manager happy
Recorded visit to Jayanagar Service Centre on 2026-10-08 by Priya Nair. Next visit due: 2026-11-07
```

Invalid input is reported and the menu continues:

```
! No location found with id 'L999'
! Visit date 2030-01-01 cannot be in the future
! A location with id 'L001' already exists
! Unknown priority 'urgent'. Use HIGH, MEDIUM or LOW
```

## Java concepts used (and where)

| Concept | Where to look |
|---|---|
| Encapsulation and validation | `Location` and `Visit` records validate themselves in compact constructors |
| Interfaces and polymorphism | `FieldVisitRepository` with three implementations; the service only sees the interface |
| Inheritance | `CsvFieldVisitRepository extends InMemoryFieldVisitRepository` and overrides the save methods |
| Enums with behaviour | `Priority` carries a rank used for sorting; `VisitStatus` order defines urgency |
| Collections | `LinkedHashMap`, `ArrayList`, `TreeMap`, `TreeSet` in repositories and service |
| Streams and lambdas | `groupingBy`, `counting`, `toMap` with a merge function, `Comparator.comparing` chains in `VisitPlannerService` |
| `Optional` | last visit and next due date in `LocationStatus` |
| `java.time` | `LocalDate`, `ChronoUnit.DAYS`, injected `Clock` so tests use a fixed date |
| Custom exceptions | `exception/` package, caught in one place in `ConsoleApp` |
| File I/O | `java.nio.file.Files` in `CsvFieldVisitRepository` |
| JDBC | `PreparedStatement`, `ResultSet`, try-with-resources in `JdbcFieldVisitRepository` |
| Unit testing | JUnit 5 with `@BeforeEach`, `@TempDir`, `assertThrows`, H2 for database tests |
| Build tooling | Maven, Surefire, Shade plugin for the runnable jar |

## Interview talking points

- **Why a repository interface?** So the business rules can be unit-tested with an in-memory map, and storage can change (CSV today, MySQL tomorrow) without touching the service.
- **Why inject a `Clock`?** `LocalDate.now()` makes tests depend on the real date. Passing a `Clock` lets tests fix "today" to 2026-10-08 and assert exact statuses.
- **Why records?** `Location` and `Visit` are immutable value objects: equals/hashCode/toString come for free and validation lives in one place.
- **How does the route ordering work?** A single `Comparator` chain: status (enum order = urgency), then priority rank, then earliest due date, then id for stable output.
- **Why `toMap` with a merge function?** Several visits can exist per location; the merge function keeps the latest date in one pass instead of sorting per location.
- **What would you improve?** Connection pooling for JDBC, a web UI (Spring Boot), and pagination for very large location lists.

## Publishing to GitHub

```bash
git init
git add .
git commit -m "Field Visit Tracker: Java 21 console app with CSV and JDBC storage"
git branch -M main
git remote add origin https://github.com/<your-username>/field-visit-tracker.git
git push -u origin main
```
