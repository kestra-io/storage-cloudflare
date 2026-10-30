package io.kestra.storage.cloudflare;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@KestraTest
class CloudflareStorageSchemaTest {
    @Inject
    private JsonSchemaGenerator jsonSchemaGenerator;

    @SuppressWarnings("unchecked")
    @Test
    void schema() {
        var schema = jsonSchemaGenerator.schemas(CloudflareStorage.class, false);

        var definition = (Map<String, Object>) ((Map<String, Object>) schema.get("definitions")).get(CloudflareStorage.class.getName());
        var properties = (Map<String, Object>) definition.get("properties");
        assertThat(properties, not(hasKey("s3Client")));
        assertThat(properties, not(hasKey("s3AsyncClient")));
    }
}
