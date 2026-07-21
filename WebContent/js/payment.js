function loadOrderSummary() {
    jQuery.ajax({
        url: "api/payment",
        type: "GET",
        dataType: "json",
        success: function (data) {
            jQuery("#payment-total").text((data.total || 0).toFixed(2));
            jQuery("#payment-item-count").text(data.itemCount || 0);

            if (!data.itemCount || data.itemCount === 0) {
                jQuery("#payment-error-message")
                    .text("Your cart is empty. Add movies before placing an order.");
                jQuery("#place-order-button").prop("disabled", true);
            }
        },
        error: function () {
            jQuery("#payment-error-message").text("Could not load order summary.");
        }
    });
}

function submitPayment(event) {
    event.preventDefault();
    jQuery("#payment-error-message").text("");

    const formData = jQuery("#payment-form").serialize();

    jQuery.ajax({
        url: "api/payment",
        type: "POST",
        data: formData,
        dataType: "json",
        success: function (data) {
            if (data.status === "success") {
                window.location.href = "confirmation.html";
            } else {
                jQuery("#payment-error-message").text(data.message || "Payment failed.");
            }
        },
        error: function (xhr) {
            try {
                const data = JSON.parse(xhr.responseText);
                jQuery("#payment-error-message").text(data.message || "Payment failed.");
            } catch (e) {
                jQuery("#payment-error-message").text("Payment failed: " + xhr.statusText);
            }
        }
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

jQuery(document).ready(function () {
    loadOrderSummary();
    jQuery("#payment-form").on("submit", submitPayment);
});