package com.taemoi.project;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.taemoi.project.servicios.FicheroService;

@SpringBootApplication
public class TaeMoiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaeMoiApplication.class, args);
	}
	
	@Bean
	public CommandLineRunner init(FicheroService ficheroService) {
		return args -> {
			ficheroService.init();
		};
	}
}
