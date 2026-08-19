function handleSingleDirectorResult(resultData) {
    // Set name
    jQuery("#director_name").text(resultData.name);

    //Set dob
    jQuery("#director_dob").text(resultData.dob);

    // Biography
    jQuery("#director_biography").text(resultData.biography || "No biography available.");

    // Photo
    const photoUrl = resultData.photo ? resultData.photo.sizes.original : null;
    if (photoUrl) {
        jQuery("#director_photo_image").attr("src", photoUrl).attr("alt", resultData.name + " photo").prop("hidden", false);
        jQuery("#director_photo_placeholder").prop("hidden", true);
    } else {
        jQuery("#director_photo_image").prop("hidden", true);
        jQuery("#director_photo_placeholder").prop("hidden", false);
    }

    // Populate Movie Table
    let tableBodyElement = jQuery("#director_table_movie_body");
    resultData.movies.forEach(movie => {
        let rowHTML = `
            <tr>
                <td>
                    <a href="single-movie.html?id=${movie.id}">
                        ${movie.title}
                    </a>
                </td>
                <td>${movie.year}</td>
            </tr>`;
        tableBodyElement.append(rowHTML);
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

// Extract ID from URL and call api
let director_id = new URLSearchParams(window.location.search).get("id");
jQuery.ajax({
    dataType: "json",
    method: "GET",
    url: "api/single-director?id=" + director_id,
    success: (resultData) => handleSingleDirectorResult(resultData)
});