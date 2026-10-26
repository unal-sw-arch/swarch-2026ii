package com.parosurvivors.serviya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServiyaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiyaApplication.class, args);
	}

}
