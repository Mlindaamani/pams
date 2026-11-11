package com.company.procurement.shared.storage;

import com.company.procurement.shared.error.DomainException;
import com.company.procurement.shared.error.ErrorCodes;

public class FileStorageException extends DomainException {

  public FileStorageException(String message, Throwable cause) {
    super(ErrorCodes.FILE_STORAGE_ERROR, message);
    initCause(cause);
  }
}
