# Reactive Stock/Crypto Tracker

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![WebFlux](https://img.shields.io/badge/Spring-WebFlux-green?logo=spring)](https://docs.spring.io/spring-framework/reference/web/webflux.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Real-time stock and cryptocurrency price tracker built with reactive stack.

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                    Clients                            │
│          REST API          SSE Stream                 │
└──────────┬──────────────────┬────────────────────────┘
           │                  │
┌──────────▼──────────────────▼────────────────────────┐
│              Spring WebFlux (Netty)                   │
│     StockRouter → StockHandler → StockService         │
└──────────┬──────────────────────────────────────────┘
           │
     ┌─────▼─────┐         ┌────────────────┐
     │  R2DBC    │         │  Reactor Kafka  │
     │ PostgreSQL│◄────────│   Consumer      │
     └───────────┘         └───────┬────────┘
                                   │
                           ┌───────▼────────┐
                           │  Kafka Topic    │
                           │  stock-prices   │
                           └───────┬────────┘
                                   │
                           ┌───────▼────────┐
                           │ PriceProducer   │
                           │  (scheduled)    │
                           └──┬──────────┬──┘
                              │          │
                    ┌─────────▼┐   ┌─────▼──────┐
                    │ Tinkoff  │   │  CoinGecko  │
                    │ API      │   │  API        │
                    │ SBER,GAZP│   │  BTC, ETH   │
                    └──────────┘   └─────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 17, Spring Boot 3.4 |
| Web | Spring WebFlux (Netty), functional routing |
| Database | R2DBC PostgreSQL, Liquibase migrations |
| Messaging | Reactor Kafka (producer/consumer) |
| HTTP Client | WebClient (reactive, non-blocking) |
| Testing | JUnit 5, Mockito, StepVerifier, Testcontainers |
| Infra | Docker Compose (PostgreSQL 16 + Kafka) |

## Data Sources

- **Tinkoff API** — Russian stocks (SBER, GAZP, LKOH, YNDX), free API
- **CoinGecko API** — Cryptocurrencies (BTC, ETH), free API

## Quick Start

```bash
# Start infrastructure
docker-compose up -d

# Run application
./gradlew bootRun

# Open dashboard
open http://localhost:8080

# Or use API directly
curl http://localhost:8080/api/stocks
curl -N http://localhost:8080/api/stocks/stream  # SSE live prices
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/stocks` | List all tracked stocks |
| GET | `/api/stocks/{symbol}` | Get stock by symbol |
| POST | `/api/stocks` | Add stock to track |
| DELETE | `/api/stocks/{symbol}` | Remove stock |
| GET | `/api/stocks/stream` | SSE stream — all prices |
| GET | `/api/stocks/{symbol}/prices/stream` | SSE stream — single stock |
| GET | `/api/stocks/{symbol}/prices?limit=50` | Price history |

## Testing

```bash
./gradlew test
```

| Test Class | Type | What it covers |
|-----------|------|---------------|
| `StockServiceTest` | Unit | CRUD operations, StepVerifier + Mockito |
| `StockHandlerTest` | WebFlux | Handlers with WebTestClient |
| `PriceFlowIntegrationTest` | E2E | Full flow with Testcontainers (PostgreSQL + Kafka) |

## Key Patterns

- **Functional routing** — `RouterFunction` + `HandlerFunction` instead of `@RestController`
- **SSE streaming** — `Flux<T>` + `MediaType.TEXT_EVENT_STREAM` for real-time updates
- **Reactive Kafka** — non-blocking producer/consumer with Project Reactor
- **StepVerifier** — testing reactive streams step by step
- **Constructor injection** — explicit DI, no `@Autowired`

## License

[MIT](LICENSE)
