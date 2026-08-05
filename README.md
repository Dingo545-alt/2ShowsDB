# 2Shows

Built with Alex Gonzalez, who helped lay the original foundation for this project.

A movie browsing platform — search, browse, and view detail pages for movies and the people who star in them, backed by real, current data imported from TMDB.

## What it does

- Browse and search movies by title, year, director, star, or genre, with sorting and pagination.
- Full-text title search (autocomplete-style, prefix matching).
- Movie detail pages with poster art, rating, genres, and cast.
- Star (actor) detail pages with photo, date of birth, and filmography.
- Simple session-based login for employee/customer accounts.

Originally built as a class project (which also included a shopping cart, employee dashboard, and a MongoDB-vs-MySQL benchmark). This revamp strips that down to a clean browse/search/detail experience.

## Stack

- **Backend:** Java / Jakarta EE (Servlets), Maven, Tomcat 10
- **Data:** MongoDB (primary), via a DAO interface layer so the persistence layer can be swapped
- **Frontend:** vanilla JS / HTML / CSS, no framework
- **Auth:** Google reCAPTCHA v3 on login
- **Data import:** `tmdb_etl.py` — a Python ETL job that pulls current movie/star data from the TMDB API and upserts it into Mongo

## Getting started

1. **Config:** set `MONGO_URI`, `MONGO_DB`, and `RECAPTCHA_SECRET_KEY` (plus `MYSQL_*` if using the legacy pipeline) either as environment variables or in `resources/config.properties` (gitignored — see `AppConfig` for the full lookup order).
2. **Build:** `mvn clean package` — produces `2shows_movie_page.war`.
3. **Run:** deploy the WAR to Tomcat 10, or run `mvn tomcat7:run` / equivalent if configured.
4. **Import data:** run `python tmdb_etl.py` (requires a TMDB API key) to populate `movies`/`stars` collections.
5. **Test:** `mvn test` runs the JUnit/Mockito servlet test suite.

## Project layout

- `src/Model/` — data models (`Movie`, `Star`, `Genre`, `Customer`, `Employee`, ...)
- `src/DataAccessObject/` — DAO interfaces and the MongoDB implementations behind them
- `src/*.java` (top level) — servlets (`MovieListServlet`, `SingleMovieServlet`, `SingleStarServlet`, `LoginServlet`, ...)
- `WebContent/` — frontend (HTML/CSS/JS)
- `test/` — JUnit + Mockito servlet tests
- `legacy/`  — the original MySQL/XML bulk-load pipeline. Also contains files for old features. Kept for reference but not part of the live app.
- `tmdb_etl.py` — the current movie/star data import job