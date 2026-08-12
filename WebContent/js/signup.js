jQuery(document).ready(function() {
   const signupFormElement = jQuery("#signup-form");
   const signupErrorMessageElement = jQuery("#signup-error-message");

   signupFormElement.on("submit", function(event) {
      event.preventDefault();

      jQuery.ajax({
          url: "api/signup",
          type: "POST",
          data: signupFormElement.serialize(),
          dataType: "json",
          success: function(data) {
              window.location.href = "main.html";
          },
          error: function(xhr) {
              const data = JSON.parse(xhr.responseText);
              signupErrorMessageElement.text(data.message);
          }
      });
   });
});