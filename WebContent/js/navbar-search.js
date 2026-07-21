// ---- Navbar full-text search + autocomplete ----
// Included on every page that has .navbar-search

(function () {
    const AC_CACHE_PREFIX = "ac_";
    const AC_MIN_CHARS    = 3;
    const AC_DEBOUNCE_MS  = 300;

    let acDebounceTimer = null;
    let acActiveIndex   = -1;
    let acSuggestions   = [];

    function acCacheGet(query) {
        try {
            const raw = sessionStorage.getItem(AC_CACHE_PREFIX + query);
            return raw ? JSON.parse(raw) : null;
        } catch { return null; }
    }

    function acCacheSet(query, suggestions) {
        try {
            sessionStorage.setItem(AC_CACHE_PREFIX + query, JSON.stringify(suggestions));
        } catch {}
    }

    function showDropdown(suggestions) {
        acSuggestions = suggestions;
        acActiveIndex = -1;
        const $ul = jQuery("#navbar-autocomplete-dropdown");
        $ul.empty();

        if (!suggestions.length) {
            $ul.prop("hidden", true);
            return;
        }

        suggestions.forEach((item) => {
            jQuery("<li>")
                .text(item.title)
                .on("mousedown", function (e) {
                    e.preventDefault();
                    window.location.href = "single-movie.html?id=" + encodeURIComponent(item.id);
                })
                .appendTo($ul);
        });

        $ul.prop("hidden", false);
    }

    function hideDropdown() {
        jQuery("#navbar-autocomplete-dropdown").prop("hidden", true);
        acActiveIndex = -1;
    }

    function highlightItem(newIndex) {
        const $items = jQuery("#navbar-autocomplete-dropdown li");
        $items.removeClass("ac-active");
        if (newIndex >= 0 && newIndex < $items.length) {
            $items.eq(newIndex).addClass("ac-active");
            jQuery("#navbar-search-input").val(acSuggestions[newIndex].title);
        }
        acActiveIndex = newIndex;
    }

    function doFullTextSearch() {
        const query = jQuery("#navbar-search-input").val().trim();
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
                if (jQuery("#navbar-search-input").val().trim() === query) {
                    showDropdown(suggestions);
                }
            },
            error: hideDropdown
        });
    }

    function onInputChange() {
        clearTimeout(acDebounceTimer);
        const query = jQuery("#navbar-search-input").val().trim();

        if (query.length < AC_MIN_CHARS) {
            hideDropdown();
            return;
        }

        acDebounceTimer = setTimeout(() => fetchSuggestions(query), AC_DEBOUNCE_MS);
    }

    jQuery(document).ready(function () {
        const $input = jQuery("#navbar-search-input");
        if (!$input.length) return; // navbar not present on this page

        $input.on("input", onInputChange);

        $input.on("keydown", function (e) {
            const $items = jQuery("#navbar-autocomplete-dropdown li");
            const isOpen = !jQuery("#navbar-autocomplete-dropdown").prop("hidden");

            if (e.key === "ArrowDown") {
                e.preventDefault();
                if (isOpen) highlightItem(Math.min(acActiveIndex + 1, $items.length - 1));
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                if (isOpen) highlightItem(Math.max(acActiveIndex - 1, 0));
            } else if (e.key === "Enter") {
                e.preventDefault();
                if (isOpen && acActiveIndex >= 0) {
                    window.location.href = "single-movie.html?id=" +
                        encodeURIComponent(acSuggestions[acActiveIndex].id);
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

        jQuery("#navbar-search-button").on("click", doFullTextSearch);
    });
})();