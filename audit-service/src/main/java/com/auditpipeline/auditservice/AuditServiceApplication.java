package com.auditpipeline.auditservice;

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
public class AuditServiceApplication {

    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    public static void main(String[] args) {
        // Same fix as order-service -- see that class for the full explanation.
        System.setProperty("user.timezone", resolveTimezone());

        SpringApplication.run(AuditServiceApplication.class, args);
    }

    private static String resolveTimezone() {
        String activeProfile = System.getProperty("spring.profiles.active",
                System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "local"));
        String resourceName = activeProfile + "_auditpipeline.properties";

        Properties properties = new Properties();
        try (InputStream in = AuditServiceApplication.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Fall back to the default below.
        }
        return properties.getProperty("app.timezone", DEFAULT_TIMEZONE);
    }
}
