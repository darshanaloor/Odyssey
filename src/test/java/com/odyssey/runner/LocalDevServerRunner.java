package com.odyssey.runner;

import com.odyssey.OdysseyApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Properties;

public class LocalDevServerRunner {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("spring.datasource.url", "jdbc:h2:mem:odyssey;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        props.put("spring.datasource.driver-class-name", "org.h2.Driver");
        props.put("spring.datasource.username", "sa");
        props.put("spring.datasource.password", "");
        props.put("spring.jpa.hibernate.ddl-auto", "validate");
        props.put("server.port", "8085");

        new SpringApplicationBuilder(OdysseyApplication.class)
                .properties(props)
                .run(args);
    }
}
