package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.Delegation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDelegationRepository extends JpaRepository<Delegation, UUID> {

  Optional<Delegation> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Query(
      """
      select count(d) > 0 from Delegation d
       where d.organizationId = :organizationId
         and d.delegatorUserId = :delegatorUserId
         and d.delegateUserId = :delegateUserId
         and d.active = true
         and d.startsAt <= :moment
         and d.endsAt > :moment
      """)
  boolean existsActiveDelegation(
      @Param("organizationId") UUID organizationId,
      @Param("delegatorUserId") UUID delegatorUserId,
      @Param("delegateUserId") UUID delegateUserId,
      @Param("moment") Instant moment);

  List<Delegation> findByOrganizationIdAndDelegatorUserIdOrderByStartsAtDesc(
      UUID organizationId, UUID delegatorUserId);
}
