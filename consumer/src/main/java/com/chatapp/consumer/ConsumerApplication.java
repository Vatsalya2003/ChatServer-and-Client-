package com.chatapp.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the SQS Consumer service.
 */
 @SpringBootApplication
public class ConsumerApplication {

/**
 * Application entry point.
 */
 public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
	}

}
