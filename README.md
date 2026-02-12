# Reactive Stock/Crypto Tracker

Реактивное приложение для отслеживания цен акций и криптовалют в реальном времени.
Учебный проект для закрепления Spring WebFlux, R2DBC, Reactor Kafka.

## Источники данных

- **MOEX ISS** — российские акции (SBER, GAZP, LKOH, YNDX), бесплатный API без ключа
- **CoinGecko** — криптовалюты (BTC, ETH), бесплатный API без ключа

## Стек

- **Java 17** + **Spring Boot 3.4.x**
- **Spring WebFlux** (Netty) — функциональный роутинг + SSE
- **WebClient** — реактивные HTTP-запросы к MOEX и CoinGecko
- **R2DBC PostgreSQL** — реактивный доступ к БД
- **Liquibase** — миграции
- **Reactor Kafka** — реактивный producer/consumer
- **Testcontainers** — интеграционные тесты

## Архитектура

```
WebFlux REST API  ←→  Service Layer (Mono/Flux)  ←→  R2DBC PostgreSQL
     ↑                       ↑
     SSE Stream         Kafka Consumer
                             ↑
                        Kafka Topic: stock-prices
                             ↑
                   PriceFetcher (Producer)
                    ↙              ↘
           MOEX ISS API      CoinGecko API
          (SBER,GAZP,...)     (BTC, ETH)
```

## Запуск

```bash
# 1. Поднять PostgreSQL + Kafka
docker-compose up -d

# 2. Запустить приложение
./gradlew bootRun

# 3. Проверить API
curl http://localhost:8080/api/stocks
curl http://localhost:8080/api/stocks/SBER
curl -N http://localhost:8080/api/stocks/stream             # SSE — все цены
curl -N http://localhost:8080/api/stocks/BTC/prices/stream  # SSE — только BTC
```

## API

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/stocks` | Список всех акций |
| GET | `/api/stocks/{symbol}` | Акция по символу |
| POST | `/api/stocks` | Добавить акцию |
| DELETE | `/api/stocks/{symbol}` | Удалить акцию |
| GET | `/api/stocks/stream` | SSE поток всех цен |
| GET | `/api/stocks/{symbol}/prices/stream` | SSE поток цен акции |
| GET | `/api/stocks/{symbol}/prices?limit=50` | История цен |

## Тесты

```bash
./gradlew test
```

- **StockServiceTest** — unit-тесты с StepVerifier + Mockito
- **StockHandlerTest** — WebFlux тесты с WebTestClient
- **PriceFlowIntegrationTest** — e2e с Testcontainers (PostgreSQL + Kafka)

## Паттерны из курса JVA-074

1. Функциональный роутинг (`RouterFunction` + `HandlerFunction`)
2. SSE через `Flux` + `MediaType.TEXT_EVENT_STREAM`
3. `StepVerifier` для тестирования реактивных потоков
4. Reactor Kafka — реактивный producer/consumer
5. WebClient — реактивный HTTP-клиент
6. Явный DI через конструктор (без `@Autowired`)
