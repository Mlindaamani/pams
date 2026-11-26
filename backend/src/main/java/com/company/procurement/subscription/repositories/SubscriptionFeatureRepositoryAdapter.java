package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionFeature;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionFeatureRepositoryAdapter implements SubscriptionFeatureRepository {

  private final JpaSubscriptionFeatureRepository jpa;

  public SubscriptionFeatureRepositoryAdapter(JpaSubscriptionFeatureRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<SubscriptionFeature> findAllOrdered() {
    return jpa.findAllByOrderBySortOrderAscCodeAsc();
  }

  @Override
  public List<SubscriptionFeature> findActiveOrdered() {
    return jpa.findByActiveTrueOrderBySortOrderAscCodeAsc();
  }

  @Override
  public List<SubscriptionFeature> findByCodeIn(Collection<String> codes) {
    return jpa.findByCodeInOrderBySortOrderAscCodeAsc(codes);
  }

  @Override
  public Optional<SubscriptionFeature> findById(String code) {
    return jpa.findById(code);
  }

  @Override
  public SubscriptionFeature save(SubscriptionFeature feature) {
    return jpa.save(feature);
  }

  @Override
  public boolean existsById(String code) {
    return jpa.existsById(code);
  }
}
