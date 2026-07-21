package Config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Central place to read secrets/config (DB credentials, API keys, etc.)
 *
 * Lookup order for a given key:
 *   1. Environment variable of the same name (e.g. MYSQL_PASS)
 *   2. config.properties in the project root (NOT committed to git — see config.properties.example)
 *
 * This means the same code works whether you run locally with a config.properties
 * file, or in an environment (CI, a server, a container) where you set real
 * environment variables instead.
 */
public class AppConfig {
    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;

        // Look for config.properties in the working directory first, then on the classpath,
        // so this works whether you run from an IDE, `java -jar`, or a servlet container.
        Path localFile = Path.of("config.properties");
        if (Files.exists(localFile)) {
            try (InputStream in = new FileInputStream(localFile.toFile())) {
                PROPS.load(in);
                return;
            } catch (IOException e) {
                System.err.println("[AppConfig] Found config.properties but couldn't read it: " + e.getMessage());
            }
        }

        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            System.err.println("[AppConfig] Couldn't read config.properties from classpath: " + e.getMessage());
        }
    }

    /** Returns the value for key, or null if it isn't set anywhere. */
    public static String get(String key) {
        load();
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        return PROPS.getProperty(key);
    }

    /** Returns the value for key, or defaultValue if it isn't set anywhere. */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the value for key, or throws a clear error if it's missing.
     * Use this for secrets that must never silently default (DB passwords, API keys).
     */
    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required config value: " + key + ". " +
                            "Set it as an environment variable, or add it to config.properties " +
                            "(copy config.properties.example to config.properties and fill it in)."
            );
        }
        return value;
    }
}