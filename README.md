# Reactive Stock/Crypto Tracker

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green)

A reactive application for tracking stock and cryptocurrency prices in real time.
Built as a learning project to practice Spring WebFlux, R2DBC, and Reactor Kafka.

## Data Sources

- **MOEX ISS** — Russian equities (SBER, GAZP, LKOH, YNDX), free public API (no key required)
- **CoinGecko** — Cryptocurrencies (BTC, ETH), free public API (no key required)

## Tech Stack

- **Java 17** + **Spring Boot 3.4.x**
- **Spring WebFlux** (Netty) — functional routing + SSE
- **WebClient** — reactive HTTP calls to MOEX and CoinGecko
- **R2DBC PostgreSQL** — reactive database access
- **Liquibase** — database migrations
- **Reactor Kafka** — reactive producer/consumer
- **Testcontainers** — integration tests

## Architecture

```
WebFlux REST API  <-->  Service Layer (Mono/Flux)  <-->  R2DBC PostgreSQL
     ^                       ^
     SSE Stream         Kafka Consumer
                             ^
                        Kafka Topic: stock-prices
                             ^
                   PriceFetcher (Producer)
                    /              \
           MOEX ISS API      CoinGecko API
          (SBER,GAZP,...)     (BTC, ETH)
```

## Getting Started

```bash
# 1. Start PostgreSQL + Kafka
docker-compose up -d

# 2. Run the application
./gradlew bootRun

# 3. Try the API
curl http://localhost:8080/api/stocks
curl http://localhost:8080/api/stocks/SBER
curl -N http://localhost:8080/api/stocks/stream             # SSE — all prices
curl -N http://localhost:8080/api/stocks/BTC/prices/stream  # SSE — BTC only
```

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/stocks` | List all stocks |
| GET | `/api/stocks/{symbol}` | Get stock by symbol |
| POST | `/api/stocks` | Add a stock |
| DELETE | `/api/stocks/{symbol}` | Remove a stock |
| GET | `/api/stocks/stream` | SSE stream of all prices |
| GET | `/api/stocks/{symbol}/prices/stream` | SSE stream for a specific stock |
| GET | `/api/stocks/{symbol}/prices?limit=50` | Price history |

## Tests

```bash
./gradlew test
```

- **StockServiceTest** — unit tests with StepVerifier + Mockito
- **StockHandlerTest** — WebFlux tests with WebTestClient
- **PriceFlowIntegrationTest** — end-to-end with Testcontainers (PostgreSQL + Kafka)

## Patterns Used

1. Functional routing (`RouterFunction` + `HandlerFunction`)
2. SSE via `Flux` + `MediaType.TEXT_EVENT_STREAM`
3. `StepVerifier` for testing reactive streams
4. Reactor Kafka — reactive producer/consumer
5. WebClient — reactive HTTP client
6. Explicit constructor-based DI (no `@Autowired`)
