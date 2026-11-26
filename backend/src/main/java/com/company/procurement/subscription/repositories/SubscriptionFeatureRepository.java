package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionFeature;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence port for subscription features. */
public interface SubscriptionFeatureRepository {

  List<SubscriptionFeature> findAllOrdered();

  List<SubscriptionFeature> findActiveOrdered();

  List<SubscriptionFeature> findByCodeIn(Collection<String> codes);

  Optional<SubscriptionFeature> findById(String code);

  SubscriptionFeature save(SubscriptionFeature feature);

  boolean existsById(String code);
}
