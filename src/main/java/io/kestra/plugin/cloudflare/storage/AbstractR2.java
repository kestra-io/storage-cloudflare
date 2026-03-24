package io.kestra.plugin.cloudflare.storage;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.services.s3.S3Client;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractR2 extends Task {

    @Schema(title = "Cloudflare account ID.")
    @NotNull
    protected Property<String> accountId;

    @Schema(title = "R2 API access key ID.")
    @NotNull
    protected Property<String> accessKeyId;

    @Schema(title = "R2 API secret access key.")
    @NotNull
    protected Property<String> secretAccessKey;

    @Schema(title = "R2 bucket name.")
    @NotNull
    protected Property<String> bucket;

    @Schema(title = "Region. Defaults to auto.")
    @Builder.Default
    protected Property<String> region = Property.ofValue("auto");

    @Schema(
        title = "Custom endpoint URL.",
        description = "Overrides the default R2 endpoint. Useful for testing or S3-compatible alternatives."
    )
    protected Property<String> endpoint;

    protected S3Client s3Client(RunContext runContext) throws Exception {
        var account = runContext.render(accountId).as(String.class).orElseThrow();
        var keyId = runContext.render(accessKeyId).as(String.class).orElseThrow();
        var secret = runContext.render(secretAccessKey).as(String.class).orElseThrow();
        var reg = runContext.render(region).as(String.class).orElse("auto");
        var ep = runContext.render(endpoint).as(String.class).orElse(null);
        return R2ClientFactory.create(account, keyId, secret, reg, ep);
    }
}
