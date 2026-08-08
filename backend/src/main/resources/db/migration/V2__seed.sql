-- Agent 1 ownership.
-- Seed data sufficient for the frontend demo.
-- Idempotent enough: clears existing seed rows first so re-running is safe during dev.

BEGIN;

-- Clean slate (dev only). FK order matters.
TRUNCATE TABLE
    bookings,
    show_seats,
    shows,
    seats,
    screens,
    theatres,
    movies
RESTART IDENTITY CASCADE;

-- ---------------------------------------------------------------------------
-- movies
-- ---------------------------------------------------------------------------
INSERT INTO movies (id, title, description, duration_minutes, poster_url) VALUES
    (1, 'Spider-Man: Brand New Day',  'The web-slinger returns for a new chapter.', 148, '/images/spiderman.jpg'),
    (2, 'Dune: Messiah',              'The journey continues across the sands.',   166, '/images/dune.jpg'),
    (3, 'Inception Reissue',           'A dream within a dream, on the big screen.', 148, '/images/inception.jpg'),
    (4, 'The Grand Heist',             'One night. One vault. One team.',            132, '/images/heist.jpg');

SELECT setval(pg_get_serial_sequence('movies', 'id'),
              (SELECT MAX(id) FROM movies));

-- ---------------------------------------------------------------------------
-- theatres
-- ---------------------------------------------------------------------------
INSERT INTO theatres (id, name, location) VALUES
    (1, 'Cinema Hall 1',  'CUET Campus, Block A'),
    (2, 'Cinema Hall 2',  'CUET Campus, Block B');

SELECT setval(pg_get_serial_sequence('theatres', 'id'),
              (SELECT MAX(id) FROM theatres));

-- ---------------------------------------------------------------------------
-- screens (2 screens per theatre)
-- ---------------------------------------------------------------------------
INSERT INTO screens (id, theatre_id, name) VALUES
    (1, 1, 'Screen 1'),
    (2, 1, 'Screen 2'),
    (3, 2, 'Screen 1');

SELECT setval(pg_get_serial_sequence('screens', 'id'),
              (SELECT MAX(id) FROM screens));

-- ---------------------------------------------------------------------------
-- seats  --  rows A..F, 10 seats per row = 60 seats per screen
-- ---------------------------------------------------------------------------
INSERT INTO seats (screen_id, row_label, seat_number)
SELECT s.id, r.row_label, n.num
FROM screens s
CROSS JOIN (VALUES ('A'),('B'),('C'),('D'),('E'),('F')) AS r(row_label)
CROSS JOIN generate_series(1, 10) AS n(num)
WHERE s.id IN (1, 2, 3);

-- ---------------------------------------------------------------------------
-- shows  --  several showtimes across the three movies
-- ---------------------------------------------------------------------------
INSERT INTO shows (id, movie_id, screen_id, start_time, base_price) VALUES
    (1, 1, 1, '2026-08-08 20:00:00+00', 450.00),  -- Spider-Man premiere
    (2, 1, 1, '2026-08-08 23:00:00+00', 500.00),  -- Spider-Man midnight
    (3, 2, 2, '2026-08-09 19:00:00+00', 420.00),  -- Dune
    (4, 3, 3, '2026-08-09 17:00:00+00', 380.00),  -- Inception
    (5, 4, 2, '2026-08-09 21:30:00+00', 400.00);  -- Heist

SELECT setval(pg_get_serial_sequence('shows', 'id'),
              (SELECT MAX(id) FROM shows));

-- ---------------------------------------------------------------------------
-- show_seats  --  one row per (show, seat) in screens used by those shows
-- ---------------------------------------------------------------------------
INSERT INTO show_seats (show_id, seat_id, status, price)
SELECT  sh.id,
        st.id,
        'AVAILABLE',
        sh.base_price
FROM shows sh
JOIN seats  st ON st.screen_id = sh.screen_id;

COMMIT;
