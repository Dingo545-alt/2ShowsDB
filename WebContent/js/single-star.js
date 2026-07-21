function handleSingleStarResult(resultData) {
    // Set name
    jQuery("#star_name").text(resultData.name);

    //Set dob
    jQuery("#star_dob").text(resultData.birth_year);

    // Populate Movie Table
    let tableBodyElement = jQuery("#star_table_movie_body");
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
let star_id = new URLSearchParams(window.location.search).get("id");
jQuery.ajax({
    dataType: "json",
    method: "GET",
    url: "api/single-star?id=" + star_id,
    success: (resultData) => handleSingleStarResult(resultData)
});