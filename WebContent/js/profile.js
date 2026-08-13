const MAX_FAVORITES = 3;

// ---- Reusable movie title search + dropdown ----
// Renders a search input into $container; calls onSelect(movie) when a result is picked.
function attachMovieSearch($container, onSelect) {
    const SEARCH_MIN_CHARS = 3;
    const SEARCH_DEBOUNCE_MS = 300;
    let debounceTimer = null;

    $container.html(`
        <div class="movie-search-wrapper">
            <input type="search" class="movie-search-input" placeholder="Search movie title…" autocomplete="off">
            <ul class="autocomplete-dropdown movie-search-dropdown" hidden></ul>
        </div>
    `);

    const $input = $container.find(".movie-search-input");
    const $dropdown = $container.find(".movie-search-dropdown");

    function hideDropdown() {
        $dropdown.empty().prop("hidden", true);
    }

    function showResults(results) {
        $dropdown.empty();
        if (!results.length) {
            hideDropdown();
            return;
        }
        results.forEach(movie => {
            jQuery("<li>")
                .text(movie.title)
                .on("mousedown", function (e) {
                    e.preventDefault(); // keep focus until onSelect runs
                    hideDropdown();
                    $input.val("");
                    onSelect(movie);
                })
                .appendTo($dropdown);
        });
        $dropdown.prop("hidden", false);
    }

    $input.on("input", function () {
        clearTimeout(debounceTimer);
        const query = $input.val().trim();
        if (query.length < SEARCH_MIN_CHARS) {
            hideDropdown();
            return;
        }
        debounceTimer = setTimeout(() => {
            jQuery.ajax({
                url: "api/full-text-search",
                type: "GET",
                data: { q: query },
                dataType: "json",
                success: showResults,
                error: hideDropdown
            });
        }, SEARCH_DEBOUNCE_MS);
    });

    $input.on("blur", function () {
        setTimeout(hideDropdown, 150);
    });
}

// ---- Favorites ----

function emptyFavoriteSlotHtml() {
    return `
        <div class="favorite-slot empty-slot">
            <span class="add-icon">+</span>
            <span class="slot-label">Add Favorite</span>
        </div>
    `;
}

function filledFavoriteSlotHtml(movie) {
    const posterUrl = movie.poster ? movie.poster.w342 : null;
    const posterHtml = posterUrl
        ? `<img class="favorite-poster" src="${posterUrl}" alt="${movie.title} poster">`
        : `<span class="favorite-poster favorite-poster-placeholder">No poster available</span>`;

    return `
        <div class="favorite-slot filled-slot">
            <a href="single-movie.html?id=${encodeURIComponent(movie.id)}">
                ${posterHtml}
                <div class="favorite-title">${movie.title} (${movie.year})</div>
            </a>
            <button class="favorite-button remove-favorite-button" data-movie-id="${movie.id}">Remove</button>
        </div>
    `;
}

function addFavoriteById(movieId) {
    jQuery.ajax({
        url: "api/favorites",
        type: "POST",
        data: { movieId: movieId },
        dataType: "json",
        success: function () {
            loadFavorites();
        },
        error: function (xhr) {
            const data = JSON.parse(xhr.responseText);
            alert(data.message || "Could not add movie to favorites.");
            loadFavorites();
        }
    });
}

function loadFavorites() {
    jQuery.ajax({
        url: "api/favorites",
        type: "GET",
        dataType: "json",
        success: function (favorites) {
            renderFavorites(favorites);
        }
    });
}

function renderFavorites(favorites) {
    const $grid = jQuery("#favorites-grid");
    $grid.empty();

    favorites.forEach(movie => $grid.append(filledFavoriteSlotHtml(movie)));
    for (let i = favorites.length; i < MAX_FAVORITES; i++) {
        $grid.append(emptyFavoriteSlotHtml());
    }

    $grid.find(".remove-favorite-button").on("click", function () {
        const movieId = jQuery(this).data("movie-id");
        jQuery.ajax({
            url: "api/favorites?movieId=" + encodeURIComponent(movieId),
            type: "DELETE",
            dataType: "json",
            success: function () {
                loadFavorites();
            }
        });
    });

    $grid.find(".empty-slot").on("click", function () {
        const $slot = jQuery(this);
        attachMovieSearch($slot, movie => addFavoriteById(movie.id));
        $slot.find(".movie-search-input").trigger("focus");
    });
}

// ---- Watch lists ----

function watchlistRowHtml(movie) {
    const posterUrl = movie.poster ? movie.poster.w342 : null;
    const posterHtml = posterUrl
        ? `<img class="watchlist-poster" src="${posterUrl}" alt="${movie.title} poster">`
        : `<span class="watchlist-poster watchlist-poster-placeholder">N/A</span>`;

    return `
        <tr>
            <td>${posterHtml}</td>
            <td class="watchlist-title-cell">
                <a href="single-movie.html?id=${encodeURIComponent(movie.id)}">${movie.title} (${movie.year})</a>
            </td>
            <td><button class="remove-watchlist-button" data-movie-id="${movie.id}">✕</button></td>
        </tr>
    `;
}

function renderWatchlistColumn(status, movies) {
    const $container = jQuery("#" + status + "-items");
    $container.empty();

    if (movies.length === 0) {
        $container.html('<p class="empty-state">No movies yet</p>');
        return;
    }

    const $table = jQuery('<table class="watchlist-table"><tbody></tbody></table>');
    const $tbody = $table.find("tbody");
    movies.forEach(movie => $tbody.append(watchlistRowHtml(movie)));
    $container.append($table);

    $table.find(".remove-watchlist-button").on("click", function () {
        const movieId = jQuery(this).data("movie-id");
        jQuery.ajax({
            url: "api/watchlist?movieId=" + encodeURIComponent(movieId),
            type: "DELETE",
            dataType: "json",
            success: function () {
                loadWatchlist();
            }
        });
    });
}

function loadWatchlist() {
    jQuery.ajax({
        url: "api/watchlist",
        type: "GET",
        dataType: "json",
        success: function (watchlist) {
            const byStatus = { watched: [], watching: [], plan_to_watch: [] };
            watchlist.forEach(movie => {
                if (byStatus[movie.status]) byStatus[movie.status].push(movie);
            });
            renderWatchlistColumn("watched", byStatus.watched);
            renderWatchlistColumn("watching", byStatus.watching);
            renderWatchlistColumn("plan_to_watch", byStatus.plan_to_watch);
        }
    });
}

// ---- Add-movie section (bottom of the watch lists) ----

function initAddMovieSection() {
    const $searchContainer = jQuery("#add-movie-search-container");
    const $picker = jQuery("#add-movie-status-picker");
    let selectedMovie = null;

    function resetAddMovieSection() {
        selectedMovie = null;
        $picker.prop("hidden", true);
        $searchContainer.show();
        attachMovieSearch($searchContainer, onMovieSelected);
    }

    function onMovieSelected(movie) {
        selectedMovie = movie;
        jQuery("#add-movie-selected-title").text(movie.title);
        $searchContainer.hide();
        $picker.prop("hidden", false);
    }

    $picker.find(".watch-status-button").on("click", function () {
        const status = jQuery(this).data("status");
        jQuery.ajax({
            url: "api/watchlist",
            type: "POST",
            data: { movieId: selectedMovie.id, status: status },
            dataType: "json",
            success: function () {
                resetAddMovieSection();
                loadWatchlist();
            },
            error: function (xhr) {
                const data = JSON.parse(xhr.responseText);
                alert(data.message || "Could not add movie.");
            }
        });
    });

    jQuery("#add-movie-cancel").on("click", resetAddMovieSection);

    resetAddMovieSection();
}

jQuery(document).ready(function () {
    jQuery.ajax({
        url: "api/session-status",
        type: "GET",
        dataType: "json",
        success: function (data) {
            if (data.loggedIn) {
                jQuery("#profile-username").text(data.username);
                jQuery("#profile-content").prop("hidden", false);
                loadFavorites();
                loadWatchlist();
                initAddMovieSection();
            } else {
                jQuery("#profile-logged-out").prop("hidden", false);
            }
        },
        error: function () {
            jQuery("#profile-logged-out").prop("hidden", false);
        }
    });
});
