-- Agent 1 ownership.
-- Source of truth: docs/DATABASE_CONTRACT.md

-- ---------------------------------------------------------------------------
-- movies
-- ---------------------------------------------------------------------------
CREATE TABLE movies (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(255)   NOT NULL,
    description       TEXT,
    duration_minutes  INTEGER        NOT NULL CHECK (duration_minutes > 0),
    poster_url        VARCHAR(1024),
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- theatres
-- ---------------------------------------------------------------------------
CREATE TABLE theatres (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    location    VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- screens
-- ---------------------------------------------------------------------------
CREATE TABLE screens (
    id          BIGSERIAL PRIMARY KEY,
    theatre_id  BIGINT       NOT NULL REFERENCES theatres(id) ON DELETE RESTRICT,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_screens_theatre ON screens(theatre_id);

-- ---------------------------------------------------------------------------
-- seats
-- ---------------------------------------------------------------------------
CREATE TABLE seats (
    id           BIGSERIAL PRIMARY KEY,
    screen_id    BIGINT       NOT NULL REFERENCES screens(id) ON DELETE RESTRICT,
    row_label    VARCHAR(8)   NOT NULL,
    seat_number  INTEGER      NOT NULL CHECK (seat_number > 0),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_seats_screen_row_number UNIQUE (screen_id, row_label, seat_number)
);

CREATE INDEX idx_seats_screen ON seats(screen_id);

-- ---------------------------------------------------------------------------
-- shows
-- ---------------------------------------------------------------------------
CREATE TABLE shows (
    id          BIGSERIAL PRIMARY KEY,
    movie_id    BIGINT       NOT NULL REFERENCES movies(id)  ON DELETE RESTRICT,
    screen_id   BIGINT       NOT NULL REFERENCES screens(id) ON DELETE RESTRICT,
    start_time  TIMESTAMP    NOT NULL,
    base_price  NUMERIC(10,2) NOT NULL CHECK (base_price >= 0),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shows_movie  ON shows(movie_id);
CREATE INDEX idx_shows_screen ON shows(screen_id);

-- ---------------------------------------------------------------------------
-- show_seats  --  THE CRITICAL TABLE.
-- One row = one physical seat for one specific show.
-- ---------------------------------------------------------------------------
CREATE TABLE show_seats (
    id               BIGSERIAL PRIMARY KEY,
    show_id          BIGINT        NOT NULL REFERENCES shows(id) ON DELETE RESTRICT,
    seat_id          BIGINT        NOT NULL REFERENCES seats(id) ON DELETE RESTRICT,
    status           VARCHAR(16)   NOT NULL,
    held_by          VARCHAR(128),
    hold_expires_at  TIMESTAMP,
    price            NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_show_seats_show_seat UNIQUE (show_id, seat_id),
    CONSTRAINT ck_show_seats_status
        CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED'))
);

CREATE INDEX idx_show_seats_show         ON show_seats(show_id);
CREATE INDEX idx_show_seats_seat         ON show_seats(seat_id);
CREATE INDEX idx_show_seats_status       ON show_seats(status);
CREATE INDEX idx_show_seats_hold_expires ON show_seats(hold_expires_at);

-- ---------------------------------------------------------------------------
-- bookings
-- ---------------------------------------------------------------------------
CREATE TABLE bookings (
    id            BIGSERIAL PRIMARY KEY,
    booking_ref   VARCHAR(64)  NOT NULL,
    show_seat_id  BIGINT       NOT NULL REFERENCES show_seats(id) ON DELETE RESTRICT,
    user_id       VARCHAR(128) NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bookings_booking_ref UNIQUE (booking_ref),
    CONSTRAINT ck_bookings_status
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PAYMENT_FAILED', 'EXPIRED'))
);

CREATE INDEX idx_bookings_show_seat ON bookings(show_seat_id);
CREATE INDEX idx_bookings_user      ON bookings(user_id);
CREATE INDEX idx_bookings_status    ON bookings(status);
