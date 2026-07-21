function buildGenreLinks() {
    jQuery.ajax({
        url: "api/genres",
        type: "GET",
        success: function(genres) {
            const $container = jQuery(".genres-container");
            genres.forEach(genre => {
                $container.append(jQuery("<a>", {
                    href:  "movie-list.html?genre=" + encodeURIComponent(genre),
                    text:  genre,
                    class: "genre-item"
                }));
                $container.append(" ");
            });
        }
    });
}

function buildTitleStartingLetterLinks() {
    const $container = jQuery(".titles-container");
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("").forEach(char => {
        $container.append(jQuery("<a>", {
            href:  "movie-list.html?start-char=" + char,
            text:  char,
            class: "alpha-link"
        }));
        $container.append(" ");
    });
}

// ---- Autocomplete ----

const AC_CACHE_PREFIX = "ac_";
const AC_MIN_CHARS    = 3;
const AC_DEBOUNCE_MS  = 300;

let acDebounceTimer = null;
let acActiveIndex   = -1;
let acSuggestions   = [];   // [{id, title}, ...]

function acCacheGet(query) {
    try {
        const raw = sessionStorage.getItem(AC_CACHE_PREFIX + query);
        return raw ? JSON.parse(raw) : null;
    } catch { return null; }
}

function acCacheSet(query, suggestions) {
    try {
        sessionStorage.setItem(AC_CACHE_PREFIX + query, JSON.stringify(suggestions));
    } catch { /* storage full — skip silently */ }
}

function showDropdown(suggestions) {
    acSuggestions = suggestions;
    acActiveIndex = -1;
    const $ul = jQuery("#autocomplete-dropdown");
    $ul.empty();

    if (!suggestions.length) {
        $ul.prop("hidden", true);
        return;
    }

    suggestions.forEach((item, idx) => {
        jQuery("<li>")
            .text(item.title)
            .attr("data-idx", idx)
            .on("mousedown", function (e) {
                e.preventDefault(); // keep focus until navigation
                goToMovie(item.id);
            })
            .appendTo($ul);
    });

    $ul.prop("hidden", false);
}

function hideDropdown() {
    jQuery("#autocomplete-dropdown").prop("hidden", true);
    acActiveIndex = -1;
}

function highlightItem(newIndex) {
    const $items = jQuery("#autocomplete-dropdown li");
    $items.removeClass("ac-active");
    if (newIndex >= 0 && newIndex < $items.length) {
        $items.eq(newIndex).addClass("ac-active");
        jQuery("#fulltext-input").val(acSuggestions[newIndex].title);
    }
    acActiveIndex = newIndex;
}

function goToMovie(movieId) {
    window.location.href = "single-movie.html?id=" + encodeURIComponent(movieId);
}

function doFullTextSearch() {
    const query = jQuery("#fulltext-input").val().trim();
    if (!query) return;
    window.location.href = "movie-list.html?fulltext=" + encodeURIComponent(query);
}

function fetchSuggestions(query) {
    const cached = acCacheGet(query);
    if (cached !== null) {
        showDropdown(cached);
        return;
    }

    jQuery.ajax({
        url:      "api/full-text-search",
        type:     "GET",
        data:     { q: query },
        dataType: "json",
        success: function (suggestions) {
            acCacheSet(query, suggestions);
            // Only render if the input still matches what we fetched
            if (jQuery("#fulltext-input").val().trim() === query) {
                showDropdown(suggestions);
            }
        },
        error: hideDropdown
    });
}

function onInputChange() {
    clearTimeout(acDebounceTimer);
    const query = jQuery("#fulltext-input").val().trim();

    if (query.length < AC_MIN_CHARS) {
        hideDropdown();
        return;
    }

    acDebounceTimer = setTimeout(() => fetchSuggestions(query), AC_DEBOUNCE_MS);
}

// ---- Bootstrap ----

jQuery(document).ready(function () {
    buildGenreLinks();
    buildTitleStartingLetterLinks();

    const $input = jQuery("#fulltext-input");

    $input.on("input", onInputChange);

    $input.on("keydown", function (e) {
        const $items = jQuery("#autocomplete-dropdown li");
        const isOpen = !jQuery("#autocomplete-dropdown").prop("hidden");

        if (e.key === "ArrowDown") {
            e.preventDefault();
            if (isOpen) highlightItem(Math.min(acActiveIndex + 1, $items.length - 1));
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            if (isOpen) highlightItem(Math.max(acActiveIndex - 1, 0));
        } else if (e.key === "Enter") {
            e.preventDefault();
            if (isOpen && acActiveIndex >= 0) {
                goToMovie(acSuggestions[acActiveIndex].id);
            } else {
                doFullTextSearch();
            }
        } else if (e.key === "Escape") {
            hideDropdown();
        }
    });

    $input.on("blur", function () {
        setTimeout(hideDropdown, 150);
    });

    jQuery("#fulltext-submit-button").on("click", doFullTextSearch);
});