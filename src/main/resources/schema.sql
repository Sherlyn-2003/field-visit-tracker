-- Field Visit Tracker schema (MySQL 8 / H2 compatible)
-- Each statement ends with a semicolon and is executed one by one by JdbcFieldVisitRepository.initSchema()

CREATE TABLE IF NOT EXISTS locations (
    id                   VARCHAR(10)  NOT NULL,
    name                 VARCHAR(100) NOT NULL,
    zone                 VARCHAR(50)  NOT NULL,
    priority             VARCHAR(10)  NOT NULL,
    visit_frequency_days INT          NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS visits (
    id           VARCHAR(36)  NOT NULL,
    location_id  VARCHAR(10)  NOT NULL,
    visit_date   DATE         NOT NULL,
    field_worker VARCHAR(100) NOT NULL,
    notes        VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_visit_location FOREIGN KEY (location_id) REFERENCES locations (id)
);
