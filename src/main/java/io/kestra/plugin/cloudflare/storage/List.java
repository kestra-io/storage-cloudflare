package io.kestra.plugin.cloudflare.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List objects in a Cloudflare R2 bucket.",
    description = "Lists objects in an R2 bucket with optional prefix filtering."
)
@Plugin(
    examples = {
        @Example(
            title = "List objects in an R2 bucket",
            full = true,
            code = """
                id: r2_list
                namespace: company.team

                tasks:
                  - id: list
                    type: io.kestra.plugin.cloudflare.storage.List
                    accountId: "{{ secret('CF_ACCOUNT_ID') }}"
                    accessKeyId: "{{ secret('CF_ACCESS_KEY_ID') }}"
                    secretAccessKey: "{{ secret('CF_SECRET_ACCESS_KEY') }}"
                    bucket: my-bucket
                    prefix: data/
                """
        )
    }
)
public class List extends AbstractR2 implements RunnableTask<List.Output> {

    @Schema(title = "Prefix to filter listed objects.")
    private Property<String> prefix;

    @Schema(title = "Maximum number of objects to return.")
    @Builder.Default
    private Property<Integer> maxKeys = Property.ofValue(1000);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var bucketValue = runContext.render(bucket).as(String.class).orElseThrow();
        var prefixValue = runContext.render(prefix).as(String.class).orElse(null);
        var maxKeysValue = runContext.render(maxKeys).as(Integer.class).orElse(1000);

        java.util.List<ObjectSummary> objects = new ArrayList<>();

        try (S3Client client = s3Client(runContext)) {
            String continuationToken = null;
            do {
                var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketValue)
                    .maxKeys(Math.min(maxKeysValue - objects.size(), 1000));
                if (prefixValue != null) {
                    requestBuilder.prefix(prefixValue);
                }
                if (continuationToken != null) {
                    requestBuilder.continuationToken(continuationToken);
                }
                var response = client.listObjectsV2(requestBuilder.build());
                for (S3Object obj : response.contents()) {
                    objects.add(ObjectSummary.builder()
                        .key(obj.key())
                        .size(obj.size())
                        .lastModified(obj.lastModified())
                        .eTag(obj.eTag())
                        .storageClass(obj.storageClassAsString())
                        .build());
                }
                continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
            } while (continuationToken != null && objects.size() < maxKeysValue);
        }

        runContext.logger().info("Found {} object(s) in r2://{}/{}", objects.size(), bucketValue,
            prefixValue != null ? prefixValue : "");

        return Output.builder()
            .objects(objects)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Objects found in the bucket.")
        private final java.util.List<ObjectSummary> objects;
    }
}
