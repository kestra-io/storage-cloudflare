package io.kestra.plugin.cloudflare.storage;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.FileWriter;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UploadTest {

    @Inject
    private RunContextFactory runContextFactory;

    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void upload_happyPath() throws Exception {
        wireMock.stubFor(put(urlPathEqualTo("/test-bucket/data/output.csv"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("ETag", "\"abc123\"")));

        var tempFile = File.createTempFile("upload-test-", ".csv");
        tempFile.deleteOnExit();
        try (var fw = new FileWriter(tempFile)) {
            fw.write("col1,col2\nval1,val2\n");
        }

        var runContext = runContextFactory.of();
        var storageUri = runContext.storage().putFile(tempFile);

        var task = Upload.builder()
            .id("upload-test")
            .type(Upload.class.getName())
            .accountId(Property.ofValue("test-account"))
            .accessKeyId(Property.ofValue("test-key"))
            .secretAccessKey(Property.ofValue("test-secret"))
            .bucket(Property.ofValue("test-bucket"))
            .from(Property.ofValue(storageUri.toString()))
            .key(Property.ofValue("data/output.csv"))
            .endpoint(Property.ofValue("http://localhost:" + wireMock.port()))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getBucket(), is("test-bucket"));
        assertThat(output.getKey(), is("data/output.csv"));

        wireMock.verify(putRequestedFor(urlPathEqualTo("/test-bucket/data/output.csv")));
    }
}
