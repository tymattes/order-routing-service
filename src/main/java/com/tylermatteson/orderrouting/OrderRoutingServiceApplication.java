package com.tylermatteson.orderrouting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrderRoutingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderRoutingServiceApplication.class, args);
	}

}
