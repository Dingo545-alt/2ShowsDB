const MAX_FAVORITES = 3;

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
}

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
            } else {
                jQuery("#profile-logged-out").prop("hidden", false);
            }
        },
        error: function () {
            jQuery("#profile-logged-out").prop("hidden", false);
        }
    });
});
