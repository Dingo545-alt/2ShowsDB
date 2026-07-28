#!/usr/bin/env python3
"""
tmdb_etl.py  —  Fetch movies from the TMDB API and load them into MongoDB
                 following the moviedb schema in "Mongo Schema readme.txt".

Schema target:
  movies : { _id, title, year, director, price, rating, vote_count, genres[], stars[{id,name}] }
  stars  : { _id, name, birth_year, movies[{id,title,year}] }

Setup:
  pip install requests pymongo
  export TMDB_API_KEY=your_key_here
  export MONGO_URI=mongodb://localhost:27017   # optional
  export MONGO_DB=moviedb                     # optional

Usage:
  python tmdb_etl.py                            # 5 pages (~100 movies)
  python tmdb_etl.py --pages 20                 # 400 movies
  python tmdb_etl.py --pages 20 --fetch-star-counts   # accurate star ordering (slow)

Star ordering note:
  The schema orders stars by career-movie-count DESC, then name ASC (matching MongoMigration).
  By default this ETL uses TMDB billing order as a fast approximation.
  Pass --fetch-star-counts to resolve exact career counts (1 extra API call per unique actor).
"""

import argparse
import os
import sys
import time
from collections import defaultdict

import requests
from pymongo import MongoClient, UpdateOne

# -- Config ------------------------------------------------------------------
TMDB_BASE    = "https://api.themoviedb.org/3"
TMDB_API_KEY = os.environ.get("TMDB_API_KEY", "")
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


def star_id(person: dict) -> str:
    """IMDb nm-id when available, else tmdb:p<id>."""
    return person.get("imdb_id") or f"tmdb:p{person['id']}"


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

    # Stars: TMDB billing order used by default; overridden by career-count sort
    # in main() when --fetch-star-counts is set.
    cast = detail.get("credits", {}).get("cast", [])
    stars = [
        {"id": star_id(c), "name": c["name"]}
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
            "_id":        sid,
            "name":       info["name"],
            "birth_year": info.get("birth_year"),
            "movies":     movies_sorted,
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


def upsert_stars_in_batches(col, star_docs: list[dict]) -> None:
    """
    Upserts star documents.

    For new stars   : inserts name + birth_year via $setOnInsert, adds movie stubs.
    For existing stars: skips name/birth_year (preserves existing values),
                        and merges new movie in without duplicating.

    After all batches, sorts each star's movies[] by year DESC, title ASC using
    $push/$each:[]/$sort
    """
    if not star_docs:
        print("  [stars] Nothing to upsert.")
        return

    total = 0
    for i in range(0, len(star_docs), BATCH_SIZE):
        batch = star_docs[i : i + BATCH_SIZE]
        ops = [
            UpdateOne(
                {"_id": d["_id"]},
                {
                    # Only set name/birth_year on first insert; never overwrite.
                    "$setOnInsert": {"name": d["name"], "birth_year": d["birth_year"]},
                    # Add each movie stub only if it isn't already in the array.
                    "$addToSet": {"movies": {"$each": d["movies"]}},
                },
                upsert=True,
            )
            for d in batch
        ]
        col.bulk_write(ops, ordered=False)
        total += len(batch)

    # Sort movies[] for every star touched in this run:
    # $push with $each:[] and $sort re-sorts the existing array without adding elements.
    affected_ids = [d["_id"] for d in star_docs]
    col.update_many(
        {"_id": {"$in": affected_ids}},
        {"$push": {"movies": {"$each": [], "$sort": {"year": -1, "title": 1}}}},
    )

    print(f"  [stars] Upserted {total} documents (movies[] merged and sorted).")


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
    cast_map: dict[str, dict] = {}   # star_id → {name, birth_year, tmdb_id}
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
            sid = star_id(person)
            if sid not in cast_map:
                cast_map[sid] = {
                    "name":       person["name"],
                    "birth_year": None,
                    "tmdb_id":    person["id"],
                }

        if i % 50 == 0:
            print(f"  … {i}/{len(tmdb_ids)} processed", flush=True)

    print(f"  Built {len(movie_docs)} movie docs ({skipped} skipped), {len(cast_map)} unique stars.")

    # -- Step 3 (optional): fetch birth years + career counts ------------------
    if args.fetch_star_counts:
        print(f"\n[Step 3] Fetching person details for {len(cast_map)} stars …")
        career_counts: dict[str, int] = {}

        for j, (sid, info) in enumerate(cast_map.items(), 1):
            try:
                person  = tmdb_get(f"/person/{info['tmdb_id']}")
                credits = tmdb_get(f"/person/{info['tmdb_id']}/movie_credits")

                birthday = person.get("birthday", "")
                cast_map[sid]["birth_year"] = (
                    int(birthday[:4]) if birthday and birthday[:4].isdigit() else None
                )
                career_counts[sid] = len(credits.get("cast", []))
            except Exception as e:
                print(f"  [warn] person {sid}: {e}", flush=True)
            time.sleep(REQUEST_DELAY)
            if j % 100 == 0:
                print(f"  … {j}/{len(cast_map)} persons fetched", flush=True)

        # Re-sort stars in each movie: career count DESC, name ASC
        for doc in movie_docs:
            doc["stars"].sort(key=lambda s: (-career_counts.get(s["id"], 0), s["name"]))
    else:
        print("\n[Step 3] Skipped (use --fetch-star-counts for career-count ordering).")

    # -- Step 4: resolve duplicate stars by name -------------------------------
    # Cast credits from TMDB don't include imdb_id, so star_id() falls back to
    # tmdb:p<id>.  Query the DB by name to remap those to existing nm- IDs before
    # building any documents, so both the stars collection and the embedded star
    # stubs in movie docs stay consistent.
    print(f"\n[Step 4] Connecting to MongoDB and resolving star IDs by name …")
    client = MongoClient(MONGO_URI)
    db = client[MONGO_DB]

    remap = resolve_ids_by_name(db["stars"], cast_map)
    if remap:
        apply_id_remap(remap, cast_map, movie_docs)

    # -- Step 5: build star documents ------------------------------------------
    print("\n[Step 5] Building star documents …")
    star_docs = build_star_docs(movie_docs, cast_map)
    print(f"  Built {len(star_docs)} star documents.")

    # -- Step 6: upsert into MongoDB -------------------------------------------
    print(f"\n[Step 6] Writing to MongoDB ({MONGO_URI} / {MONGO_DB}) …")
    inserted_movie_ids = upsert_in_batches(db["movies"], movie_docs, "movies")
    upsert_stars_in_batches(db["stars"], star_docs)
    client.close()

    write_inserted_movies_report(movie_docs, inserted_movie_ids)

    print("\n=== ETL complete. ===")


if __name__ == "__main__":
    main()
