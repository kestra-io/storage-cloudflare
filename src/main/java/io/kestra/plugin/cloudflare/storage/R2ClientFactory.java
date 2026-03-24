package io.kestra.plugin.cloudflare.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

public final class R2ClientFactory {

    private R2ClientFactory() {}

    /**
     * Build an S3Client targeting the given endpoint URI.
     * When endpoint is null, it defaults to the standard R2 endpoint derived from accountId.
     */
    public static S3Client create(String accountId, String accessKeyId, String secretAccessKey, String region, String endpoint) {
        var resolvedEndpoint = endpoint != null
            ? endpoint
            : "https://" + accountId + ".r2.cloudflarestorage.com";
        return S3Client.builder()
            .endpointOverride(URI.create(resolvedEndpoint))
            .region(Region.of(region != null ? region : "auto"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            ))
            .forcePathStyle(true)
            .httpClientBuilder(serviceDefaults -> ApacheHttpClient.builder().build())
            .build();
    }
}
