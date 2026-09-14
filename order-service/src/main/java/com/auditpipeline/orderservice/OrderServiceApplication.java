package com.auditpipeline.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@SpringBootApplication
@PropertySources({
        @PropertySource("classpath:${spring.profiles.active}_auditpipeline.properties")
})
public class OrderServiceApplication {

    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    public static void main(String[] args) {
        // Windows reports the local timezone to Java as "Asia/Calcutta", an old
        // IANA alias newer Postgres builds no longer recognize as a valid session
        // TimeZone -- so this has to be corrected before Spring creates the
        // DataSource bean. That happens too early in the startup sequence for a
        // normal @Value-injected field to help (Spring beans aren't wired up
        // yet), so we read app.timezone from the same profile properties file
        // Spring itself will load, using a plain classpath lookup here instead.
        // The value still lives in exactly one place -- the properties file --
        // this is just an earlier read of it, not a second source of truth.
        System.setProperty("user.timezone", resolveTimezone());

        SpringApplication.run(OrderServiceApplication.class, args);
    }

    private static String resolveTimezone() {
        String activeProfile = System.getProperty("spring.profiles.active",
                System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "local"));
        String resourceName = activeProfile + "_auditpipeline.properties";

        Properties properties = new Properties();
        try (InputStream in = OrderServiceApplication.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Fall back to the default below -- this only affects the session
            // TimeZone GUC sent to Postgres, never application correctness.
        }
        return properties.getProperty("app.timezone", DEFAULT_TIMEZONE);
    }
}
