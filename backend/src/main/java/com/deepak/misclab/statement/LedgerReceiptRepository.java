package com.deepak.misclab.statement;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerReceiptRepository extends JpaRepository<LedgerReceipt, Long> {

    /** BEFORE: 1 query for the page of receipts; touching r.getLines() later fires 1 more query PER receipt (N+1). */
    List<LedgerReceipt> findByPayerIdOrderByCreatedAtDescIdDesc(long payerId, Pageable page);

    /** AFTER, step 1: just the ids of the latest N receipts (LIMIT runs in SQL, served by the composite index). */
    @Query("select r.id from LedgerReceipt r where r.payerId = :payerId order by r.createdAt desc, r.id desc")
    List<Long> findLatestIds(@Param("payerId") long payerId, Pageable page);

    /**
     * AFTER, step 2: those receipts and all their lines in ONE query (fetch join). Paging is done in step 1 on purpose:
     * a fetch join + LIMIT would make Hibernate page in memory (HHH90003004) because the join multiplies rows.
     */
    @Query("select distinct r from LedgerReceipt r left join fetch r.lines where r.id in :ids")
    List<LedgerReceipt> findWithLines(@Param("ids") Collection<Long> ids);
}
