function handleSingleMovieResults(resultData) {
    // Basic fields
    jQuery("#movie_title").text(resultData.title);
    jQuery("#movie_year").text(resultData.year);
    jQuery("#movie_rating").text(
        resultData.rating === null || resultData.rating === undefined
            ? "N/A"
            : resultData.rating
    );
    if (resultData.directorId) {
        jQuery("#movie_director").html(
            `<a href="single-director.html?id=${resultData.directorId}">${resultData.director}</a>`
        );
    } else {
        jQuery("#movie_director").text(resultData.director);
    }

    // Poster
    const posterUrl = resultData.poster ? resultData.poster.original : null;
    if (posterUrl) {
        jQuery("#movie_poster_image").attr("src", posterUrl).attr("alt", resultData.title + " poster").prop("hidden", false);
        jQuery("#movie_poster_placeholder").prop("hidden", true);
    } else {
        jQuery("#movie_poster_image").prop("hidden", true);
        jQuery("#movie_poster_placeholder").prop("hidden", false);
    }

    // Genres (displaying as tags)
    let genresHTML = resultData.genres.map(g =>
        `<a href="movie-list.html?genre=${encodeURIComponent(g.name)}" class="genres-tag">${g.name}</a>`
    );
    jQuery("#movie_genres").html(genresHTML);

    // Stars (displaying as list of links)
    let starsHTML = "";
    resultData.stars.forEach(star => {
        starsHTML += `<li><a href="single-star.html?id=${star.id}">${star.name}</a> (${star.movie_count} movies)</li>`;
    });
    jQuery("#movie_stars").html(starsHTML);
}

function markFavoriteButtonAsFavorited() {
    jQuery("#favorite-button")
        .text("★ Favorited")
        .prop("disabled", true);
}

function initFavoriteButton(movieId) {
    jQuery.ajax({
        dataType: "json",
        method: "GET",
        url: "api/session-status",
        success: (sessionData) => {
            if (!sessionData.loggedIn) return;

            jQuery("#favorite-button").prop("hidden", false);

            jQuery.ajax({
                dataType: "json",
                method: "GET",
                url: "api/favorites",
                success: (favorites) => {
                    if (favorites.some(favorite => favorite.id === movieId)) {
                        markFavoriteButtonAsFavorited();
                    }
                }
            });
        }
    });

    jQuery("#favorite-button").on("click", function () {
        jQuery.ajax({
            dataType: "json",
            method: "POST",
            url: "api/favorites",
            data: { movieId: movieId },
            success: () => markFavoriteButtonAsFavorited(),
            error: (xhr) => {
                const data = JSON.parse(xhr.responseText);
                alert(data.message || "Could not add movie to favorites.");
            }
        });
    });
}

function restoreBackToMovieListButton() {
    jQuery.ajax({
        dataType: "json",
        method: "GET",
        url: "api/movie-list-state",
        success: (data) => {
            const savedQuery = data.query || "";
            const href = savedQuery
                ? "movie-list.html?" + savedQuery
                : "movie-list.html";
            jQuery("#back-to-movie-list").attr("href", href);
        }
    });
}

restoreBackToMovieListButton();

// Retrieve parameter "id" from HTML URL starting from "?"
let movie_id = new URLSearchParams(window.location.search).get("id");

initFavoriteButton(movie_id);

// Make AJAX call using ID
jQuery.ajax({
    dataType: "json",
    method: "GET",
    url: "api/single-movie?id=" + movie_id,
    success: (resultData) => handleSingleMovieResults(resultData)
});