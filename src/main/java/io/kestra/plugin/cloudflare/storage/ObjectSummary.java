package io.kestra.plugin.cloudflare.storage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ObjectSummary {

    @Schema(title = "Object key.")
    private final String key;

    @Schema(title = "Object size in bytes.")
    private final Long size;

    @Schema(title = "Last modified timestamp.")
    private final Instant lastModified;

    @Schema(title = "ETag of the object.")
    private final String eTag;

    @Schema(title = "Storage class.")
    private final String storageClass;
}
