package com.company.procurement.shared.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** In-memory storage used by tests and when MinIO is explicitly disabled. */
@Component
@ConditionalOnProperty(prefix = "pams.storage", name = "enabled", havingValue = "false")
public class InMemoryFileStorageAdapter implements FileStoragePort {

  private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

  @Override
  public void put(String key, byte[] content, String contentType) {
    objects.put(key, content.clone());
  }

  @Override
  public InputStream get(String key) {
    byte[] content = objects.get(key);
    if (content == null) {
      throw new FileStorageException("Stored object is missing", null);
    }
    return new ByteArrayInputStream(content);
  }

  @Override
  public void delete(String key) {
    objects.remove(key);
  }
}
