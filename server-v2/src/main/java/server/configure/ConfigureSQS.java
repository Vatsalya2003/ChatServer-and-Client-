package server.configure;

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
 * AWS SQS Configuration with connection pooling
 */
@Configuration
public class ConfigureSQS {

    @Value("${aws.region}")
    private String awsRegion;

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                //Set AWS Region to my US-West-2 Region
                .region(Region.of(awsRegion))
                //authentication with AWS using my LabRole
                .credentialsProvider(DefaultCredentialsProvider.create())
                //Connection Pooling Http upto 100 connection to sqs
                //also resue connection instead of making new if needed
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(100)
                        //waiting for 10 seconds after starting connection
                        //if not connected in 10 sec gives time error
                        .connectionTimeout(Duration.ofSeconds(10))
                        //waiting for 30 sec for data afte connection starting
                        // if no response after 30 sec gives time error
                        .socketTimeout(Duration.ofSeconds(30))
                )
                // if sqs failes auto try for 3 seeconds
                // handles network glitches
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                        //alloweed 30 sec for sqs operations which also include reties, processing, connection every things
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .build()
                )
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}