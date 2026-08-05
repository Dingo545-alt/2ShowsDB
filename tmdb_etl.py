#!/usr/bin/env python3
"""
tmdb_etl.py  —  Fetch movies from the TMDB API and load them into MongoDB
                 following the moviedb schema in "Mongo Schema readme.txt".

Schema target:
  movies    : { _id, title, year, director, price, rating, vote_count, genres[], stars[{id,name}] }
  stars     : { _id, name, dob, photo, movies[{id,title,year}] }
  directors : { _id, name, dob, photo, movies[{id,title,year}] }

Setup:
  pip install requests pymongo
  export TMDB_API_KEY=your_key_here
  export MONGO_URI=mongodb://localhost:27017   # optional
  export MONGO_DB=moviedb                     # optional

Usage:
  python tmdb_etl.py                            # 5 pages (~100 movies)
  python tmdb_etl.py --pages 20                 # 400 movies
  python tmdb_etl.py --pages 20 --fetch-star-counts   # accurate star ordering (slow)

Star / director dob / photo note:
  Every ETL run fetches /person/{id} for each newly-seen star or director to
  populate dob and photo (one extra API call per unique person, shared by
  both fields) — dob and photo are core schema data, not optional.

Star ordering note:
  The schema orders stars by career-movie-count DESC, then name ASC (matching MongoMigration).
  By default this ETL uses TMDB billing order as a fast approximation.
  Pass --fetch-star-counts to resolve exact career counts (1 more extra API call
  per unique actor, on top of the dob fetch above — slow for large imports).
"""

import argparse
import os
import sys
import time
from collections import defaultdict

import requests
from pymongo import MongoClient, UpdateOne

# -- Config ------------------------------------------------------------------
TMDB_BASE       = "https://api.themoviedb.org/3"
TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
TMDB_API_KEY    = os.environ.get("TMDB_API_KEY", "")
MONGO_URI    = os.environ.get("MONGO_URI", "mongodb://localhost:27017")
MONGO_DB     = os.environ.get("MONGO_DB", "moviedb")

DEFAULT_PRICE = 14.99
BATCH_SIZE    = 500
REQUEST_DELAY = 0.05   # seconds between TMDB calls to stay under rate limit (40 req/s)


# -- TMDB helpers ------------------------------------------------------------
_session = requests.Session()
_session.params = {"api_key": TMDB_API_KEY}


def tmdb_get(path: str, **params) -> dict:
    url = f"{TMDB_BASE}{path}"
    for attempt in range(3):
        resp = _session.get(url, params=params, timeout=10)
        if resp.status_code == 429:
            retry_after = int(resp.headers.get("Retry-After", 2))
            print(f"  [rate-limit] sleeping {retry_after}s …", flush=True)
            time.sleep(retry_after)
            continue
        resp.raise_for_status()
        return resp.json()
    raise RuntimeError(f"Failed after 3 retries: {path}")


def movie_id(detail: dict) -> str:
    """IMDb tt-id when available (matches existing data), else tmdb:<id>."""
    return detail.get("imdb_id") or f"tmdb:{detail['id']}"


def person_id(person: dict) -> str:
    """IMDb nm-id when available, else tmdb:p<id>. Used for both cast and crew credits."""
    return person.get("imdb_id") or f"tmdb:p{person['id']}"


def build_poster(poster_path: str | None) -> dict | None:
    """Resolves a TMDB poster_path into the stored poster sub-document, or
    None if the movie has no poster on TMDB."""
    if not poster_path:
        return None
    return {
        "path": poster_path,
        "sizes": {
            "w342":     f"{TMDB_IMAGE_BASE}/w342{poster_path}",
            "original": f"{TMDB_IMAGE_BASE}/original{poster_path}",
        },
    }


def build_photo(profile_path: str | None) -> dict | None:
    """Resolves a TMDB profile_path into the stored star photo sub-document,
    or None if the person has no profile image on TMDB."""
    if not profile_path:
        return None
    return {
        "path": profile_path,
        "sizes": {
            "w185":     f"{TMDB_IMAGE_BASE}/w185{profile_path}",
            "original": f"{TMDB_IMAGE_BASE}/original{profile_path}",
        },
    }


# -- Fetch helpers --------------------------------------------------------------
def fetch_popular_ids(page: int) -> list[int]:
    data = tmdb_get("/movie/popular", page=page)
    return [m["id"] for m in data.get("results", [])]


def fetch_movie_detail(tmdb_id: int) -> dict | None:
    try:
        # append_to_response bundles credits into a single API call
        return tmdb_get(f"/movie/{tmdb_id}", append_to_response="credits")
    except Exception as e:
        print(f"  [warn] Could not fetch movie {tmdb_id}: {e}", flush=True)
        return None


def fetch_person_details(person_map: dict, label: str, fetch_career_counts: bool = False) -> dict:
    """
    Fetches dob + photo for every TMDB person in person_map (mutated in place),
    shared by both stars and directors. When fetch_career_counts is set (stars
    only), also fetches each person's total movie-credit count, returned as
    {id: count} — used to order movies.stars[] by career count DESC.
    """
    career_counts: dict[str, int] = {}
    total = len(person_map)
    for j, (pid, info) in enumerate(person_map.items(), 1):
        try:
            person = tmdb_get(f"/person/{info['tmdb_id']}")
            info["dob"] = person.get("birthday") or None
            info["photo"] = build_photo(person.get("profile_path"))

            if fetch_career_counts:
                credits = tmdb_get(f"/person/{info['tmdb_id']}/movie_credits")
                career_counts[pid] = len(credits.get("cast", []))
        except Exception as e:
            print(f"  [warn] person {pid}: {e}", flush=True)
        time.sleep(REQUEST_DELAY)
        if j % 100 == 0:
            print(f"  … {j}/{total} {label} fetched", flush=True)
    return career_counts


# -- Document builders ----------------------------------------------
def build_movie_doc(detail: dict, price: float) -> dict | None:
    title = detail.get("title") or detail.get("original_title")
    if not title:
        return None

    release = detail.get("release_date", "")
    year = int(release[:4]) if release and release[:4].isdigit() else None
    if year is None:
        return None

    crew = detail.get("credits", {}).get("crew", [])
    director = next((c["name"] for c in crew if c.get("job") == "Director"), None)
    if not director:
        return None

    # Genres: alphabetically sorted (matches MongoMigration ORDER BY g.name)
    genres = sorted(g["name"] for g in detail.get("genres", []))

    vote_avg   = detail.get("vote_average")
    vote_count = detail.get("vote_count")
    rating     = round(float(vote_avg), 1) if vote_avg else None
    votes      = int(vote_count)           if vote_count else None

    poster = build_poster(detail.get("poster_path"))

    # Stars: TMDB billing order used by default; overridden by career-count sort
    # in main() when --fetch-star-counts is set.
    cast = detail.get("credits", {}).get("cast", [])
    stars = [
        {"id": person_id(c), "name": c["name"]}
        for c in sorted(cast, key=lambda c: c.get("order", 999))
    ]

    return {
        "_id":        movie_id(detail),
        "title":      title,
        "year":       year,
        "director":   director,
        "price":      price,
        "rating":     rating,
        "vote_count": votes,
        "genres":     genres,
        "stars":      stars,
        "poster":     poster,
    }


def resolve_ids_by_name(stars_col, cast_map: dict) -> dict:
    """
    For stars whose _id is a tmdb:p<id> fallback (i.e. no IMDb nm-id was available
    from the cast credits response), look up the stars collection by name.
    If a match is found, return a remap of  tmdb:p<id> → existing nm-id  so the
    ETL merges into the existing document instead of creating a duplicate.

    Names are not globally unique, but they are a good-enough signal here: a
    collision (two different actors sharing a name) will be logged as a warning
    rather than silently merged.
    """
    tmdb_stars = {sid: info for sid, info in cast_map.items()
                  if sid.startswith("tmdb:p")}
    if not tmdb_stars:
        return {}

    names = [info["name"] for info in tmdb_stars.values()]

    # Find all existing stars whose name appears in our set.
    # Project only _id + name to keep the query lightweight.
    existing = list(stars_col.find({"name": {"$in": names}}, {"_id": 1, "name": 1}))

    # Build name → existing _id.  If two DB docs share a name, skip both to
    # avoid a wrong merge and warn instead.
    name_to_id: dict[str, str] = {}
    seen: set[str] = set()
    for doc in existing:
        name = doc["name"]
        if name in seen:
            print(f"  [warn] Ambiguous name '{name}' — multiple DB entries found, skipping merge.")
            name_to_id.pop(name, None)
        else:
            seen.add(name)
            name_to_id[name] = doc["_id"]

    remap: dict[str, str] = {}
    for sid, info in tmdb_stars.items():
        existing_id = name_to_id.get(info["name"])
        if existing_id:
            remap[sid] = existing_id

    if remap:
        print(f"  [name-match] Resolved {len(remap)} tmdb:p IDs to existing nm- IDs.")
    return remap


def apply_id_remap(remap: dict, cast_map: dict, movie_docs: list[dict]) -> None:
    """
    Apply a tmdb:p<id> → nm-<id> remap in-place to both cast_map and the
    star stubs embedded inside every movie document.
    """
    # Remap cast_map keys
    for old_id, new_id in remap.items():
        if old_id in cast_map:
            cast_map[new_id] = cast_map.pop(old_id)

    # Remap star stubs inside each movie doc
    for doc in movie_docs:
        doc["stars"] = [
            {"id": remap.get(s["id"], s["id"]), "name": s["name"]}
            for s in doc["stars"]
        ]


def build_star_docs(movie_docs: list[dict], cast_map: dict) -> list[dict]:
    """
    Builds star documents with embedded movie stubs.
    movies[] is sorted year DESC, title ASC
    """
    star_movies: dict[str, list[dict]] = defaultdict(list)
    for movie in movie_docs:
        stub = {"id": movie["_id"], "title": movie["title"], "year": movie["year"]}
        for s in movie["stars"]:
            star_movies[s["id"]].append(stub)

    docs = []
    for sid, info in cast_map.items():
        movies_sorted = sorted(
            star_movies.get(sid, []),
            key=lambda m: (-m["year"], m["title"])
        )
        docs.append({
            "_id":    sid,
            "name":   info["name"],
            "dob":    info.get("dob"),
            "photo":  info.get("photo"),
            "movies": movies_sorted,
        })
    return docs


def build_director_docs(director_map: dict, director_movies: dict) -> list[dict]:
    """
    Builds director documents with embedded movie stubs, mirroring build_star_docs.
    movies[] is sorted year DESC, title ASC. Unlike stars (whose movie list is
    derived from movie_docs after the fact), director_movies is accumulated
    directly while walking movie credits in main(), since movie docs only carry
    the director's name, not their id.
    """
    docs = []
    for did, info in director_map.items():
        movies_sorted = sorted(
            director_movies.get(did, []),
            key=lambda m: (-m["year"], m["title"])
        )
        docs.append({
            "_id":    did,
            "name":   info["name"],
            "dob":    info.get("dob"),
            "photo":  info.get("photo"),
            "movies": movies_sorted,
        })
    return docs


# -- MongoDB upsert -------------------------------------------------------------
def upsert_in_batches(col, docs: list[dict], label: str) -> set:
    """Upserts docs and returns the set of _ids that were newly inserted."""
    if not docs:
        print(f"  [{label}] Nothing to upsert.")
        return set()

    inserted_ids: set = set()
    total = 0
    for i in range(0, len(docs), BATCH_SIZE):
        batch = docs[i : i + BATCH_SIZE]
        ops = [UpdateOne({"_id": d["_id"]}, {"$set": d}, upsert=True) for d in batch]
        result = col.bulk_write(ops, ordered=False)
        # upserted_ids maps batch-local index → _id for docs that were inserted
        for local_idx, oid in result.upserted_ids.items():
            inserted_ids.add(oid)
        total += len(batch)

    print(f"  [{label}] Upserted {total} documents ({len(inserted_ids)} newly inserted).")
    return inserted_ids


def upsert_person_docs_in_batches(col, docs: list[dict], label: str) -> None:
    """
    Upserts star or director documents — anything shaped
    { _id, name, dob, photo, movies[] }.

    For new people     : inserts name via $setOnInsert; dob/photo are
                         $setOnInsert'd as null only if this run has no value
                         for the person, so the fields still exist on the doc.
    For existing people : name is never overwritten. dob/photo are only ever
                         $set when this run resolved a real (non-null) value —
                         an already null value is never used to clobber a
                         previously-known one, and a previously-null value
                         gets filled in as soon as a later run resolves it
                         (fixes dob/photo being frozen at null forever).
    In all cases        : merges new movie stubs in without duplicating.

    After all batches, sorts each person's movies[] by year DESC, title ASC
    using $push/$each:[]/$sort
    """
    if not docs:
        print(f"  [{label}] Nothing to upsert.")
        return

    total = 0
    for i in range(0, len(docs), BATCH_SIZE):
        batch = docs[i : i + BATCH_SIZE]
        ops = []
        for d in batch:
            set_on_insert = {"name": d["name"]}
            update = {"$addToSet": {"movies": {"$each": d["movies"]}}}
            set_fields = {}
            if d["dob"]:
                set_fields["dob"] = d["dob"]
            else:
                set_on_insert["dob"] = None
            if d["photo"]:
                set_fields["photo"] = d["photo"]
            else:
                set_on_insert["photo"] = None
            if set_fields:
                update["$set"] = set_fields
            update["$setOnInsert"] = set_on_insert
            ops.append(UpdateOne({"_id": d["_id"]}, update, upsert=True))
        col.bulk_write(ops, ordered=False)
        total += len(batch)

    # Sort movies[] for every person touched in this run:
    # $push with $each:[] and $sort re-sorts the existing array without adding elements.
    affected_ids = [d["_id"] for d in docs]
    col.update_many(
        {"_id": {"$in": affected_ids}},
        {"$push": {"movies": {"$each": [], "$sort": {"year": -1, "title": 1}}}},
    )

    print(f"  [{label}] Upserted {total} documents (movies[] merged and sorted).")


def write_inserted_movies_report(movie_docs: list[dict], inserted_ids: set,
                                 path: str = "NewlyInsertedMovies.txt") -> None:
    newly_inserted = [d for d in movie_docs if d["_id"] in inserted_ids]
    with open(path, "w", encoding="utf-8") as f:
        f.write(f"Newly Inserted Movies ({len(newly_inserted)})\n")
        f.write("=" * 60 + "\n\n")
        for doc in newly_inserted:
            rating_str = f"{doc['rating']} ({doc['vote_count']:,} votes)" \
                         if doc["rating"] is not None else "Unrated"
            f.write(f"ID       : {doc['_id']}\n")
            f.write(f"Title    : {doc['title']}\n")
            f.write(f"Year     : {doc['year']}\n")
            f.write(f"Director : {doc['director']}\n")
            f.write(f"Genres   : {', '.join(doc['genres']) or 'N/A'}\n")
            f.write(f"Rating   : {rating_str}\n")
            f.write(f"Price    : ${doc['price']:.2f}\n")
            f.write("-" * 60 + "\n")
    print(f"  Report written to {path} ({len(newly_inserted)} movies).")


# -- Main -----------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="TMDB → MongoDB ETL")
    parser.add_argument("--pages", type=int, default=5,
                        help="Popular-movie pages to fetch (20 movies/page, default 5)")
    parser.add_argument("--price", type=float, default=DEFAULT_PRICE,
                        help=f"Default price per movie (default {DEFAULT_PRICE})")
    parser.add_argument("--fetch-star-counts", action="store_true",
                        help="Fetch each star's filmography for accurate career-count ordering "
                             "(1 extra API call per unique actor — slow for large imports)")
    args = parser.parse_args()

    if not TMDB_API_KEY:
        sys.exit("ERROR: TMDB_API_KEY environment variable is not set")

    print("=== TMDB → MongoDB ETL ===\n")

    # -- Step 1: collect TMDB movie IDs ----------------------------------------
    print(f"[Step 1] Fetching {args.pages} page(s) of popular movies …")
    tmdb_ids: list[int] = []
    for page in range(1, args.pages + 1):
        tmdb_ids.extend(fetch_popular_ids(page))
        time.sleep(REQUEST_DELAY)
    print(f"  Collected {len(tmdb_ids)} movie IDs.")

    # -- Step 2: fetch detail + credits ----------------------------------------
    print(f"\n[Step 2] Fetching movie details + credits …")
    movie_docs: list[dict] = []
    cast_map: dict[str, dict] = {}                              # star_id     → {name, dob, photo, tmdb_id}
    director_map: dict[str, dict] = {}                          # director_id → {name, dob, photo, tmdb_id}
    director_movies: dict[str, list[dict]] = defaultdict(list)  # director_id → [movie stub, ...]
    skipped = 0

    for i, tid in enumerate(tmdb_ids, 1):
        detail = fetch_movie_detail(tid)
        time.sleep(REQUEST_DELAY)
        if detail is None:
            skipped += 1
            continue

        doc = build_movie_doc(detail, args.price)
        if doc is None:
            skipped += 1
            print(f"  [skip] tmdb:{tid} — missing title, year, or director", flush=True)
            continue

        movie_docs.append(doc)

        for person in detail.get("credits", {}).get("cast", []):
            sid = person_id(person)
            if sid not in cast_map:
                cast_map[sid] = {
                    "name":    person["name"],
                    "dob":     None,
                    "photo":   None,
                    "tmdb_id": person["id"],
                }

        # doc is non-None only when build_movie_doc found a director in this
        # same crew list, so director_person is guaranteed to be found again here.
        crew = detail.get("credits", {}).get("crew", [])
        director_person = next((c for c in crew if c.get("job") == "Director"), None)
        if director_person:
            did = person_id(director_person)
            if did not in director_map:
                director_map[did] = {
                    "name":    director_person["name"],
                    "dob":     None,
                    "photo":   None,
                    "tmdb_id": director_person["id"],
                }
            director_movies[did].append({"id": doc["_id"], "title": doc["title"], "year": doc["year"]})

        if i % 50 == 0:
            print(f"  … {i}/{len(tmdb_ids)} processed", flush=True)

    print(f"  Built {len(movie_docs)} movie docs ({skipped} skipped), "
          f"{len(cast_map)} unique stars, {len(director_map)} unique directors.")

    # -- Step 3: fetch dob/photo (always) + career counts (stars, opt-in) -------
    print(f"\n[Step 3] Fetching person details for {len(cast_map)} stars "
          f"and {len(director_map)} directors …")
    career_counts = fetch_person_details(cast_map, "stars", fetch_career_counts=args.fetch_star_counts)
    fetch_person_details(director_map, "directors")

    if args.fetch_star_counts:
        # Re-sort stars in each movie: career count DESC, name ASC
        for doc in movie_docs:
            doc["stars"].sort(key=lambda s: (-career_counts.get(s["id"], 0), s["name"]))
    else:
        print("  (use --fetch-star-counts for accurate career-count ordering; "
              "using TMDB billing order as an approximation.)")

    # -- Step 4: resolve duplicate stars by name -------------------------------
    # Cast credits from TMDB don't include imdb_id, so person_id() falls back to
    # tmdb:p<id>.  Query the DB by name to remap those to existing nm- IDs before
    # building any documents, so both the stars collection and the embedded star
    # stubs in movie docs stay consistent.
    #
    # Directors skip this step: the directors collection has no pre-existing
    # legacy dataset to reconcile against (unlike stars, which were migrated
    # from the old MySQL pipeline before TMDB import existed), so a director's
    # tmdb:p<id> is already stable and consistent across ETL runs.
    print(f"\n[Step 4] Connecting to MongoDB and resolving star IDs by name …")
    client = MongoClient(MONGO_URI)
    db = client[MONGO_DB]

    remap = resolve_ids_by_name(db["stars"], cast_map)
    if remap:
        apply_id_remap(remap, cast_map, movie_docs)

    # -- Step 5: build star + director documents -------------------------------
    print("\n[Step 5] Building star and director documents …")
    star_docs = build_star_docs(movie_docs, cast_map)
    director_docs = build_director_docs(director_map, director_movies)
    print(f"  Built {len(star_docs)} star documents, {len(director_docs)} director documents.")

    # -- Step 6: upsert into MongoDB -------------------------------------------
    print(f"\n[Step 6] Writing to MongoDB ({MONGO_URI} / {MONGO_DB}) …")
    inserted_movie_ids = upsert_in_batches(db["movies"], movie_docs, "movies")
    upsert_person_docs_in_batches(db["stars"], star_docs, "stars")
    upsert_person_docs_in_batches(db["directors"], director_docs, "directors")
    client.close()

    write_inserted_movies_report(movie_docs, inserted_movie_ids)

    print("\n=== ETL complete. ===")


if __name__ == "__main__":
    main()
