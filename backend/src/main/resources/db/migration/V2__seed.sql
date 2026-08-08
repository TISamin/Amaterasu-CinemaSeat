-- Agent 1 ownership.
-- Seed data sufficient for the frontend demo.

-- ---------------------------------------------------------------------------
-- movies
-- ---------------------------------------------------------------------------
INSERT INTO movies (id, title, description, duration_minutes, poster_url) VALUES
    (1, 'Spider-Man: Brand New Day',  'The web-slinger returns for a new chapter.', 148, '/images/spiderman.jpg'),
    (2, 'Dune: Messiah',              'The journey continues across the sands.',   166, '/images/dune.jpg'),
    (3, 'Inception Reissue',           'A dream within a dream, on the big screen.', 148, '/images/inception.jpg'),
    (4, 'The Grand Heist',             'One night. One vault. One team.',            132, '/images/heist.jpg');

-- ---------------------------------------------------------------------------
-- theatres
-- ---------------------------------------------------------------------------
INSERT INTO theatres (id, name, location) VALUES
    (1, 'Cinema Hall 1',  'CUET Campus, Block A'),
    (2, 'Cinema Hall 2',  'CUET Campus, Block B');

-- ---------------------------------------------------------------------------
-- screens (2 screens per theatre)
-- ---------------------------------------------------------------------------
INSERT INTO screens (id, theatre_id, name) VALUES
    (1, 1, 'Screen 1'),
    (2, 1, 'Screen 2'),
    (3, 2, 'Screen 1');

-- ---------------------------------------------------------------------------
-- seats  --  rows A..F, 10 seats per row = 60 seats per screen
-- ---------------------------------------------------------------------------
INSERT INTO seats (screen_id, row_label, seat_number) VALUES
    (1, 'A', 1), (1, 'A', 2), (1, 'A', 3), (1, 'A', 4), (1, 'A', 5), (1, 'A', 6), (1, 'A', 7), (1, 'A', 8), (1, 'A', 9), (1, 'A', 10),
    (1, 'B', 1), (1, 'B', 2), (1, 'B', 3), (1, 'B', 4), (1, 'B', 5), (1, 'B', 6), (1, 'B', 7), (1, 'B', 8), (1, 'B', 9), (1, 'B', 10),
    (1, 'C', 1), (1, 'C', 2), (1, 'C', 3), (1, 'C', 4), (1, 'C', 5), (1, 'C', 6), (1, 'C', 7), (1, 'C', 8), (1, 'C', 9), (1, 'C', 10),
    (1, 'D', 1), (1, 'D', 2), (1, 'D', 3), (1, 'D', 4), (1, 'D', 5), (1, 'D', 6), (1, 'D', 7), (1, 'D', 8), (1, 'D', 9), (1, 'D', 10),
    (1, 'E', 1), (1, 'E', 2), (1, 'E', 3), (1, 'E', 4), (1, 'E', 5), (1, 'E', 6), (1, 'E', 7), (1, 'E', 8), (1, 'E', 9), (1, 'E', 10),
    (1, 'F', 1), (1, 'F', 2), (1, 'F', 3), (1, 'F', 4), (1, 'F', 5), (1, 'F', 6), (1, 'F', 7), (1, 'F', 8), (1, 'F', 9), (1, 'F', 10),
    (2, 'A', 1), (2, 'A', 2), (2, 'A', 3), (2, 'A', 4), (2, 'A', 5), (2, 'A', 6), (2, 'A', 7), (2, 'A', 8), (2, 'A', 9), (2, 'A', 10),
    (2, 'B', 1), (2, 'B', 2), (2, 'B', 3), (2, 'B', 4), (2, 'B', 5), (2, 'B', 6), (2, 'B', 7), (2, 'B', 8), (2, 'B', 9), (2, 'B', 10),
    (3, 'A', 1), (3, 'A', 2), (3, 'A', 3), (3, 'A', 4), (3, 'A', 5), (3, 'A', 6), (3, 'A', 7), (3, 'A', 8), (3, 'A', 9), (3, 'A', 10),
    (3, 'B', 1), (3, 'B', 2), (3, 'B', 3), (3, 'B', 4), (3, 'B', 5), (3, 'B', 6), (3, 'B', 7), (3, 'B', 8), (3, 'B', 9), (3, 'B', 10);

-- ---------------------------------------------------------------------------
-- shows  --  several showtimes across the three movies
-- ---------------------------------------------------------------------------
INSERT INTO shows (id, movie_id, screen_id, start_time, base_price) VALUES
    (1, 1, 1, '2026-08-08 20:00:00', 450.00),  -- Spider-Man premiere
    (2, 1, 1, '2026-08-08 23:00:00', 500.00),  -- Spider-Man midnight
    (3, 2, 2, '2026-08-09 19:00:00', 420.00),  -- Dune
    (4, 3, 3, '2026-08-09 17:00:00', 380.00),  -- Inception
    (5, 4, 2, '2026-08-09 21:30:00', 400.00);  -- Heist

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
