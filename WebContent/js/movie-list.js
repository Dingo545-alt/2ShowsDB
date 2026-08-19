const DEFAULT_PRIMARY_SORT_FIELD       = "rating";
const DEFAULT_PRIMARY_SORT_DIRECTION   = "desc";
const DEFAULT_PAGE_SIZE                = 10;
const DEFAULT_PAGE_NUMBER              = 1;

const ALLOWED_PAGE_SIZES = [10, 25, 50, 100];

// The direction a column starts in the first time it's clicked (ratings are
// most useful sorted high-to-low first, titles most useful A-to-Z first).
const DEFAULT_DIRECTION_FOR_FIELD = {
    rating: "desc",
    title:  "asc"
};

/**
 * Returns the "other" sortable field. Used as the fixed tiebreaker for whichever
 * field is currently active, so results are always stably ordered.
 */
function otherSortField(sortField) {
    return sortField === "title" ? "rating" : "title";
}

/**
 * Reads the current sort state from the page URL's query string, falling back
 * to the defaults if the params are missing or bad. Uses URL to determine it.
 *
 * Only the primary field/direction are user-controlled; the secondary is always
 * used as a fixed tiebreaker (the other field, ascending).
 *
 * Returns example {primaryField, primaryDirection, secondaryField, secondaryDirection}.
 */
function readCurrentStateFromUrl() {
    const urlParams = new URLSearchParams(window.location.search);

    let primaryField = urlParams.get("primarySortField");
    if (primaryField !== "title" && primaryField !== "rating") {
        primaryField = DEFAULT_PRIMARY_SORT_FIELD;
    }

    let primaryDirection = urlParams.get("primarySortDirection");
    if (primaryDirection !== "asc" && primaryDirection !== "desc") {
        primaryDirection = DEFAULT_PRIMARY_SORT_DIRECTION;
    }

    const secondaryField     = otherSortField(primaryField);
    const secondaryDirection = "asc";

    // --- Pagination ---
    let pageSize = parseInt(urlParams.get("pageSize"), 10);
    if (!ALLOWED_PAGE_SIZES.includes(pageSize)) {
        pageSize = DEFAULT_PAGE_SIZE;
    }

    let pageNumber = parseInt(urlParams.get("pageNumber"), 10);
    if (!Number.isInteger(pageNumber) || pageNumber < 1) {
        pageNumber = DEFAULT_PAGE_NUMBER;
    }

    return {
        primaryField, primaryDirection,
        secondaryField, secondaryDirection,
        pageSize, pageNumber,

        title:      urlParams.get("title")       || null,
        year:       urlParams.get("year")        || null,
        director:   urlParams.get("director")    || null,
        star:       urlParams.get("star")        || null,
        genre:      urlParams.get("genre")       || null,
        startChar:  urlParams.get("start-char")  || null,
        fulltext:   urlParams.get("fulltext")    || null
    };
}

/**
 * Writes the given sort state back to the URL's query string without reloading the page.
 * It uses history.replaceStates so that it does not pollute the back button on the browser with
 * every single sort toggle
 */
function writeStateToUrl(state) {
    const urlParams = new URLSearchParams(window.location.search);
    urlParams.set("primarySortField",       state.primaryField);
    urlParams.set("primarySortDirection",   state.primaryDirection);
    urlParams.set("secondarySortField",     state.secondaryField);
    urlParams.set("secondarySortDirection", state.secondaryDirection);
    urlParams.set("pageSize",               state.pageSize);
    urlParams.set("pageNumber",             state.pageNumber);

    const newRelativeUrl = window.location.pathname + "?" + urlParams.toString();
    window.history.replaceState(null, "", newRelativeUrl);
}

/**
 * Updates the ▲ / ▼ icon next to whichever sortable column header is active.
 * ▲ = ascending
 * ▼ = descending
 */
function updateHeaderSortIndicatorIcons(sortState) {
    jQuery(".sortable-header").each(function () {
        const headerElement    = jQuery(this);
        const headerSortField  = headerElement.data("sort-field");
        const directionElement = headerElement.find(".sort-direction-icon");

        headerElement.removeClass("is-active-sort-column");
        directionElement.text("");

        if (headerSortField === sortState.primaryField) {
            directionElement.text(sortState.primaryDirection === "asc" ? "▲" : "▼");
            headerElement.addClass("is-active-sort-column");
        }
    });
}

/**
 * forces front end to show what backend updated to pagination on the dropdown menu
 */
function updatePageSizeDropdownSelection(state) {
    jQuery("#page-size-select").val(String(state.pageSize));
}

/**
 * updates prev/next button. Disables prv on page 1, and vice versa
 */
function updatePaginationControlsFromServerResponse(state, totalCount) {
    const firstRowNumberOnCurrentPage = (state.pageNumber - 1) * state.pageSize + 1;
    const lastRowNumberOnCurrentPage  =
        Math.min(state.pageNumber * state.pageSize, totalCount);

    const isOnFirstPage = state.pageNumber <= 1;
    const isOnLastPage  = state.pageNumber * state.pageSize >= totalCount;

    // Friendly empty-result case
    const statusText = totalCount === 0
        ? "No movies found"
        : `Showing ${firstRowNumberOnCurrentPage}–${lastRowNumberOnCurrentPage} of ${totalCount}`;

    jQuery(".pagination-status-text").text(statusText);
    jQuery(".previous-page-button").prop("disabled", isOnFirstPage);
    jQuery(".next-page-button").prop("disabled", isOnLastPage);
}

/**
 *   Clicking the already-active column flips its direction.
 *   Clicking the other column makes it active, starting in its default direction.
 *   The inactive column always becomes the tiebreaker (ascending), so results
 *   never look randomly ordered when two movies share a title or rating.
 */
function computeNextStateForHeaderClick(clickedSortField, currentState) {
    const clickedFieldIsActive = clickedSortField === currentState.primaryField;

    const nextPrimaryDirection = clickedFieldIsActive
        ? (currentState.primaryDirection === "asc" ? "desc" : "asc")
        : DEFAULT_DIRECTION_FOR_FIELD[clickedSortField];

    return {
        ...currentState,
        primaryField:       clickedSortField,
        primaryDirection:   nextPrimaryDirection,
        secondaryField:     otherSortField(clickedSortField),
        secondaryDirection: "asc",
        pageNumber:         1    // reset to first page on any sort change
    };
}


function populateMovieTableWithResults(movieArray, state) {
    const movieTableBodyElement = jQuery("#movie-table-body");

    movieTableBodyElement.empty(); // Clear previous rows, so it replaces and not appends

    const rowNumberOffset = (state.pageNumber - 1) * state.pageSize;


    movieArray.forEach((movie, indexWithinPage) => {
        const starsHTML = (movie.stars || []).map(star => {
            return `<a href="single-star.html?id=${star.star_id}">${star.star_name}</a>`;
        }).join(", ");

        const ratingDisplay =
            (movie.movie_rating === null || movie.movie_rating === undefined)
                ? "N/A"
                : movie.movie_rating;

        const posterHTML = movie.movie_poster_thumbnail_url
            ? `<img src="${movie.movie_poster_thumbnail_url}" alt="${movie.movie_title} poster" class="poster-thumbnail">`
            : `<span class="poster-placeholder">N/A</span>`;

        const directorHTML = movie.movie_director_id
            ? `<a href="single-director.html?id=${movie.movie_director_id}">${movie.movie_director}</a>`
            : movie.movie_director;

        const rowHTML = `
            <tr>
                <td>${rowNumberOffset + indexWithinPage + 1}</td>
                <td>${posterHTML}</td>
                <td>
                    <a href="single-movie.html?id=${movie.movie_id}">
                        ${movie.movie_title}
                    </a>
                </td>
                <td>${movie.movie_year}</td>
                <td>${directorHTML}</td>
                <td>${ratingDisplay}</td>
                <td>${(movie.genres || []).map(g => `<a href="movie-list.html?genre=${encodeURIComponent(g)}">${g}</a>`).join(", ")}</td>
                <td>${starsHTML}</td>
            </tr>`;

        movieTableBodyElement.append(rowHTML);
    });
}

function fetchMovieListAndRender(state) {
    const requestData = {
        primarySortField:       state.primaryField,
        primarySortDirection:   state.primaryDirection,
        secondarySortField:     state.secondaryField,
        secondarySortDirection: state.secondaryDirection,
        pageSize:               state.pageSize,
        pageNumber:             state.pageNumber
    };

    if (state.title)     requestData.title      = state.title;
    if (state.year)      requestData.year       = state.year;
    if (state.director)  requestData.director   = state.director;
    if (state.star)      requestData.star       = state.star;
    if (state.genre)     requestData.genre      = state.genre;
    if (state.startChar) requestData["start-char"] = state.startChar;
    if (state.fulltext)  requestData.fulltext   = state.fulltext;

    jQuery.ajax({
        dataType: "json",
        method:   "GET",
        url:      "api/movie-list",
        data: requestData,
        success: (responseEnvelope) => {
            populateMovieTableWithResults(responseEnvelope.movies || [], state);
            updatePaginationControlsFromServerResponse(state, responseEnvelope.totalCount || 0);
        },
        error: (xhr) => {
            jQuery("#error").text("Failed to load movies: " + xhr.statusText);
        }
    });
}

function applyStateChangeAndRefetch(newState) {
    writeStateToUrl(newState);
    updateHeaderSortIndicatorIcons(newState);
    updatePageSizeDropdownSelection(newState);
    fetchMovieListAndRender(newState);
}

function attachClickHandlersToSortableHeaders() {
    jQuery(".sortable-header").on("click", function () {
        const clickedSortField = jQuery(this).data("sort-field");
        const currentState     = readCurrentStateFromUrl();
        const nextState        = computeNextStateForHeaderClick(clickedSortField, currentState);
        applyStateChangeAndRefetch(nextState);
    });
}

function attachChangeHandlerToPageSizeDropdown() {
    jQuery("#page-size-select").on("change", function () {
        const currentState = readCurrentStateFromUrl();
        const newPageSize  = parseInt(jQuery(this).val(), 10);
        const nextState = {
            ...currentState,
            pageSize:   newPageSize,
            pageNumber: 1
        };
        applyStateChangeAndRefetch(nextState);
    });
}

function attachClickHandlersToPaginationButtons() {
    jQuery(".previous-page-button").on("click", function () {
        const currentState = readCurrentStateFromUrl();
        if (currentState.pageNumber <= 1) {
            return;
        }
        const nextState = { ...currentState, pageNumber: currentState.pageNumber - 1 };
        applyStateChangeAndRefetch(nextState);
    });

    jQuery(".next-page-button").on("click", function () {
        const currentState = readCurrentStateFromUrl();
        const nextState = { ...currentState, pageNumber: currentState.pageNumber + 1 };
        applyStateChangeAndRefetch(nextState);
    });
}


jQuery(function () {
    const initialState = readCurrentStateFromUrl();

    updateHeaderSortIndicatorIcons(initialState);
    updatePageSizeDropdownSelection(initialState);

    attachClickHandlersToSortableHeaders();
    attachChangeHandlerToPageSizeDropdown();
    attachClickHandlersToPaginationButtons();

    fetchMovieListAndRender(initialState);
});