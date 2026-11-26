package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionMrrSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence port for MRR snapshots. */
public interface SubscriptionMrrSnapshotRepository {

  Optional<SubscriptionMrrSnapshot> findByDate(LocalDate snapshotDate);

  List<SubscriptionMrrSnapshot> findAllOrdered();

  SubscriptionMrrSnapshot save(SubscriptionMrrSnapshot snapshot);
}
