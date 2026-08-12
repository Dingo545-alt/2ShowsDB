jQuery(document).ready(function () {
    jQuery.ajax({
        url: "api/session-status",
        type: "GET",
        dataType: "json",
        success: function (data) {
            if (data.loggedIn) {
                jQuery("#profile-username").text(data.username);
                jQuery("#profile-content").prop("hidden", false);
            } else {
                jQuery("#profile-logged-out").prop("hidden", false);
            }
        },
        error: function () {
            jQuery("#profile-logged-out").prop("hidden", false);
        }
    });
});
