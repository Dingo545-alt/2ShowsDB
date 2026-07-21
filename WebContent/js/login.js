jQuery(document).ready(function() {
   const loginFormElement = jQuery("#login-form");
   const loginErrorMessageElement = jQuery("#login-error-message");

   loginFormElement.on("submit", function(event) {
      event.preventDefault();

      grecaptcha.ready(function() {
          grecaptcha.execute("6LcoYF0tAAAAACq-8RtzbWEFBoBsHBgbXt21noNY", {action: "login"}).then(function(token){
              let formData = loginFormElement.serialize();
              formData += "&g-recaptcha-response=" + encodeURIComponent(token);

              jQuery.ajax({
                  url: "api/login",
                  type: "POST",
                  data: formData,
                  dataType: "json",
                  success: function(data) {
                      window.location.href = "main.html";
                      console.log(data.type);
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