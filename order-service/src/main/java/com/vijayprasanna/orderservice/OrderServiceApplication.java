package com.vijayprasanna.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        // Windows reports the local timezone to Java as "Asia/Calcutta", an old
        // IANA alias that newer Postgres builds no longer recognize as a valid
        // session TimeZone. Force the current name before anything connects to
        // the database, so this works the same whether run via mvn, the packaged
        // jar, or an IDE run configuration.
        System.setProperty("user.timezone", "Asia/Kolkata");

        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
