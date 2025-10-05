package com.souvik.ai.meeting_summarizer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client() {
        // This will load credentials from your AWS CLI default profile (~/.aws/credentials)
        return S3Client.builder()
                .region(Region.of("ap-south-1")) // replace with your actual bucket region
                .credentialsProvider(ProfileCredentialsProvider.create("default"))
                .build();
    }


 
    @PostConstruct
        public void checkAwsCreds() {
    System.out.println("AWS creds loaded from environment");
}
}
