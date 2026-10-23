package com.prismprd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PrismPrdApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrismPrdApplication.class, args);
    }
}
