package com.accesolibre;

import com.accesolibre.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AccesoLibreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccesoLibreApplication.class, args);
    }
}
