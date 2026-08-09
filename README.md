# RemitCore — M1

Учебный домен денежных переводов: заявка проходит валидацию бизнес-правил и попадает в отчётность.

## Запуск

```bash
docker compose up -d   # Postgres для приложения (M3)
./gradlew test         # все тесты (persistence-тесты поднимают Postgres в Testcontainers)
./gradlew bootRun      # демо: 5 заявок + три отчёта + фоновая очередь (M2), всё в Postgres
```

Тестам нужен только Docker (Postgres поднимается автоматически через Testcontainers).
`bootRun` ждёт Postgres на `localhost:5432` — либо `docker compose up -d`, либо свой инстанс
(параметры через `DB_URL`/`DB_USER`/`DB_PASSWORD`).

## Структура

```
org.dave.middle
├── domain.vo       Money, Corridor                     — value objects, инварианты в компактном конструкторе
├── domain.model    Transfer, TransferStatus, Currency, Country
├── domain.rule     TransferRule, ValidationResult, CurrencyAllowedRule, SameClientRule, ValidationEngine
├── repository      TransferRepository                  — порт (в M3 реализация на JPA/Postgres)
├── service         TransferService, ReportService
└── DemoRunner      CommandLineRunner с демо-сценарием
```

## Модель

**Статусы** — `PREPARE`, `PERFORM`, `SUCCESS`, `FAILED`. Обычный жизненный цикл заявки:
`PREPARE → PERFORM → SUCCESS`, при отказе валидации — `FAILED`. Порядок не навязывается кодом:
проверки допустимости перехода нет, ответственность за корректную последовательность на вызывающем.

**`Transfer`** — корень агрегата, публичных сеттеров нет. Создаётся фабрикой `Transfer.create(...)`
в статусе `PREPARE`, дальше меняется только через `perform()` / `success()` / `fail()`.

**Lombok** снимает бойлерплейт там, где он есть: `@Getter`, `@EqualsAndHashCode(of = "id")`
и приватный `@AllArgsConstructor` на агрегате, `@RequiredArgsConstructor` на сервисах. Используются только стабильные аннотации, ничего
из `lombok.experimental` — поэтому у классов геттеры в стиле `getMoney()`, а у records остаётся
каноническое `amount()`. В `Money`, `Corridor`, `ValidationResult` Lombok не нужен —
это records. В `CurrencyAllowedRule` и `ValidationEngine` конструкторы написаны руками: они делают
защитное копирование (`Map.copyOf` / `List.copyOf`), которое Lombok не сгенерирует.

**Инварианты в VO:** `Money` — сумма > 0 и не больше двух знаков после запятой, `plus()` отказывается
складывать разные валюты; `Corridor` — страна отправления != страна получения.

**Бизнес-правила (Strategy):** `CurrencyAllowedRule` (валюта допустима для коридора) и `SameClientRule`
(отправитель != получатель). Интерфейс `TransferRule` функциональный, метод `and()` даёт композицию
с коротким замыканием. `ValidationEngine` прогоняет заявку через весь список и собирает **все** ошибки,
а не первую — пользователю полезнее увидеть сразу всё.

**Отчёты** — три разных коллектора:

| Отчёт | Коллектор |
|---|---|
| `countByStatus()` | `groupingBy` + `counting()` в `EnumMap` |
| `turnoverByCorridor()` | `groupingBy` + `reducing` по `BigDecimal` |
| `successVsFailed()` | `partitioningBy` |

## Какая коллекция и почему

| Где | Структура | Почему |
|---|---|---|
| Хранилище заявок | `HashMap<String, Transfer>` | O(1) поиск по id вместо O(n) обхода списка |
| Счётчики по статусам | `EnumMap<TransferStatus, Long>` | массив вместо хеш-таблицы, порядок ключей = порядок объявления enum |
| Валюты коридора | `Map<Corridor, Set<Currency>>` | проверка правила за O(1); `Corridor` — record, hashCode готов |
| Список правил | `List<TransferRule>` | порядок важен, нужен только обход |

## Тесты

`MoneyTest`, `CorridorTest`, `TransferTest` (создание, смена статуса, равенство по id),
`RulesTest` (позитив/негатив на каждое правило + композиция `and()`),
`TransferServiceTest`, `ReportServiceTest` (фикстура из 6 заявок) — на реальном Postgres (Testcontainers).

---

# M2 — Конкурентность

Фоновый обработчик очереди `queue.TransferQueueProcessor` на **virtual threads**: диспетчер снимает
заявки из `BlockingQueue` и запускает обработку каждой в отдельном виртуальном потоке.

| Гарантия | Как сделано |
|---|---|
| Virtual threads | `Executors.newVirtualThreadPerTaskExecutor()` + диспетчер на `Thread.ofVirtual()` |
| Потокобезопасное состояние | `ConcurrentHashMap`, `AtomicLong`-счётчики (`ProcessorStats`), claimed-множество |
| Идемпотентность | `claimed.add(id)` — один id обрабатывается не более раза |
| Повторы с backoff | `RetryPolicy` — до N попыток, экспоненциальная пауза; отказ бизнес-правил не повторяется |

`TransferExecutor` — точка внешнего «проведения» перевода (может временно падать → повтор).
Тесты `TransferQueueProcessorTest`: успех, отказ без повторов, идемпотентность, повтор→успех,
исчерпание попыток, нагрузка 500 заявок.

---

# M3 — Persistence (Spring Data JPA + PostgreSQL)

Продакшн-хранилище — Postgres через Spring Data. Порт `TransferRepository` теперь интерфейс;
`JpaTransferRepository` реализует его поверх JPA (домен ↔ сущность через `TransferMapper`),
поэтому M1/M2 работают без изменений. Схемой владеет **Flyway** (`ddl-auto: validate`).

```
persistence
├── entity      TransferEntity, ClientEntity, TransferEventEntity, Money/CorridorEmbeddable
├── repository  TransferEntityRepository (JpaRepository + JpaSpecificationExecutor), ClientEntityRepository
├── spec        TransferSpecifications — динамическая фильтрация
├── projection  TransferSummary (interface), TransferReportDto (class/DTO)
└── TransferMapper
repository       TransferRepository (порт) + JpaTransferRepository (продакшн)
resources/db/migration  V1__init.sql, V2__indexes.sql
```

**Связи** (ради демонстрации N+1): `Transfer → Client` (sender/receiver, `@ManyToOne LAZY`) и
`Transfer → TransferEvent` (`@OneToMany`, история смены статусов = аудит-трейл).

| Фича | Где показана |
|---|---|
| Сущности и репозитории | `persistence.entity`, `TransferEntityRepository` |
| `@Query` (JPQL + native) | `reportByCorridorFrom`, `turnover`, `countByStatusNative` |
| Проекции / DTO | `TransferSummary` (interface), `TransferReportDto` (конструкторная выборка) |
| Specifications | `TransferSpecifications` + `JpaSpecificationExecutor` (null-условие = нет фильтра) |
| `Pageable` | `findByStatus(status, Pageable)` + сортировка |
| Оптимистичная блокировка | `@Version` в `TransferEntity` |
| Метки времени | Hibernate `@CreationTimestamp/@UpdateTimestamp` + `default now()` в БД |
| `@EntityGraph` против N+1 | `findWithGraphByStatus` vs `findByStatus` |
| Миграции | Flyway `V1`/`V2` |

**Тесты** `TransferPersistenceTest` — на реальном Postgres через **Testcontainers**
(`@SpringBootTest` + `@Transactional` откат): Flyway-схема и аудит, Specifications, Pageable,
проекции, `@Query`, оборот/native-count, N+1 vs `@EntityGraph` (по счётчику запросов Hibernate),
конфликт `@Version`, roundtrip доменного порта.
