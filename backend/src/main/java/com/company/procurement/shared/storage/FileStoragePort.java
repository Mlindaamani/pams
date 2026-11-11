package com.company.procurement.shared.storage;

import java.io.InputStream;

/** Port for object storage (MinIO in dev/prod, in-memory for tests). */
public interface FileStoragePort {

  void put(String key, byte[] content, String contentType);

  InputStream get(String key);

  void delete(String key);
}
