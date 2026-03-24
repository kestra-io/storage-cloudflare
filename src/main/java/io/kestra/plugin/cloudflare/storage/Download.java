package io.kestra.plugin.cloudflare.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Download an object from Cloudflare R2.",
    description = "Downloads an object from an R2 bucket into Kestra internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Download a file from an R2 bucket",
            full = true,
            code = """
                id: r2_download
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.cloudflare.storage.Download
                    accountId: "{{ secret('CF_ACCOUNT_ID') }}"
                    accessKeyId: "{{ secret('CF_ACCESS_KEY_ID') }}"
                    secretAccessKey: "{{ secret('CF_SECRET_ACCESS_KEY') }}"
                    bucket: my-bucket
                    key: data/output.csv
                """
        )
    }
)
public class Download extends AbstractR2 implements RunnableTask<Download.Output> {

    @Schema(title = "Object key to download from the R2 bucket.")
    @NotNull
    private Property<String> key;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var keyValue = runContext.render(key).as(String.class).orElseThrow();
        var bucketValue = runContext.render(bucket).as(String.class).orElseThrow();

        var tempFile = File.createTempFile("r2-download-", ".tmp");
        tempFile.deleteOnExit();

        try (S3Client client = s3Client(runContext)) {
            var request = GetObjectRequest.builder()
                .bucket(bucketValue)
                .key(keyValue)
                .build();
            try {
                client.getObject(request, ResponseTransformer.toFile(tempFile));
            } catch (NoSuchKeyException e) {
                throw new FileNotFoundException("Object not found: " + keyValue);
            }
        }

        var outputUri = runContext.storage().putFile(tempFile);
        runContext.logger().info("Downloaded r2://{}/{} to {}", bucketValue, keyValue, outputUri);

        return Output.builder()
            .uri(outputUri)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "URI of the downloaded file in Kestra internal storage.")
        private final URI uri;
    }
}
