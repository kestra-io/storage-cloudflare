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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeletedObject;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete objects from Cloudflare R2.",
    description = "Deletes one or multiple objects from an R2 bucket."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete objects from an R2 bucket",
            full = true,
            code = """
                id: r2_delete
                namespace: company.team

                tasks:
                  - id: delete
                    type: io.kestra.plugin.cloudflare.storage.Delete
                    accountId: "{{ secret('CF_ACCOUNT_ID') }}"
                    accessKeyId: "{{ secret('CF_ACCESS_KEY_ID') }}"
                    secretAccessKey: "{{ secret('CF_SECRET_ACCESS_KEY') }}"
                    bucket: my-bucket
                    keys:
                      - data/file1.csv
                      - data/file2.csv
                """
        )
    }
)
public class Delete extends AbstractR2 implements RunnableTask<Delete.Output> {

    @Schema(title = "List of object keys to delete.")
    @NotNull
    private Property<List<String>> keys;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var keysValue = runContext.render(keys).asList(String.class);
        var bucketValue = runContext.render(bucket).as(String.class).orElseThrow();

        List<String> deleted;

        try (S3Client client = s3Client(runContext)) {
            if (keysValue.size() == 1) {
                var request = DeleteObjectRequest.builder()
                    .bucket(bucketValue)
                    .key(keysValue.getFirst())
                    .build();
                client.deleteObject(request);
                deleted = keysValue;
            } else {
                var identifiers = keysValue.stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
                var request = DeleteObjectsRequest.builder()
                    .bucket(bucketValue)
                    .delete(software.amazon.awssdk.services.s3.model.Delete.builder().objects(identifiers).build())
                    .build();
                var response = client.deleteObjects(request);
                deleted = response.deleted().stream().map(DeletedObject::key).toList();
            }
        }

        runContext.logger().info("Deleted {} object(s) from r2://{}", deleted.size(), bucketValue);

        return Output.builder()
            .deletedKeys(deleted)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Keys of the deleted objects.")
        private final List<String> deletedKeys;
    }
}
