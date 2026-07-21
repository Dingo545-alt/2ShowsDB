import Config.AppConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

public class RecaptchaVerify {
    public static final String SITE_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    // Read from the RECAPTCHA_SECRET_KEY env var, or config.properties. See config.properties.example.
    private static final String SECRET_KEY = AppConfig.require("RECAPTCHA_SECRET_KEY");

    public static boolean verify(String gRecaptchaResponse) throws IOException {
        URL verifyURL = new URL(SITE_VERIFY_URL);
        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) verifyURL.openConnection();

        httpsURLConnection.setRequestMethod("POST");
        httpsURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
        httpsURLConnection.setRequestProperty("Accept", "en-US;q=0.5");

        String postParams = "secret=" + SECRET_KEY + "&response=" + gRecaptchaResponse;

        httpsURLConnection.setDoOutput(true);

        try (OutputStream outputStream = httpsURLConnection.getOutputStream()) {
            outputStream.write(postParams.getBytes());
            outputStream.flush();
        }

        try (InputStream inputStream = httpsURLConnection.getInputStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            JsonObject jsonObject = new Gson().fromJson(inputStreamReader, JsonObject.class);

            boolean success = jsonObject.get("success").getAsBoolean();
            if (!success) return false;

            double score = jsonObject.get("score").getAsDouble();
            String action = jsonObject.get("action").getAsString();
            return score >= 0.5 && "login".equals(action);
        }

    }
}