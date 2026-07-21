jQuery(document).ready(function() {
   const loginFormElement = jQuery("#login-form");
   const loginErrorMessageElement = jQuery("#login-error-message");

   loginFormElement.on("submit", function(event) {
      event.preventDefault();

      grecaptcha.ready(function() {
          grecaptcha.execute("6Lc92NAsAAAAAC_649M6F1UmMzM6X0gB6LB3Oz7d", {action: "login"}).then(function(token){
              let formData = loginFormElement.serialize();
              formData += "&g-recaptcha-response=" + encodeURIComponent(token);

              jQuery.ajax({
                  url: "api/login",
                  type: "POST",
                  data: formData,
                  dataType: "json",
                  success: function(data) {
                      if (data.type === "employee") {
                          window.location.href = "_dashboard/index.html";
                      } else {
                          window.location.href = "main.html";
                          console.log(data.type);
                      }
                  },
                  error: function(xhr) {
                      const data = JSON.parse(xhr.responseText);
                      loginErrorMessageElement.text(data.message);
                  }
              });
          });
      });
   });
});