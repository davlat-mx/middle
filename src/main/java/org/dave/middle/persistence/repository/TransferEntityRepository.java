package org.dave.middle.persistence.repository;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.TransferStatus;
import org.dave.middle.persistence.entity.TransferEntity;
import org.dave.middle.persistence.projection.TransferReportDto;
import org.dave.middle.persistence.projection.TransferSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TransferEntityRepository
        extends JpaRepository<TransferEntity, String>, JpaSpecificationExecutor<TransferEntity> {

    Page<TransferEntity> findByStatus(TransferStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"sender", "receiver", "events"})
    List<TransferEntity> findWithGraphByStatus(TransferStatus status);

    // @Query JPQL с конструкторной выборкой в DTO
    @Query("""
            select new org.dave.middle.persistence.projection.TransferReportDto(
                       t.id, t.status, t.money.amount, t.corridor.from, t.corridor.to)
            from TransferEntity t
            where t.corridor.from = :from
            """)
    List<TransferReportDto> reportByCorridorFrom(@Param("from") Country from);

    // interface-проекция + Pageable
    Page<TransferSummary> findSummaryByStatus(TransferStatus status, Pageable pageable);

    // @Query JPQL: суммарный оборот по коридору
    @Query("""
            select coalesce(sum(t.money.amount), 0)
            from TransferEntity t
            where t.corridor.from = :from and t.corridor.to = :to
            """)
    BigDecimal turnover(@Param("from") Country from, @Param("to") Country to);

    // native query — pg-специфичный путь
    @Query(value = "select count(*) from transfer where status = :status", nativeQuery = true)
    long countByStatusNative(@Param("status") String status);
}
