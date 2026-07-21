-- ==========================================================================================================================
-- ______________ Step 1: create the sequence table ______________
--      - For inserting new IDS. Takes Highest and increments it
--      - currently highest for MOVIES is tt0499469, can store up to tt99999999
--          - between tt0499469 - tt99999999 is available (99,500,530)
--      - currently highest for STARS is nm9423080, can store up to nm99999999
--          - between nm9423080 - nm99999999 is available (90,576,919)
-- ==========================================================================================================================
CREATE TABLE IF NOT EXISTS id_sequences (
    entity_type VARCHAR(10)  PRIMARY KEY,
    next_val    INT UNSIGNED NOT NULL
);

-- ==========================================================================================================================
-- ______________ Step 2: seed the counters from the current DB state ______________
-- ==========================================================================================================================
INSERT INTO id_sequences (entity_type, next_val)
    SELECT 'movie', COALESCE(MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)), 0) + 1
    FROM movies
ON DUPLICATE KEY UPDATE next_val = VALUES(next_val);

INSERT INTO id_sequences (entity_type, next_val)
    SELECT 'star', COALESCE(MAX(CAST(SUBSTRING(id, 3) AS UNSIGNED)), 0) + 1
    FROM stars
ON DUPLICATE KEY UPDATE next_val = VALUES(next_val);

-- ==========================================================================================================================
-- ______________ STEP 3: Genre code mapping table ______________
-- ==========================================================================================================================
CREATE TABLE IF NOT EXISTS genre_code_map (
    code VARCHAR(20) PRIMARY KEY,
    genre_name VARCHAR(32) NOT NULL
);

INSERT IGNORE INTO genre_code_map (code, genre_name) VALUES
-- Drama
('Dram', 'Drama'), ('DRam', 'Drama'), ('DRAM', 'Drama'), ('dram', 'Drama'),
('Drama', 'Drama'), ('Dramd', 'Drama'), ('Dramn', 'Drama'), ('Draam', 'Drama'),
('DraM', 'Drama'), ('ram', 'Drama'), ('Dram>', 'Drama'), ('UnDr', 'Drama'),
('Ctxx', 'Drama'), ('Ctxxx', 'Drama'), ('Ctcxx', 'Drama'), ('txx', 'Drama'),
('AvGa', 'Drama'), ('Avant Garde', 'Drama'), ('Expm', 'Drama'), ('Art Video', 'Drama'),
('Allegory', 'Drama'), ('anti-Dram', 'Drama'), ('Dram Docu', 'Drama'),
-- Comedy
('Comd', 'Comedy'), ('comd', 'Comedy'), ('Sati', 'Comedy'), ('Comdx', 'Comedy'),
('Cond', 'Comedy'), ('Camp', 'Comedy'), ('Cult', 'Comedy'), ('Comd Noir', 'Comedy'),
('Comd West', 'Comedy'),
-- Action & Adventure
('Actn', 'Action'), ('actn', 'Action'), ('Act', 'Action'), ('Axtn', 'Action'),
('Sctn', 'Action'), ('Viol', 'Action'), ('Dram.Actn', 'Action'), ('Romt Actn', 'Action'),
('Advt', 'Adventure'), ('Road', 'Adventure'), ('RomtAdvt', 'Adventure'),
-- Thriller & Disaster
('Susp', 'Thriller'), ('susp', 'Thriller'), ('Psyc', 'Thriller'), ('Psych Dram', 'Thriller'),
('Disa', 'Thriller'), ('Dist', 'Thriller'),
-- Romance
('Romt', 'Romance'), ('romt', 'Romance'), ('Romtx', 'Romance'), ('Ront', 'Romance'),
('Romt Comd', 'Romance'), ('Romt. Comd', 'Romance'), ('Romt Dram', 'Romance'),
-- Fantasy & Surreal
('Fant', 'Fantasy'), ('fant', 'Fantasy'), ('Surr', 'Fantasy'), ('Surl', 'Fantasy'),
('surreal', 'Fantasy'), ('Weird', 'Fantasy'), ('Romt Fant', 'Fantasy'), ('FantH*', 'Fantasy'),
-- Horror
('Horr', 'Horror'), ('Hor', 'Horror'), ('horr', 'Horror'), ('H', 'Horror'),
('H**', 'Horror'), ('H0', 'Horror'), ('RFP; H*', 'Horror'),
-- Documentary
('Docu', 'Documentary'), ('Natu', 'Documentary'), ('Ducu', 'Documentary'), ('Dicu', 'Documentary'),
('Duco', 'Documentary'), ('verite', 'Documentary'), ('CA', 'Documentary'), ('TVmini', 'Documentary'),
('Docu Dram', 'Documentary'),
-- Biography
('BioP', 'Biography'), ('Biop', 'Biography'), ('Bio', 'Biography'), ('BioG', 'Biography'),
('BioB', 'Biography'), ('BioPP', 'Biography'), ('BioPx', 'Biography'), ('BiopP', 'Biography'),
-- Sci-Fi & Animation
('ScFi', 'Sci-Fi'), ('SciF', 'Sci-Fi'), ('S.F.', 'Sci-Fi'), ('Scfi', 'Sci-Fi'),
('SxFi', 'Sci-Fi'), ('Cart', 'Animation'),
-- Crime & Mystery & Noir
('CnRb', 'Crime'), ('CnR', 'Crime'), ('Crim', 'Crime'), ('CmR', 'Crime'),
('CnRbb', 'Crime'), ('Myst', 'Mystery'), ('myst', 'Mystery'), ('Mystp', 'Mystery'),
('Noir', 'Film-Noir'), ('noir', 'Film-Noir'), ('Noir Comd', 'Film-Noir'), ('Noir Comd Romt', 'Film-Noir'),
-- Adult
('Porn', 'Adult'), ('porn', 'Adult'), ('Porb', 'Adult'), ('Adct', 'Adult'),
('Adctx', 'Adult'), ('Homo', 'Adult'), ('Kinky', 'Adult'),
-- Others
('West', 'Western'), ('West1', 'Western'), ('Hist', 'History'), ('Epic', 'History'),
('Musc', 'Musical'), ('Muscl', 'Musical'), ('musc', 'Musical'), ('Muusc', 'Musical'),
('stage musical', 'Musical'), ('Faml', 'Family'), ('Scat', 'Short'), ('TV', 'Short');

-- ==========================================================================================================================
--  generate_movie_id  –  returns the next available tt-style ID
-- ==========================================================================================================================
DELIMITER $$
DROP PROCEDURE IF EXISTS generate_movie_id$$
CREATE PROCEDURE generate_movie_id(OUT p_id VARCHAR(10))
BEGIN
    UPDATE id_sequences
    SET    next_val = next_val + 1
    WHERE  entity_type = 'movie';

    SELECT CONCAT('tt', LPAD(next_val - 1, 7, '0'))
    INTO   p_id
    FROM   id_sequences
    WHERE  entity_type = 'movie';
END$$

-- =============================================================
--  generate_star_id  –  same logic, nm prefix, star counter
-- =============================================================


DROP PROCEDURE IF EXISTS generate_star_id$$

CREATE PROCEDURE generate_star_id(OUT p_id VARCHAR(10))
BEGIN
    UPDATE id_sequences
    SET    next_val = next_val + 1
    WHERE  entity_type = 'star';

    SELECT CONCAT('nm', LPAD(next_val - 1, 7, '0'))
    INTO   p_id
    FROM   id_sequences
    WHERE  entity_type = 'star';
END$$

-- =============================================================
--  upsert_movie
--  If movie exists, fill in null fields, else make new entry
-- =============================================================


DROP PROCEDURE IF EXISTS upsert_movie$$

CREATE PROCEDURE upsert_movie(
    IN p_title    VARCHAR(100),
    IN p_year     INT,
    IN p_director VARCHAR(100)
)
proc_label: BEGIN
    DECLARE v_id       VARCHAR(10)  DEFAULT NULL;
    DECLARE v_year     INT          DEFAULT NULL;
    DECLARE v_director VARCHAR(100) DEFAULT NULL;
    DECLARE v_new_id   VARCHAR(10)  DEFAULT NULL;

    IF p_title IS NULL OR p_title = '' THEN
        LEAVE proc_label;
    END IF;

    SELECT id, year, director
    INTO   v_id, v_year, v_director
    FROM   movies
    WHERE  title = p_title
    LIMIT  1;

    IF v_id IS NOT NULL THEN
        -- Movie exists: fill in only the fields that are currently NULL
        IF v_year IS NULL AND p_year IS NOT NULL THEN
            UPDATE movies SET year = p_year WHERE id = v_id;
        END IF;
        IF (v_director IS NULL OR v_director = '') AND p_director IS NOT NULL THEN
            UPDATE movies SET director = p_director WHERE id = v_id;
        END IF;

    ELSE
        -- New movie: claim the next ID from the sequence and insert
        CALL generate_movie_id(v_new_id);
        INSERT INTO movies (id, title, year, director)
        VALUES (v_new_id, p_title, p_year, COALESCE(p_director, ''));
    END IF;

END$$

-- =============================================================
--  upsert_star
--  same logic as the last one
-- =============================================================


DROP PROCEDURE IF EXISTS upsert_star$$

CREATE PROCEDURE upsert_star(
    IN p_name       VARCHAR(100),
    IN p_birth_year INT
)
proc_label: BEGIN
    DECLARE v_id         VARCHAR(10) DEFAULT NULL;
    DECLARE v_birth_year INT         DEFAULT NULL;
    DECLARE v_new_id     VARCHAR(10) DEFAULT NULL;

    IF p_name IS NULL OR p_name = '' THEN
        LEAVE proc_label;
    END IF;

    SELECT id, birth_year
    INTO   v_id, v_birth_year
    FROM   stars
    WHERE  name = p_name
      AND (p_birth_year IS NULL
        OR birth_year  IS NULL
        OR birth_year  = p_birth_year)
    LIMIT  1;

    IF v_id IS NOT NULL THEN
        IF v_birth_year IS NULL AND p_birth_year IS NOT NULL THEN
            UPDATE stars SET birth_year = p_birth_year WHERE id = v_id;
        END IF;

    ELSE
        CALL generate_star_id(v_new_id);
        INSERT INTO stars (id, name, birth_year)
        VALUES (v_new_id, p_name, p_birth_year);
    END IF;

END$$

-- =============================================================
--  link_genre_to_movie
-- =============================================================


DROP PROCEDURE IF EXISTS link_genre_to_movie$$

CREATE PROCEDURE link_genre_to_movie(
    IN p_movie_title VARCHAR(100),
    IN p_genre_code  VARCHAR(20)
)
proc_label: BEGIN
    DECLARE v_genre_name VARCHAR(32) DEFAULT NULL;
    DECLARE v_genre_id   INT         DEFAULT NULL;
    DECLARE v_movie_id   VARCHAR(10) DEFAULT NULL;

    SELECT genre_name INTO v_genre_name
    FROM   genre_code_map
    WHERE  code = TRIM(p_genre_code)
    LIMIT  1;

    IF v_genre_name IS NULL THEN
        LEAVE proc_label;
    END IF;

    SELECT id INTO v_movie_id
    FROM   movies
    WHERE  title = p_movie_title
    LIMIT  1;

    IF v_movie_id IS NULL THEN
        LEAVE proc_label;
    END IF;

    SELECT id INTO v_genre_id
    FROM   genres
    WHERE  name = v_genre_name
    LIMIT  1;

    IF v_genre_id IS NULL THEN
        INSERT INTO genres (name) VALUES (v_genre_name);
        SET v_genre_id = LAST_INSERT_ID();
    END IF;

    INSERT IGNORE INTO genres_in_movies (genre_id, movie_id)
    VALUES (v_genre_id, v_movie_id);

END$$

-- =============================================================
--  upsert_cast
-- =============================================================


DROP PROCEDURE IF EXISTS upsert_cast$$

CREATE PROCEDURE upsert_cast(
    IN p_movie_title VARCHAR(100),
    IN p_actor_name  VARCHAR(100)
)
proc_label: BEGIN
    DECLARE v_movie_id VARCHAR(10) DEFAULT NULL;
    DECLARE v_star_id  VARCHAR(10) DEFAULT NULL;
    DECLARE v_new_id   VARCHAR(10) DEFAULT NULL;

    SELECT id INTO v_movie_id
    FROM   movies
    WHERE  title = p_movie_title
    LIMIT  1;

    IF v_movie_id IS NULL THEN
        LEAVE proc_label;
    END IF;

    SELECT id INTO v_star_id
    FROM   stars
    WHERE  name = p_actor_name
    LIMIT  1;

    IF v_star_id IS NULL THEN
        CALL generate_star_id(v_new_id);
        INSERT INTO stars (id, name, birth_year)
        VALUES (v_new_id, p_actor_name, NULL);
        SET v_star_id = v_new_id;
    END IF;

    INSERT IGNORE INTO stars_in_movies (star_id, movie_id)
    VALUES (v_star_id, v_movie_id);

END$$

DELIMITER ;