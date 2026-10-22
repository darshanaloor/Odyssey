package com.odyssey.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnectionFailureListener implements ApplicationListener<ApplicationFailedEvent> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionFailureListener.class);

    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable cause = event.getException();
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Communications link failure") ||
                    msg.contains("Access denied for user") ||
                    msg.contains("Connection refused") ||
                    msg.contains("Cannot create PoolableConnectionFactory") ||
                    msg.contains("Unable to obtain connection to query metadata"))) {
                log.error("Cannot connect to MySQL at localhost:3306/odyssey, check DB_USER and DB_PASSWORD");
                break;
            }
            cause = cause.getCause();
        }
    }
}
