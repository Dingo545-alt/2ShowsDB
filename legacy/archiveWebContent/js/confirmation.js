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

/**
 * Loads the most recent confirmed order from /api/confirmation and renders it.
 */
jQuery(document).ready(function () {
    jQuery.ajax({
        url: "api/confirmation",
        type: "GET",
        dataType: "json",
        success: function (data) {
            const items = data.items || [];
            const saleIds = data.saleIds || [];

            if (items.length === 0) {
                jQuery("#confirmation-empty").show();
                jQuery("#confirmation-table").hide();
                return;
            }

            const tbody = jQuery("#confirmation-body");
            tbody.empty();

            items.forEach(item => {
                const lineTotal = item.quantity * item.price;
                const row = jQuery("<tr>");
                row.append(jQuery("<td>").text(item.title || item.id));
                row.append(jQuery("<td>").text(item.quantity));
                row.append(jQuery("<td>").text("$" + item.price.toFixed(2)));
                row.append(jQuery("<td>").text("$" + lineTotal.toFixed(2)));
                tbody.append(row);
            });

            jQuery("#confirmation-total").text((data.total || 0).toFixed(2));
            jQuery("#confirmation-sale-ids").text(saleIds.join(", "));
        },
        error: function () {
            jQuery("#confirmation-empty").show();
            jQuery("#confirmation-table").hide();
        }
    });
});
