package com.linerobot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages="com.linerobot.*")
@EnableScheduling
public class LineEchoRobotApplication {

	public static void main(String[] args) {
		SpringApplication.run(LineEchoRobotApplication.class, args);
	}

}
