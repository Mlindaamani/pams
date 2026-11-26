package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionFeature;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSubscriptionFeatureRepository
    extends JpaRepository<SubscriptionFeature, String> {

  List<SubscriptionFeature> findAllByOrderBySortOrderAscCodeAsc();

  List<SubscriptionFeature> findByActiveTrueOrderBySortOrderAscCodeAsc();

  List<SubscriptionFeature> findByCodeInOrderBySortOrderAscCodeAsc(Collection<String> codes);
}
