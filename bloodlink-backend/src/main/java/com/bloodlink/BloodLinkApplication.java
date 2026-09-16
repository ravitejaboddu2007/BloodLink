package com.bloodlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BloodLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloodLinkApplication.class, args);
    }
}
