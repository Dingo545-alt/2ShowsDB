package Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataRecords {

    public static abstract class DataRecord {
        public final boolean isPoisonPill;
        protected DataRecord(boolean isPoisonPill) {
            this.isPoisonPill = isPoisonPill;
        }
    }

    /*
     * -------------------------- Actor Record--------------------------
     * Captures:
     *      stageName  -> name
     *      dob        -> birth_year
     * Ignores:
     *      firstname, familyname, gender, dowstart, dowend, dod, roletype, origin, relationships, awards, notes, error
     *
     * Database target : stars(id, name, birth_year)
     * -----------------------------------------------------------------
     */
    public static final class ActorRecord extends DataRecord {
        public static final ActorRecord POISON_PILL = new ActorRecord();

        public final String stageName;
        public final Integer birthYear;

        public ActorRecord(String stageName, Integer birthYear) {
            super(false);
            this.stageName = stageName;
            this.birthYear = birthYear;
        }

        private ActorRecord() {
            super(true);
            this.stageName = null;
            this.birthYear = null;
        }

        @Override public String toString() {
            return "ActorRecord{" + "name=" + stageName + ", dob=" + birthYear + '}';
        }
    }

    /*
     * -------------------------- Movie Record --------------------------
     * Captures:
     *      t → title,
     *      year → year,
     *      dirname → director,
     *      ALL <cat> values → rawGenreCodes
     *
     * Ignores:
     *      "fields that are not listed above"
     *
     * Database target : movies(id, title, year, director)
     *                   genres_in_movies(genre_id, movie_id)
     * -----------------------------------------------------------------
     */
    public static final class MovieRecord extends DataRecord {
        public static final MovieRecord POISON_PILL = new MovieRecord();

        public final String title;
        public final Integer year;
        public final String director;
        public final List<String> rawGenreCodes;

        public MovieRecord(String title, Integer year, String director,
                           List<String> rawGenreCodes) {
            super(false);
            this.title = title;
            this.year = year;
            this.director = director;
            this.rawGenreCodes = Collections.unmodifiableList(new ArrayList<>(rawGenreCodes));
        }

        private MovieRecord() {
            super(true);
            title = null;
            year = null;
            director = null;
            rawGenreCodes = Collections.emptyList();
        }

        @Override public String toString() {
            return "MovieRecord{title='" + title + "', year=" + year
                    + ", director='" + director + "', genres=" + rawGenreCodes + "}";
        }
    }

    /*
     * -------------------------- Cast Record --------------------------
     * Captures:
     *      t → movieTitle (for DB lookup),
     *      a → actorName
     * Ignores:
     *      "fields that are not listed above"
     *
     * Database target : stars_in_movies(star_id, movie_id)
     * -----------------------------------------------------------------
     */
    public static final class CastRecord extends DataRecord {
        public static final CastRecord POISON_PILL = new CastRecord();

        public final String movieTitle;
        public final String actorName;

        public CastRecord(String movieTitle, String actorName) {
            super(false);
            this.movieTitle = movieTitle;
            this.actorName = actorName;

        }

        private CastRecord() {
            super(true);
            this.movieTitle = null;
            this.actorName = null;
        }

        @Override public String toString() {
            return "CastRecord{movieId='" + movieTitle + "', actor='" + actorName + "'}";
        }
    }

    private DataRecords() {}
}
