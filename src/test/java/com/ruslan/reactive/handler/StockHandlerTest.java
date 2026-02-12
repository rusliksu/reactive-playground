package com.ruslan.reactive.handler;

import com.ruslan.reactive.config.R2dbcConfig;
import com.ruslan.reactive.kafka.PriceConsumer;
import com.ruslan.reactive.kafka.PriceProducer;
import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.repository.StockPriceRepository;
import com.ruslan.reactive.repository.StockRepository;
import com.ruslan.reactive.router.StockRouter;
import com.ruslan.reactive.service.StockPriceService;
import com.ruslan.reactive.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest
@Import({StockRouter.class, StockHandler.class, StockService.class, StockPriceService.class})
class StockHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private StockRepository stockRepository;

    @MockitoBean
    private StockPriceRepository priceRepository;

    @MockitoBean
    private PriceProducer priceProducer;

    @MockitoBean
    private PriceConsumer priceConsumer;

    @Test
    void getAllStocks_returnsStockList() {
        Stock apple = new Stock("AAPL", "Apple Inc.", "NASDAQ");
        apple.setId(1L);
        Stock btc = new Stock("BTC", "Bitcoin", "CRYPTO");
        btc.setId(2L);

        when(stockRepository.findAll()).thenReturn(Flux.just(apple, btc));

        webTestClient.get()
                .uri("/api/stocks")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(Stock.class)
                .hasSize(2);
    }

    @Test
    void getStockBySymbol_existingStock_returnsStock() {
        Stock apple = new Stock("AAPL", "Apple Inc.", "NASDAQ");
        apple.setId(1L);
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Mono.just(apple));

        webTestClient.get()
                .uri("/api/stocks/AAPL")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("AAPL")
                .jsonPath("$.name").isEqualTo("Apple Inc.");
    }

    @Test
    void getStockBySymbol_nonExisting_returns404() {
        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/stocks/UNKNOWN")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createStock_validInput_returnsCreated() {
        Stock stock = new Stock("TSLA", "Tesla Inc.", "NASDAQ");
        Stock saved = new Stock("TSLA", "Tesla Inc.", "NASDAQ");
        saved.setId(1L);

        when(stockRepository.existsBySymbol("TSLA")).thenReturn(Mono.just(false));
        when(stockRepository.save(any(Stock.class))).thenReturn(Mono.just(saved));

        webTestClient.post()
                .uri("/api/stocks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(stock)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("TSLA")
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void createStock_duplicateSymbol_returnsBadRequest() {
        Stock stock = new Stock("AAPL", "Apple Duplicate", "NASDAQ");
        when(stockRepository.existsBySymbol("AAPL")).thenReturn(Mono.just(true));

        webTestClient.post()
                .uri("/api/stocks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(stock)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void deleteStock_existingStock_returnsNoContent() {
        Stock apple = new Stock("AAPL", "Apple Inc.", "NASDAQ");
        apple.setId(1L);
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Mono.just(apple));
        when(stockRepository.deleteById(1L)).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/api/stocks/AAPL")
                .exchange()
                .expectStatus().isNoContent();
    }
}
