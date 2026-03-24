package io.kestra.plugin.cloudflare.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when new objects appear in Cloudflare R2.",
    description = "Polls an R2 bucket and triggers an execution when objects modified since the last poll are found."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger on new objects in an R2 bucket",
            full = true,
            code = """
                id: r2_trigger
                namespace: company.team

                triggers:
                  - id: watch
                    type: io.kestra.plugin.cloudflare.storage.Trigger
                    accountId: "{{ secret('CF_ACCOUNT_ID') }}"
                    accessKeyId: "{{ secret('CF_ACCESS_KEY_ID') }}"
                    secretAccessKey: "{{ secret('CF_SECRET_ACCESS_KEY') }}"
                    bucket: my-bucket
                    prefix: incoming/
                    interval: PT1M

                tasks:
                  - id: process
                    type: io.kestra.core.tasks.log.Log
                    message: "{{ trigger.objects | length }} new object(s) found"
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Trigger.Output> {

    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Schema(title = "Cloudflare account ID.")
    @NotNull
    private Property<String> accountId;

    @Schema(title = "R2 API access key ID.")
    @NotNull
    private Property<String> accessKeyId;

    @Schema(title = "R2 API secret access key.")
    @NotNull
    private Property<String> secretAccessKey;

    @Schema(title = "R2 bucket name.")
    @NotNull
    private Property<String> bucket;

    @Schema(title = "Prefix to filter objects.")
    private Property<String> prefix;

    @Schema(
        title = "Custom endpoint URL.",
        description = "Overrides the default R2 endpoint. Useful for testing or S3-compatible alternatives."
    )
    private Property<String> endpoint;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();

        var accountIdValue = runContext.render(accountId).as(String.class).orElseThrow();
        var keyId = runContext.render(accessKeyId).as(String.class).orElseThrow();
        var secret = runContext.render(secretAccessKey).as(String.class).orElseThrow();
        var bucketValue = runContext.render(bucket).as(String.class).orElseThrow();
        var prefixValue = runContext.render(prefix).as(String.class).orElse(null);

        var since = context.getDate().minus(interval).toInstant();
        var ep = runContext.render(endpoint).as(String.class).orElse(null);

        List<ObjectSummary> newObjects = new ArrayList<>();

        try (S3Client client = R2ClientFactory.create(accountIdValue, keyId, secret, "auto", ep)) {
            String continuationToken = null;
            do {
                var requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketValue);
                if (prefixValue != null) {
                    requestBuilder.prefix(prefixValue);
                }
                if (continuationToken != null) {
                    requestBuilder.continuationToken(continuationToken);
                }
                var response = client.listObjectsV2(requestBuilder.build());
                for (S3Object obj : response.contents()) {
                    if (obj.lastModified().isAfter(since)) {
                        newObjects.add(ObjectSummary.builder()
                            .key(obj.key())
                            .size(obj.size())
                            .lastModified(obj.lastModified())
                            .eTag(obj.eTag())
                            .storageClass(obj.storageClassAsString())
                            .build());
                    }
                }
                continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
            } while (continuationToken != null);
        }

        if (newObjects.isEmpty()) {
            return Optional.empty();
        }

        runContext.logger().info("Found {} new object(s) in r2://{}", newObjects.size(), bucketValue);

        var execution = TriggerService.generateExecution(
            this,
            conditionContext,
            context,
            Output.builder().objects(newObjects).build()
        );
        return Optional.of(execution);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "Objects found in the bucket since the last poll.")
        private final List<ObjectSummary> objects;
    }
}
