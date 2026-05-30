package org.example.simplify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class SimplifyMoneyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimplifyMoneyServiceApplication.class, args);
    }
}

