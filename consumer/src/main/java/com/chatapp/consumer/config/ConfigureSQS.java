package com.chatapp.consumer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * AWS SQS Configuration for Consumer
 * Creates SqsClient bean that will be used to poll messages from SQS queues
 */
@Configuration
public class ConfigureSQS {

    @Value("${aws.region}")
    private String awsRegion;

    /**
     * Class SqsClient for consuming messages from SQS
     */
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Creates ObjectMapper bean for JSON serialization and parsing
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}