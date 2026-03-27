package io.kestra.storage.cloudflare;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Download;
import software.amazon.awssdk.transfer.s3.model.DownloadRequest;
import software.amazon.awssdk.transfer.s3.model.Upload;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Jacksonized
@Getter
@Plugin
@Plugin.Id("cloudflare")
@Slf4j
public class CloudflareStorage implements CloudflareConfig, StorageInterface {

    private static final Logger LOG = LoggerFactory.getLogger(CloudflareStorage.class);

    @NotEmpty
    private String bucket;

    @NotEmpty
    private String accountId;

    private String path;
    private String accessKeyId;
    private String secretAccessKey;
    private String endpointOverride;

    @Getter(AccessLevel.PRIVATE)
    private S3Client s3Client;

    @Getter(AccessLevel.PRIVATE)
    private S3AsyncClient s3AsyncClient;

    @Override
    public void init() {
        this.s3Client = CloudflareClientFactory.getS3Client(this);
        this.s3AsyncClient = CloudflareClientFactory.getAsyncS3Client(this);
    }

    @Override
    public String getPath(String tenantId, URI uri) {
        String basePath = StorageInterface.super.getPath(tenantId, uri);
        if (path == null) {
            return basePath;
        }
        return path + (path.endsWith("/") ? basePath : "/" + basePath);
    }

    @Override
    public boolean exists(String tenantId, @Nullable String namespace, URI uri) {
        return exists(getPath(tenantId, uri));
    }

    @Override
    public boolean existsInstanceResource(@Nullable String namespace, URI uri) {
        return exists(getPath(uri));
    }

    private boolean exists(String path) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(path).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public InputStream get(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return this.getWithMetadata(tenantId, namespace, uri).inputStream();
    }

    @Override
    public InputStream getInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        return this.getWithMetadata(getPath(uri)).inputStream();
    }

    @Override
    public StorageObject getWithMetadata(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return getWithMetadata(getPath(tenantId, uri));
    }

    private StorageObject getWithMetadata(String path) throws IOException {
        try {
            GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(path).build();
            ResponseInputStream<GetObjectResponse> result = s3Client.getObject(request);
            InputStream resultInputStream = result;
            if (result.response().contentLength() == 0) {
                result.close();
                resultInputStream = InputStream.nullInputStream();
            }
            return new StorageObject(
                MetadataUtils.toRetrievedMetadata(result.response().metadata()), resultInputStream
            );
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException();
        } catch (AwsServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<URI> allByPrefix(String tenantId, @Nullable String namespace, URI prefix, boolean includeDirectories) {
        String path = getPath(tenantId, prefix);
        return keysForPrefix(path, true, includeDirectories)
            .map(key -> URI.create("kestra://" + prefix.getPath() + key.substring(path.length())))
            .toList();
    }

    @Override
    public List<FileAttributes> list(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        String path = getPath(tenantId, uri);
        String prefix = path.endsWith("/") ? path : path + "/";
        try {
            List<FileAttributes> list = keysForPrefix(prefix, false, true)
                .map(throwFunction(this::getFileAttributes))
                .toList();
            if (list.isEmpty()) {
                this.getAttributes(tenantId, namespace, uri);
            }
            return list;
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException();
        } catch (AwsServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<FileAttributes> listInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        String path = getPath(uri);
        String prefix = path.endsWith("/") ? path : path + "/";
        prefix = prefix.equals("/") ? "" : prefix;
        try {
            List<FileAttributes> list = keysForPrefix(prefix, false, true)
                .map(throwFunction(this::getFileAttributes))
                .toList();
            if (list.isEmpty()) {
                this.getInstanceAttributes(namespace, uri);
            }
            return list;
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException();
        } catch (AwsServiceException e) {
            throw new IOException(e);
        }
    }

    private Stream<String> keysForPrefix(String prefix, boolean recursive, boolean includeDirectories) {
        List<String> allKeys = new ArrayList<>();
        String continuationToken = null;
        do {
            var requestBuilder = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }
            var response = s3Client.listObjectsV2(requestBuilder.build());
            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
            response.contents().stream()
                .map(S3Object::key)
                .filter(key -> {
                    String rel = key.substring(prefix.length());
                    return !rel.isEmpty()
                        && !Objects.equals(key, prefix)
                        && !rel.equals("/")
                        && (recursive || Path.of(rel).getParent() == null)
                        && (includeDirectories || !rel.endsWith("/"));
                })
                .forEach(allKeys::add);
        } while (continuationToken != null);
        return allKeys.stream();
    }

    @Override
    public FileAttributes getAttributes(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return getAttributes(getPath(tenantId, uri));
    }

    @Override
    public FileAttributes getInstanceAttributes(@Nullable String namespace, URI uri) throws IOException {
        return getAttributes(getPath(uri));
    }

    private FileAttributes getAttributes(String path) throws IOException {
        try {
            return getFileAttributes(path);
        } catch (FileNotFoundException e) {
            if (path.endsWith("/")) {
                throw e;
            }
            return getFileAttributes(path + "/");
        }
    }

    private FileAttributes getFileAttributes(String path) throws IOException {
        try {
            HeadObjectResponse head = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(path).build()
            );
            return CloudflareFileAttributes.builder()
                .fileName(Optional.ofNullable(Path.of(path).getFileName()).map(Path::toString).orElse("/"))
                .head(head)
                .isDirectory(path.endsWith("/"))
                .build();
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException();
        } catch (AwsServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public URI put(String tenantId, @Nullable String namespace, URI uri, StorageObject storageObject) throws IOException {
        put(storageObject, getPath(tenantId, uri));
        return createUri(uri.getPath());
    }

    @Override
    public URI putInstanceResource(@Nullable String namespace, URI uri, StorageObject storageObject) throws IOException {
        put(storageObject, getPath(uri));
        return createUri(uri.getPath());
    }

    private void put(StorageObject storageObject, String path) throws IOException {
        try (InputStream data = storageObject.inputStream()) {
            mkdirs(path);
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .metadata(MetadataUtils.toStoredMetadata(storageObject.metadata()))
                .build();

            Long length = (long) data.available();
            if (data instanceof ResponseInputStream<?> ri && ri.response() instanceof GetObjectResponse gor) {
                length = gor.contentLength();
            }
            if (length == Integer.MAX_VALUE) {
                length = null;
            }

            // use sync client for empty or unknown-length content to avoid multipart issues
            if (length != null && length == 0) {
                s3Client.putObject(request, RequestBody.empty());
                return;
            }

            try (S3TransferManager transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build()) {
                Upload upload = transferManager.upload(
                    UploadRequest.builder()
                        .putObjectRequest(request)
                        .requestBody(AsyncRequestBody.fromInputStream(data, length, Executors.newSingleThreadExecutor()))
                        .build()
                );
                upload.completionFuture().get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        } catch (AwsServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean delete(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        FileAttributes attrs;
        try {
            attrs = getAttributes(tenantId, namespace, uri);
        } catch (FileNotFoundException e) {
            return false;
        }
        if (attrs.getType() == FileAttributes.FileType.Directory) {
            deleteByPrefix(tenantId, namespace, uri.getPath().endsWith("/") ? uri : URI.create(uri + "/"));
        }
        return deleteSingleObject(getPath(tenantId, uri));
    }

    @Override
    public boolean deleteInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        FileAttributes attrs;
        try {
            attrs = getInstanceAttributes(namespace, uri);
        } catch (FileNotFoundException e) {
            return false;
        }
        String path = getPath(uri);
        if (attrs.getType() == FileAttributes.FileType.Directory) {
            deleteByPrefix(null, path.endsWith("/") ? path : path + "/");
        }
        return deleteSingleObject(path);
    }

    private boolean deleteSingleObject(String path) {
        boolean result = s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(bucket).key(path).build()
        ).sdkHttpResponse().isSuccessful();
        // clean up the parent directory marker if it is now empty
        String parentDir = path.contains("/") ? path.substring(0, path.lastIndexOf("/") + 1) : null;
        if (parentDir != null && exists(parentDir) && keysForPrefix(parentDir, false, false).findAny().isEmpty()) {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(parentDir).build());
        }
        return result;
    }

    @Override
    public URI createDirectory(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        createDirectory(getPath(tenantId, uri));
        return createUri(uri.getPath());
    }

    @Override
    public URI createInstanceDirectory(String namespace, URI uri) throws IOException {
        createDirectory(getPath(uri));
        return createUri(uri.getPath());
    }

    private void createDirectory(String path) throws IOException {
        if (!Strings.CS.endsWith(path, "/")) {
            path += "/";
        }
        mkdirs(path);
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(path).build(),
            RequestBody.empty()
        );
    }

    private void mkdirs(String path) throws IOException {
        if (!path.endsWith("/")) {
            path = path.substring(0, path.lastIndexOf("/") + 1);
        }
        if (exists(path)) {
            return;
        }
        String[] parts = path.split("/");
        StringBuilder agg = new StringBuilder();
        try {
            for (String part : parts) {
                agg.append(part).append("/");
                s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(agg.toString()).build(),
                    RequestBody.empty()
                );
            }
        } catch (AwsServiceException | SdkClientException e) {
            throw new IOException(e);
        }
    }

    @Override
    public URI move(String tenantId, @Nullable String namespace, URI from, URI to) throws IOException {
        String source = getPath(tenantId, from);
        String dest = getPath(tenantId, to);
        try {
            FileAttributes attrs = getAttributes(tenantId, namespace, from);
            if (attrs.getType() == FileAttributes.FileType.Directory) {
                String continuationToken = null;
                do {
                    var listBuilder = ListObjectsV2Request.builder().bucket(bucket).prefix(source);
                    if (continuationToken != null) {
                        listBuilder.continuationToken(continuationToken);
                    }
                    ListObjectsV2Response listResponse = s3Client.listObjectsV2(listBuilder.build());
                    continuationToken = listResponse.isTruncated() ? listResponse.nextContinuationToken() : null;
                    List<S3Object> objects = listResponse.contents();
                    if (objects.isEmpty() && continuationToken == null) {
                        throw new FileNotFoundException(to + " (Not Found)");
                    }
                    for (S3Object obj : objects) {
                        moveObject(obj.key(), dest + obj.key().substring(source.length()));
                    }
                } while (continuationToken != null);
            } else {
                moveObject(source, dest);
            }
            return createUri(to.getPath());
        } catch (AwsServiceException | SdkClientException e) {
            throw new IOException(e);
        }
    }

    private void moveObject(String oldKey, String newKey) {
        s3Client.copyObject(CopyObjectRequest.builder()
            .sourceBucket(bucket).sourceKey(oldKey)
            .destinationBucket(bucket).destinationKey(newKey)
            .build());
        deleteSingleObject(oldKey);
    }

    @Override
    public List<URI> deleteByPrefix(String tenantId, @Nullable String namespace, URI storagePrefix) throws IOException {
        return deleteByPrefix(tenantId, getPath(tenantId, storagePrefix));
    }

    private List<URI> deleteByPrefix(String tenantId, String path) throws IOException {
        ListObjectsResponse listing = s3Client.listObjects(
            ListObjectsRequest.builder().bucket(bucket).prefix(path).build()
        );

        @SuppressWarnings("unchecked")
        List<S3Object> objects = listing.getValueForField("Contents", List.class).get();

        List<ObjectIdentifier> keys = objects.stream()
            .map(S3Object::key)
            .map(k -> ObjectIdentifier.builder().key(k).build())
            .toList();

        if (keys.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            DeleteObjectsResponse result = s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(d -> d.objects(keys))
                    .build()
            );
            return result.deleted().stream()
                .map(DeletedObject::key)
                .map(k -> k.endsWith("/") ? k.substring(0, k.length() - 1) : k)
                .map(k -> createUri(removeTenant(tenantId, k)))
                .toList();
        } catch (AwsServiceException e) {
            throw new IOException(e);
        }
    }

    private static String removeTenant(String tenantId, String k) {
        return tenantId == null ? "/" + k : k.replaceFirst(tenantId, "");
    }

    private static URI createUri(String key) {
        return URI.create("kestra://%s".formatted(key));
    }

    @VisibleForTesting
    void createBucket() throws IOException {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (AwsServiceException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() {
        if (s3Client != null) {
            try { s3Client.close(); } catch (Exception e) { LOG.warn("Failed to close S3Client", e); }
        }
        if (s3AsyncClient != null) {
            try { s3AsyncClient.close(); } catch (Exception e) { LOG.warn("Failed to close S3AsyncClient", e); }
        }
    }
}
