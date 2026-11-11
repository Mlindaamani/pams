package com.company.procurement.shared.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pams.storage", name = "enabled", havingValue = "true")
public class MinioFileStorageAdapter implements FileStoragePort {

  private final StorageProperties properties;
  private final MinioClient client;

  public MinioFileStorageAdapter(StorageProperties properties) {
    this.properties = properties;
    this.client =
        MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .build();
  }

  @PostConstruct
  void ensureBucket() {
    try {
      boolean exists =
          client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
      if (!exists) {
        client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
      }
    } catch (Exception ex) {
      throw new FileStorageException("Could not initialise MinIO bucket", ex);
    }
  }

  @Override
  public void put(String key, byte[] content, String contentType) {
    try {
      client.putObject(
          PutObjectArgs.builder().bucket(properties.bucket()).object(key).stream(
                  new ByteArrayInputStream(content), content.length, -1)
              .contentType(contentType)
              .build());
    } catch (Exception ex) {
      throw new FileStorageException("Could not store attachment", ex);
    }
  }

  @Override
  public InputStream get(String key) {
    try {
      return client.getObject(
          GetObjectArgs.builder().bucket(properties.bucket()).object(key).build());
    } catch (Exception ex) {
      throw new FileStorageException("Could not read attachment", ex);
    }
  }

  @Override
  public void delete(String key) {
    try {
      client.removeObject(
          RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
    } catch (Exception ex) {
      throw new FileStorageException("Could not delete attachment", ex);
    }
  }
}
