package com.ruslan.reactive.service;

import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(stockRepository);
    }

    @Test
    void findAll_returnsAllStocks() {
        Stock apple = new Stock("AAPL", "Apple Inc.", "NASDAQ");
        Stock bitcoin = new Stock("BTC", "Bitcoin", "CRYPTO");
        when(stockRepository.findAll()).thenReturn(Flux.just(apple, bitcoin));

        StepVerifier.create(stockService.findAll())
                .expectNextMatches(stock -> stock.getSymbol().equals("AAPL"))
                .expectNextMatches(stock -> stock.getSymbol().equals("BTC"))
                .verifyComplete();
    }

    @Test
    void findBySymbol_existingStock_returnsStock() {
        Stock apple = new Stock("AAPL", "Apple Inc.", "NASDAQ");
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Mono.just(apple));

        StepVerifier.create(stockService.findBySymbol("AAPL"))
                .expectNextMatches(stock -> stock.getName().equals("Apple Inc."))
                .verifyComplete();
    }

    @Test
    void findBySymbol_nonExisting_returnsEmpty() {
        when(stockRepository.findBySymbol("UNKNOWN")).thenReturn(Mono.empty());

        StepVerifier.create(stockService.findBySymbol("UNKNOWN"))
                .verifyComplete();
    }

    @Test
    void findBySymbol_convertsToUpperCase() {
        Stock apple = new Stock("AAPL", "Apple Inc.", "NASDAQ");
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Mono.just(apple));

        StepVerifier.create(stockService.findBySymbol("aapl"))
                .expectNextMatches(stock -> stock.getSymbol().equals("AAPL"))
                .verifyComplete();
    }

    @Test
    void create_newStock_savesSuccessfully() {
        Stock stock = new Stock("TSLA", "Tesla Inc.", "NASDAQ");
        Stock saved = new Stock("TSLA", "Tesla Inc.", "NASDAQ");
        saved.setId(1L);

        when(stockRepository.existsBySymbol("TSLA")).thenReturn(Mono.just(false));
        when(stockRepository.save(any(Stock.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(stockService.create(stock))
                .expectNextMatches(s -> s.getId() != null && s.getSymbol().equals("TSLA"))
                .verifyComplete();
    }

    @Test
    void create_duplicateSymbol_returnsError() {
        Stock stock = new Stock("AAPL", "Apple Duplicate", "NASDAQ");
        when(stockRepository.existsBySymbol("AAPL")).thenReturn(Mono.just(true));

        StepVerifier.create(stockService.create(stock))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                        && e.getMessage().contains("already exists"))
                .verify();
    }

    @Test
    void deleteBySymbol_existingStock_deletesSuccessfully() {
        Stock apple = new Stock("AAPL", "Apple Inc.", "NASDAQ");
        apple.setId(1L);
        when(stockRepository.findBySymbol("AAPL")).thenReturn(Mono.just(apple));
        when(stockRepository.deleteById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(stockService.deleteBySymbol("AAPL"))
                .verifyComplete();
    }

    @Test
    void deleteBySymbol_nonExisting_returnsError() {
        when(stockRepository.findBySymbol(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(stockService.deleteBySymbol("UNKNOWN"))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                        && e.getMessage().contains("not found"))
                .verify();
    }
}
