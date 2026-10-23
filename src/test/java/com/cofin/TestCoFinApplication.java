package com.cofin;

import org.springframework.boot.SpringApplication;

public class TestCoFinApplication {

	public static void main(String[] args) {
		SpringApplication.from(CoFinApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
