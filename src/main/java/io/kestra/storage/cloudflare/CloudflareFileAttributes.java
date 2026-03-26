package io.kestra.storage.cloudflare;

import io.kestra.core.storages.FileAttributes;
import lombok.Builder;
import lombok.Value;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.Map;

@Value
public class CloudflareFileAttributes implements FileAttributes {
    String fileName;
    HeadObjectResponse head;
    boolean isDirectory;
    Map<String, String> metadata;

    @Builder
    public CloudflareFileAttributes(String fileName, HeadObjectResponse head, boolean isDirectory) {
        this.fileName = fileName;
        this.head = head;
        this.isDirectory = isDirectory;
        this.metadata = MetadataUtils.toRetrievedMetadata(head.metadata());
    }

    @Override
    public long getLastModifiedTime() {
        return head.lastModified().toEpochMilli();
    }

    // R2 (like S3) only tracks last modified, not a separate creation time.
    @Override
    public long getCreationTime() {
        return head.lastModified().toEpochMilli();
    }

    @Override
    public FileType getType() {
        if (isDirectory || fileName.endsWith("/") || "application/x-directory".equals(head.contentType())) {
            return FileType.Directory;
        }
        return FileType.File;
    }

    @Override
    public long getSize() {
        return head.contentLength();
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }
}
