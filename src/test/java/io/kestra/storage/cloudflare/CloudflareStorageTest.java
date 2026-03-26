package io.kestra.storage.cloudflare;

import io.kestra.core.storage.StorageTestSuite;
import io.kestra.core.storages.StorageInterface;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudflareStorageTest extends StorageTestSuite {

    private static final String ACCESS_KEY = "testuser";
    private static final String SECRET_KEY = "testsecret";
    private static final int PORT = 7070;

    StorageInterface storageInterface;

    private static GenericContainer<?> versityGw;

    @BeforeAll
    void startVersityGw() throws IOException {
        versityGw = new GenericContainer<>(DockerImageName.parse("versity/versitygw:v1.3.1"))
            .withEnv("ROOT_ACCESS_KEY", ACCESS_KEY)
            .withEnv("ROOT_SECRET_KEY", SECRET_KEY)
            .withEnv("VGW_DATA_DIR", "/tmp/vgw-data")
            .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint(
                "sh", "-c", "mkdir -p /tmp/vgw-data && /usr/local/bin/docker-entrypoint.sh posix /tmp/vgw-data"
            ))
            .withExposedPorts(PORT)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
        versityGw.start();

        String endpoint = "http://" + versityGw.getHost() + ":" + versityGw.getMappedPort(PORT);

        storageInterface = CloudflareStorage.builder()
            .accountId("test")
            .accessKeyId(ACCESS_KEY)
            .secretAccessKey(SECRET_KEY)
            .bucket("kestra-unit-test")
            .endpointOverride(endpoint)
            .path("kestra")
            .build();
        storageInterface.init();
    }

    @AfterAll
    void stopVersityGw() {
        if (versityGw != null) {
            versityGw.stop();
        }
    }

    @BeforeEach
    void createBucket() {
        try {
            ((CloudflareStorage) storageInterface).createBucket();
        } catch (IOException ignored) {
        }
    }
}
