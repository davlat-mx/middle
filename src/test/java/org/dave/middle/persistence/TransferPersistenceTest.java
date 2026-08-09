package org.dave.middle.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.Currency;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.model.TransferStatus;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.domain.vo.Money;
import org.dave.middle.persistence.entity.ClientEntity;
import org.dave.middle.persistence.entity.CorridorEmbeddable;
import org.dave.middle.persistence.entity.MoneyEmbeddable;
import org.dave.middle.persistence.entity.TransferEntity;
import org.dave.middle.persistence.projection.TransferReportDto;
import org.dave.middle.persistence.projection.TransferSummary;
import org.dave.middle.persistence.repository.ClientEntityRepository;
import org.dave.middle.persistence.repository.TransferEntityRepository;
import org.dave.middle.persistence.spec.TransferSpecifications;
import org.dave.middle.repository.JpaTransferRepository;
import org.dave.middle.support.TestcontainersConfig;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class TransferPersistenceTest {

    @Autowired
    private TransferEntityRepository transfers;
    @Autowired
    private ClientEntityRepository clients;
    @Autowired
    private JpaTransferRepository domainRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private EntityManagerFactory emf;

    private ClientEntity alice;
    private ClientEntity bob;
    private ClientEntity carol;

    @BeforeEach
    void seed() {
        alice = clients.save(new ClientEntity("alice", "Alice", Country.UZ));
        bob = clients.save(new ClientEntity("bob", "Bob", Country.KZ));
        carol = clients.save(new ClientEntity("carol", "Carol", Country.RU));

        save("t-1", alice, bob, "100", Country.UZ, Country.KZ, TransferStatus.PERFORM);
        save("t-2", alice, bob, "300", Country.UZ, Country.KZ, TransferStatus.PERFORM);
        save("t-3", alice, carol, "500", Country.UZ, Country.RU, TransferStatus.SUCCESS);
        save("t-4", carol, bob, "200", Country.RU, Country.KZ, TransferStatus.FAILED);

        em.flush();
        em.clear();
    }

    private void save(String id, ClientEntity from, ClientEntity to,
                      String amount, Country cf, Country ct, TransferStatus status) {
        TransferEntity t = new TransferEntity();
        t.setId(id);
        t.setSender(from);
        t.setReceiver(to);
        t.setMoney(new MoneyEmbeddable(new BigDecimal(amount), Currency.USD));
        t.setCorridor(new CorridorEmbeddable(cf, ct));
        t.changeStatus(status, "seed");
        transfers.save(t);
    }

    @Test
    @DisplayName("Flyway поднял схему, метки времени заполнены")
    void flywayAndAuditing() {
        TransferEntity t = transfers.findById("t-1").orElseThrow();
        assertNotNull(t.getCreatedAt());
        assertNotNull(t.getUpdatedAt());
    }

    @Test
    @DisplayName("Specifications: динамическая фильтрация по статусу + сумме + коридору")
    void specifications() {
        Specification<TransferEntity> spec = Specification.allOf(
                TransferSpecifications.hasStatus(TransferStatus.PERFORM),
                TransferSpecifications.corridorFrom(Country.UZ),
                TransferSpecifications.amountBetween(new BigDecimal("150"), null));

        List<TransferEntity> found = transfers.findAll(spec);

        assertEquals(1, found.size());
        assertEquals("t-2", found.getFirst().getId());
    }

    @Test
    @DisplayName("Specifications: null-условие = нет фильтра")
    void specificationsSkipNulls() {
        Specification<TransferEntity> spec = Specification.allOf(
                TransferSpecifications.senderId(null),
                TransferSpecifications.hasStatus(null));

        assertEquals(4, transfers.findAll(spec).size());
    }

    @Test
    @DisplayName("Pageable + сортировка по убыванию суммы")
    void pageableSorted() {
        Page<TransferEntity> page = transfers.findByStatus(
                TransferStatus.PERFORM, PageRequest.of(0, 1, Sort.by("money.amount").descending()));

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(1, page.getContent().size());
        assertEquals("t-2", page.getContent().getFirst().getId()); // 300 > 100
    }

    @Test
    @DisplayName("Interface-проекция тянет только id/status/money")
    void interfaceProjection() {
        Page<TransferSummary> page = transfers.findSummaryByStatus(
                TransferStatus.SUCCESS, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        TransferSummary summary = page.getContent().getFirst();
        assertEquals("t-3", summary.getId());
        assertEquals(0, new BigDecimal("500.00").compareTo(summary.getMoney().getAmount()));
    }

    @Test
    @DisplayName("@Query JPQL: DTO по коридору отправления")
    void dtoQuery() {
        List<TransferReportDto> report = transfers.reportByCorridorFrom(Country.UZ);

        assertEquals(3, report.size()); // t-1, t-2, t-3
        assertTrue(report.stream().allMatch(dto -> dto.from() == Country.UZ));
    }

    @Test
    @DisplayName("@Query: оборот по коридору + native count по статусу")
    void aggregates() {
        assertEquals(0, new BigDecimal("400.00").compareTo(transfers.turnover(Country.UZ, Country.KZ)));
        assertEquals(2, transfers.countByStatusNative("PERFORM"));
    }

    @Test
    @DisplayName("@EntityGraph бьёт N+1: меньше запросов при обходе связей")
    void entityGraphBeatsNPlusOne() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        em.clear();
        stats.clear();
        transfers.findByStatus(TransferStatus.PERFORM, PageRequest.of(0, 10))
                .forEach(t -> t.getEvents().size()); // ленивый обход -> N+1
        long withoutGraph = stats.getPrepareStatementCount();

        em.clear();
        stats.clear();
        transfers.findWithGraphByStatus(TransferStatus.PERFORM)
                .forEach(t -> t.getEvents().size()); // связи уже подтянуты графом
        long withGraph = stats.getPrepareStatementCount();

        assertTrue(withGraph < withoutGraph,
                "ожидали меньше запросов с @EntityGraph: graph=" + withGraph + " plain=" + withoutGraph);
    }

    @Test
    @DisplayName("@Version: устаревшая версия -> OptimisticLockingFailureException")
    void optimisticLocking() {
        TransferEntity stale = transfers.findById("t-1").orElseThrow();
        em.detach(stale); // держим устаревшую копию (version 0)

        TransferEntity fresh = transfers.findById("t-1").orElseThrow();
        fresh.setStatus(TransferStatus.SUCCESS);
        transfers.saveAndFlush(fresh); // version 0 -> 1
        em.detach(fresh);

        stale.setStatus(TransferStatus.FAILED); // пишем поверх устаревшей версии
        assertThrows(OptimisticLockingFailureException.class, () -> transfers.saveAndFlush(stale));
    }

    @Test
    @DisplayName("Доменный порт JpaTransferRepository: save(домен) -> findById(домен)")
    void domainPortRoundTrip() {
        Transfer transfer = Transfer.create("d-1", "dave", "erin",
                Money.of("777.77", Currency.USD), Corridor.of(Country.UZ, Country.KZ));
        transfer.perform();

        domainRepository.save(transfer);
        em.flush();
        em.clear();

        Transfer loaded = domainRepository.findById("d-1").orElseThrow();
        assertEquals(TransferStatus.PERFORM, loaded.getStatus());
        assertEquals(0, new BigDecimal("777.77").compareTo(loaded.getMoney().amount()));
        assertEquals(Country.KZ, loaded.getCorridor().to());
        assertFalse(clients.findById("dave").isEmpty()); // клиент создан автоматически
    }
}
