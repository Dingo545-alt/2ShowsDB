function renderCart() {
    jQuery.ajax({
        url: "api/shopping-cart",
        type: "GET",
        dataType: "json",
        success: function(cartItems) {
            const cartBody = jQuery("#cart-body");
            cartBody.empty();
            let grandTotal = 0;

            const isEmpty = !cartItems || cartItems.length === 0;
            jQuery("#cart-empty-message").toggle(isEmpty);
            jQuery("#cart-table").toggle(!isEmpty);
            jQuery("#proceed-to-payment-button").prop("disabled", isEmpty);

            if (!isEmpty) {
                cartItems.forEach(item => {
                    const lineTotal = item.quantity * item.price;
                    grandTotal += lineTotal;

                    const rowHTML = `
                        <tr>
                            <td>${item.title}</td>
                            <td>
                                <button class="cart-decrease-button" data-movie-id="${item.id}">-</button>
                                <span class="cart-quantity"> ${item.quantity} </span>
                                <button class="cart-increase-button" data-movie-id="${item.id}">+</button>
                            </td>
                            <td>$${item.price.toFixed(2)}</td>
                            <td>$${lineTotal.toFixed(2)}</td>
                            <td>
                                <button class="cart-delete-button" data-movie-id="${item.id}">Delete</button>
                            </td>
                        </tr>
                    `;
                    cartBody.append(rowHTML);
                });
            }
            jQuery("#grand-total").text(grandTotal.toFixed(2));
        },
        error: function (xhr) {
            console.error("Failed to load cart:", xhr.statusText);
        }
    });
}

function updateCart(movieId, action) {
    jQuery.ajax({
        url: "api/shopping-cart",
        type: "POST",
        data: {id: movieId, action: action},
        success: function() {
            renderCart();
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
    renderCart();

    jQuery("#cart-body").on("click", ".cart-increase-button", function () {
        updateCart(jQuery(this).data("movie-id"), "increase");
    });
    jQuery("#cart-body").on("click", ".cart-decrease-button", function () {
        updateCart(jQuery(this).data("movie-id"), "decrease");
    });
    jQuery("#cart-body").on("click", ".cart-delete-button", function () {
        updateCart(jQuery(this).data("movie-id"), "delete");
    });

    jQuery("#proceed-to-payment-button").on("click", function () {
        window.location.href = "payment.html";
    });
});