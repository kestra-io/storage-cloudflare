package io.kestra.storage.cloudflare;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface CloudflareConfig {

    @Schema(title = "The R2 bucket where internal objects are stored.")
    @PluginProperty
    String getBucket();

    @Schema(title = "Cloudflare account ID used to build the R2 endpoint.")
    @PluginProperty
    String getAccountId();

    @Schema(
        title = "Object path prefix within the bucket.",
        description = "If set, all keys are stored under bucket/path."
    )
    @PluginProperty
    String getPath();

    @Schema(title = "R2 API token access key ID.")
    @PluginProperty
    String getAccessKeyId();

    @Schema(title = "R2 API token secret access key.")
    @PluginProperty
    String getSecretAccessKey();

    @Schema(
        title = "Override the R2 endpoint URL.",
        description = "Used for testing against a local S3-compatible server. Leave unset for production R2 use."
    )
    @PluginProperty
    String getEndpointOverride();
}
