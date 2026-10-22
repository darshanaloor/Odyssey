package com.odyssey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OdysseyApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(OdysseyApplication.class);
        app.addListeners(new com.odyssey.config.DatabaseConnectionFailureListener());
        app.run(args);
    }
}
