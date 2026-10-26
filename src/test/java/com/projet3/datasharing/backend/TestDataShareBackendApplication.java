package com.projet3.datasharing.backend;

import org.springframework.boot.SpringApplication;

public class TestDataShareBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(DataShareBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
