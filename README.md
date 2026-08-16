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
| Потокобезопасное состояние | `ConcurrentHashMap`, `AtomicLong`-счётчики (`ProcessorStats`) |
| Идемпотентность | один id обрабатывается не более раза (в M4 вынесено в `IdempotencyGuard`, см. ниже) |
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

---

# M4 — Spring internals + собственный стартер

Сквозная функциональность (correlation-id / логирование / идемпотентность) вынесена в
**отдельный Spring Boot starter** — Gradle-подпроект `observability-spring-boot-starter`,
который приложение подключает как зависимость (`implementation project(...)`).
Пакет `org.dave.observability`, префикс настроек `middle.observability`.

```
observability-spring-boot-starter
├── ObservabilityAutoConfiguration   @AutoConfiguration, набор @ConditionalOn*
├── ObservabilityProperties          @ConfigurationProperties("middle.observability")
├── CorrelationContext               MDC-обёртка run(id, task) — для очереди и HTTP
├── CorrelationIdFilter              заголовок X-Correlation-Id -> MDC на время запроса
├── RequestLoggingFilter             одна строка на запрос: метод, путь, статус, время
├── IdempotencyGuard / InMemoryIdempotencyGuard   защита от повторной обработки по ключу
└── META-INF/spring/…AutoConfiguration.imports    регистрация автоконфига
```

| Механизм Spring | Где показан |
|---|---|
| Авто-конфигурация | `@AutoConfiguration` + `AutoConfiguration.imports` (не `spring.factories`) |
| Условное подключение | `@ConditionalOnWebApplication`/`OnClass` (фильтры), `@ConditionalOnProperty` (флаги), `@ConditionalOnMissingBean` (переопределение) |
| Типобезопасная конфигурация | `@ConfigurationProperties` + `spring-boot-configuration-processor` (IDE-метаданные) |
| DI / замена реализации | `IdempotencyGuard` — бин из стартера, потребитель может подставить свой (Redis и т.п.) |

**Интеграция в сервис:** `TransferQueueProcessor` больше не держит свой inline `claimed`-набор —
принимает `IdempotencyGuard` и оборачивает обработку заявки в `CorrelationContext.run(id, …)`,
поэтому все `log.*` внутри воркера помечены id заявки. В `application.yml` паттерн лога
`logging.pattern.level: "%5p [%X{correlationId:-}]"` выводит этот id в каждой строке —
видно жизненный путь заявки (взяли → повтор → успех / дубликат) и что параллельные
virtual-thread воркеры не путаются между собой.

**Тесты** `ObservabilityAutoConfigurationTest` — на `ApplicationContextRunner` (без Docker):
бин идемпотентности поднимается по умолчанию, гаснет по `enabled=false`, уступает
пользовательскому бину через `@ConditionalOnMissingBean`.

---

# M5 — Контейнеризация (Dockerfile)

Multi-stage [`Dockerfile`](Dockerfile): сборка на `eclipse-temurin:21-jdk`, рантайм на
**distroless** `gcr.io/distroless/java21-debian12:nonroot` — минимальный образ без shell и
пакетного менеджера, процесс идёт под непривилегированным пользователем (uid 65532).

| Приём | Как |
|---|---|
| Multi-stage | стадия `build` (Gradle → `bootJar`) отдельно от рантайма; в финальный образ едет только jar |
| Layer caching | сначала копируем build-скрипты и тянем зависимости, потом исходники + BuildKit `--mount=type=cache` на `/root/.gradle` |
| Непривилегированный запуск | тег `:nonroot` distroless |
| JVM под контейнер | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 …` — куча в % от cgroup-лимита, а не фиксированный `-Xmx` |
| Лёгкий контекст | [`.dockerignore`](.dockerignore) исключает `build/`, `.gradle/`, `.git/`, IDE |

Единственная правка сборки — отключён «plain»-jar (`tasks.named('jar') { enabled = false }`),
чтобы в `build/libs` лежал ровно один артефакт. Локальный запуск:

```bash
docker compose up --build      # app (:8080) + postgres, app ждёт готовности БД
```

---

# M6 — Kubernetes / minikube

Манифесты в [`k8s/`](k8s): приложение + Postgres в кластере, конфиг через ConfigMap/Secret,
health-пробы через **Spring Boot Actuator**.

```
k8s
├── config.yaml     ConfigMap (DB_URL, DB_NAME) + Secret (DB_USER, DB_PASSWORD)
├── postgres.yaml   Deployment (emptyDir) + Service middle-postgres:5432
└── app.yaml        Deployment (probes, requests/limits) + Service NodePort:8080
```

| Элемент | Роль у middle |
|---|---|
| ConfigMap / Secret | `DB_URL`/`DB_NAME` (несекретно) и креды Postgres (секрет); прокидываются в под через `envFrom` |
| readinessProbe | `/actuator/health/readiness` (группа включает `db`) — под не получает трафик, пока БД недоступна |
| livenessProbe | `/actuator/health/liveness` — зависший под перезапускается |
| startupProbe | прикрывает медленный старт (boot + Flyway), до ~60s, чтобы liveness не убил под преждевременно |
| requests/limits | `limits.memory: 1Gi` — от него JVM (`MaxRAMPercentage=75`) считает heap; связка с M5 |

Actuator добавлен ради проб (`spring-boot-starter-actuator` + группа `readiness: readinessState,db`).
Развёртывание в minikube (образ собирается **внутрь** демона minikube, поэтому реестр не нужен):

```bash
minikube start
eval $(minikube docker-env)              # переключить docker на демон minikube
docker build -t middle:latest .          # образ виден кластеру (pullPolicy=IfNotPresent)
kubectl apply -f k8s/                     # config/secret + postgres + app
kubectl get pods -w                       # ждём Running + READY 1/1
kubectl logs -f deploy/middle-app         # логи (там же correlation-id из M4)
minikube service middle-app               # открыть сервис в браузере
# альтернатива: kubectl port-forward svc/middle-app 8080:8080
```

Диагностика: `kubectl describe pod <p>` (события, pull, статус проб),
`kubectl rollout status deploy/middle-app`, `kubectl get deploy,svc`.
