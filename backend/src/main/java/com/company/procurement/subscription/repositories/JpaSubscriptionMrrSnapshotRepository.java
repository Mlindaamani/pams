package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionMrrSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSubscriptionMrrSnapshotRepository
    extends JpaRepository<SubscriptionMrrSnapshot, UUID> {

  Optional<SubscriptionMrrSnapshot> findBySnapshotDate(LocalDate snapshotDate);

  List<SubscriptionMrrSnapshot> findAllByOrderBySnapshotDateAsc();
}
