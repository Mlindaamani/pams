package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionMrrSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionMrrSnapshotRepositoryAdapter implements SubscriptionMrrSnapshotRepository {

  private final JpaSubscriptionMrrSnapshotRepository jpa;

  public SubscriptionMrrSnapshotRepositoryAdapter(JpaSubscriptionMrrSnapshotRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<SubscriptionMrrSnapshot> findByDate(LocalDate snapshotDate) {
    return jpa.findBySnapshotDate(snapshotDate);
  }

  @Override
  public List<SubscriptionMrrSnapshot> findAllOrdered() {
    return jpa.findAllByOrderBySnapshotDateAsc();
  }

  @Override
  public SubscriptionMrrSnapshot save(SubscriptionMrrSnapshot snapshot) {
    return jpa.save(snapshot);
  }
}
