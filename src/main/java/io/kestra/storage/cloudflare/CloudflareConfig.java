package io.kestra.storage.cloudflare;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface CloudflareConfig {

    @Schema(title = "The R2 bucket where internal objects are stored.")
    @PluginProperty(group = "connection")
    String getBucket();

    @Schema(title = "Cloudflare account ID used to build the R2 endpoint.")
    @PluginProperty(group = "advanced")
    String getAccountId();

    @Schema(
        title = "Object path prefix within the bucket.",
        description = "If set, all keys are stored under bucket/path."
    )
    @PluginProperty(group = "advanced")
    String getPath();

    @Schema(title = "R2 API token access key ID.")
    @PluginProperty(group = "advanced")
    String getAccessKeyId();

    @Schema(title = "R2 API token secret access key.")
    @PluginProperty(group = "connection")
    String getSecretAccessKey();

    @Schema(
        title = "Override the R2 endpoint URL.",
        description = "Used for testing against a local S3-compatible server. Leave unset for production R2 use."
    )
    @PluginProperty(group = "advanced")
    String getEndpointOverride();
}
