package com.ruslan.reactive.handler;

import com.ruslan.reactive.client.CoinGeckoClient;
import com.ruslan.reactive.client.MoexClient;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    private MoexClient moexClient;

    @MockitoBean
    private CoinGeckoClient coinGeckoClient;

    @Test
    void getAllStocks_returnsStockList() {
        Stock sber = new Stock("SBER", "Сбербанк", "MOEX");
        sber.setId(1L);
        Stock btc = new Stock("BTC", "Bitcoin", "CRYPTO");
        btc.setId(2L);

        when(stockRepository.findAll()).thenReturn(Flux.just(sber, btc));

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
        Stock sber = new Stock("SBER", "Сбербанк", "MOEX");
        sber.setId(1L);
        when(stockRepository.findBySymbol("SBER")).thenReturn(Mono.just(sber));

        webTestClient.get()
                .uri("/api/stocks/SBER")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("SBER")
                .jsonPath("$.name").isEqualTo("Сбербанк");
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
        Stock stock = new Stock("GAZP", "Газпром", "MOEX");
        Stock saved = new Stock("GAZP", "Газпром", "MOEX");
        saved.setId(1L);

        when(stockRepository.existsBySymbol("GAZP")).thenReturn(Mono.just(false));
        when(stockRepository.save(any(Stock.class))).thenReturn(Mono.just(saved));

        webTestClient.post()
                .uri("/api/stocks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(stock)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("GAZP")
                .jsonPath("$.id").isEqualTo(1);
    }

    @Test
    void createStock_duplicateSymbol_returnsBadRequest() {
        Stock stock = new Stock("SBER", "Сбер дубль", "MOEX");
        when(stockRepository.existsBySymbol("SBER")).thenReturn(Mono.just(true));

        webTestClient.post()
                .uri("/api/stocks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(stock)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void deleteStock_existingStock_returnsNoContent() {
        Stock sber = new Stock("SBER", "Сбербанк", "MOEX");
        sber.setId(1L);
        when(stockRepository.findBySymbol("SBER")).thenReturn(Mono.just(sber));
        when(stockRepository.deleteById(1L)).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/api/stocks/SBER")
                .exchange()
                .expectStatus().isNoContent();
    }
}
