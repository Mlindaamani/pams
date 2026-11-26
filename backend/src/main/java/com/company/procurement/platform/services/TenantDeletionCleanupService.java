package com.company.procurement.platform.services;

import com.company.procurement.identity.services.KeycloakAdminPort;
import com.company.procurement.shared.storage.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Best-effort cleanup of Keycloak identities and stored files after tenant deletion. */
@Service
public class TenantDeletionCleanupService {

  private static final Logger log = LoggerFactory.getLogger(TenantDeletionCleanupService.class);

  private final KeycloakAdminPort keycloak;
  private final FileStoragePort storage;

  public TenantDeletionCleanupService(KeycloakAdminPort keycloak, FileStoragePort storage) {
    this.keycloak = keycloak;
    this.storage = storage;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void cleanup(TenantDeletedEvent event) {
    for (String subject : event.userSubjects()) {
      try {
        keycloak.deleteUser(subject);
      } catch (RuntimeException ex) {
        log.warn(
            "Could not delete Keycloak user {} after tenant {} deletion",
            subject,
            event.organizationId(),
            ex);
      }
    }
    for (String key : event.storageKeys()) {
      try {
        storage.delete(key);
      } catch (RuntimeException ex) {
        log.warn(
            "Could not delete object {} after tenant {} deletion", key, event.organizationId(), ex);
      }
    }
  }
}
