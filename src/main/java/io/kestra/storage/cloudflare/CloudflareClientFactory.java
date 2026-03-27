package io.kestra.storage.cloudflare;

import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

public final class CloudflareClientFactory {

    // R2 does not use AWS-style regions; "auto" routes to the nearest location.
    private static final Region R2_REGION = Region.of("auto");

    private CloudflareClientFactory() {}

    public static S3Client getS3Client(final CloudflareConfig config) {
        return S3Client.builder()
            .httpClientBuilder(serviceDefaults -> ApacheHttpClient.builder().build())
            .endpointOverride(endpoint(config))
            .region(R2_REGION)
            .forcePathStyle(true)
            .credentialsProvider(credentials(config))
            .build();
    }

    public static S3AsyncClient getAsyncS3Client(final CloudflareConfig config) {
        return S3AsyncClient.crtBuilder()
            .endpointOverride(endpoint(config))
            .region(R2_REGION)
            .forcePathStyle(true)
            .credentialsProvider(credentials(config))
            .build();
    }

    private static URI endpoint(CloudflareConfig config) {
        if (config.getEndpointOverride() != null) {
            return URI.create(config.getEndpointOverride());
        }
        return URI.create("https://" + config.getAccountId() + ".r2.cloudflarestorage.com");
    }

    private static AwsCredentialsProvider credentials(final CloudflareConfig config) {
        if (StringUtils.isNotEmpty(config.getAccessKeyId()) &&
            StringUtils.isNotEmpty(config.getSecretAccessKey())) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey())
            );
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
