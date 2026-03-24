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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Upload a file to Cloudflare R2.",
    description = "Reads a file from Kestra internal storage and uploads it to the specified R2 bucket."
)
@Plugin(
    examples = {
        @Example(
            title = "Upload a file to an R2 bucket",
            full = true,
            code = """
                id: r2_upload
                namespace: company.team

                tasks:
                  - id: upload
                    type: io.kestra.plugin.cloudflare.storage.Upload
                    accountId: "{{ secret('CF_ACCOUNT_ID') }}"
                    accessKeyId: "{{ secret('CF_ACCESS_KEY_ID') }}"
                    secretAccessKey: "{{ secret('CF_SECRET_ACCESS_KEY') }}"
                    bucket: my-bucket
                    from: "{{ outputs.previousTask.uri }}"
                    key: data/output.csv
                """
        )
    }
)
public class Upload extends AbstractR2 implements RunnableTask<Upload.Output> {

    @Schema(title = "URI of the file in Kestra internal storage.")
    @NotNull
    private Property<String> from;

    @Schema(title = "Destination object key in the R2 bucket.")
    @NotNull
    private Property<String> key;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var fromValue = runContext.render(from).as(String.class).orElseThrow();
        var keyValue = runContext.render(key).as(String.class).orElseThrow();
        var bucketValue = runContext.render(bucket).as(String.class).orElseThrow();

        var tempFile = File.createTempFile("r2-upload-", ".tmp");
        tempFile.deleteOnExit();

        try (InputStream is = runContext.storage().getFile(URI.create(fromValue));
             var fos = new FileOutputStream(tempFile)) {
            is.transferTo(fos);
        }

        try (S3Client client = s3Client(runContext)) {
            var request = PutObjectRequest.builder()
                .bucket(bucketValue)
                .key(keyValue)
                .build();
            client.putObject(request, RequestBody.fromFile(tempFile));
        }

        runContext.logger().info("Uploaded {} to r2://{}/{}", fromValue, bucketValue, keyValue);

        return Output.builder()
            .bucket(bucketValue)
            .key(keyValue)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Bucket where the object was uploaded.")
        private final String bucket;

        @Schema(title = "Object key in the bucket.")
        private final String key;
    }
}
