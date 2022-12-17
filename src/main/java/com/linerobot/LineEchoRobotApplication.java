package com.linerobot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages="com.linerobot.*")
public class LineEchoRobotApplication {

	public static void main(String[] args) {
		SpringApplication.run(LineEchoRobotApplication.class, args);
	}

}
