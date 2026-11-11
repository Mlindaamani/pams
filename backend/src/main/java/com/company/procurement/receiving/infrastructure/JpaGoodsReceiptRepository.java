package com.company.procurement.receiving.infrastructure;

import com.company.procurement.receiving.domain.GoodsReceipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaGoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {

  @EntityGraph(attributePaths = "lines")
  @Query("select r from GoodsReceipt r where r.id = :id and r.organizationId = :organizationId")
  Optional<GoodsReceipt> findDetail(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  @EntityGraph(attributePaths = "lines")
  @Query(
      """
      select distinct r from GoodsReceipt r
      where r.organizationId = :organizationId and r.purchaseOrderId = :purchaseOrderId
      order by r.receivedDate desc, r.createdAt desc
      """)
  List<GoodsReceipt> findByPurchaseOrder(
      @Param("organizationId") UUID organizationId, @Param("purchaseOrderId") UUID purchaseOrderId);
}
