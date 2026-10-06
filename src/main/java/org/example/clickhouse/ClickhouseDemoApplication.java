package org.example.clickhouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClickhouseDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClickhouseDemoApplication.class, args);
        System.out.println("ClickhouseDemoApplication Started");
    }
}
