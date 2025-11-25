//package com.chatapp.consumer.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
//import software.amazon.awssdk.regions.Region;
//import software.amazon.awssdk.services.sqs.SqsClient;
//
///**
// * AWS SQS Configuration for Consumer
// * Creates SqsClient bean that will be used to poll messages from SQS queues
// */
//@Configuration
//public class ConfigureSQS {
//
//    @Value("${aws.region}")
//    private String awsRegion;
//
//    /**
//     * Class SqsClient for consuming messages from SQS
//     */
//    @Bean
//    public SqsClient sqsClient() {
//        return SqsClient.builder()
//                .region(Region.of(awsRegion))
//                .credentialsProvider(DefaultCredentialsProvider.create())
//                .build();
//    }
//
//    /**
//     * Creates ObjectMapper bean for JSON serialization and parsing
//     */
//    @Bean
//    public ObjectMapper objectMapper() {
//        return new ObjectMapper();
//    }
//}
package com.chatapp.consumer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.time.Duration;

/**
 * AWS SQS Configuration for Consumer - OPTIMIZED
 */
@Configuration
public class ConfigureSQS {

    @Value("${aws.region}")
    private String awsRegion;

    /**
     * Optimized SqsClient with connection pooling for high throughput
     */
    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                // CRITICAL: Connection pooling for 80 consumer threads
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(200)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(15))
                )
                // Fail fast, retry less
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(RetryPolicy.builder().numRetries(2).build())
                        .apiCallTimeout(Duration.ofSeconds(20))
                        .build()
                )
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}