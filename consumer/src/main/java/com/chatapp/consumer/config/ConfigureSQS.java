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
     * Creates SqsClient bean for consuming messages from SQS
     * Uses DefaultCredentialsProvider which automatically finds LabRole on EC2
     */
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Creates ObjectMapper bean for JSON serialization/deserialization
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}