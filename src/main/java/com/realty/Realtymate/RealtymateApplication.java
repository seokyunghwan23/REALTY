package com.realty.Realtymate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RealtymateApplication {

	public static void main(String[] args) {
		SpringApplication.run(RealtymateApplication.class, args);
		System.out.println("Let's inspect the beans provided by Spring Boot:");
	}

}
