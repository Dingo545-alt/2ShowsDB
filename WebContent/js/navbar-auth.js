// ---- Navbar login/logout visibility ----
// Included on every page that has .login-link / .logout-link

jQuery(document).ready(function () {
    const $loginLink = jQuery(".login-link");
    const $logoutLink = jQuery(".logout-link");
    if (!$loginLink.length && !$logoutLink.length) return; // navbar not present on this page

    jQuery.ajax({
        url: "api/session-status",
        type: "GET",
        dataType: "json",
        success: function (data) {
            $loginLink.prop("hidden", !!data.loggedIn);
            $logoutLink.prop("hidden", !data.loggedIn);
        }
    });
});