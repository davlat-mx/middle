# RemitCore — M1

Учебный домен денежных переводов: заявка проходит валидацию бизнес-правил и попадает в отчётность.

## Запуск

```bash
./gradlew test      # все тесты
./gradlew bootRun   # демо-сценарий: 5 заявок + три отчёта
```

## Структура

```
org.dave.middle
├── domain.vo       Money, Corridor                     — value objects, инварианты в компактном конструкторе
├── domain.model    Transfer, TransferStatus, Currency, Country
├── domain.rule     TransferRule, ValidationResult, CurrencyAllowedRule, SameClientRule, ValidationEngine
├── repository      TransferRepository                  — in-memory
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
`TransferServiceTest`, `ReportServiceTest` (фикстура из 6 заявок).
