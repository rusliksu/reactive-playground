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
        Stock sber = new Stock("SBER", "Сбербанк", "MOEX");
        Stock btc = new Stock("BTC", "Bitcoin", "CRYPTO");
        when(stockRepository.findAll()).thenReturn(Flux.just(sber, btc));

        StepVerifier.create(stockService.findAll())
                .expectNextMatches(stock -> stock.getSymbol().equals("SBER"))
                .expectNextMatches(stock -> stock.getSymbol().equals("BTC"))
                .verifyComplete();
    }

    @Test
    void findBySymbol_existingStock_returnsStock() {
        Stock sber = new Stock("SBER", "Сбербанк", "MOEX");
        when(stockRepository.findBySymbol("SBER")).thenReturn(Mono.just(sber));

        StepVerifier.create(stockService.findBySymbol("SBER"))
                .expectNextMatches(stock -> stock.getName().equals("Сбербанк"))
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
        Stock sber = new Stock("SBER", "Сбербанк", "MOEX");
        when(stockRepository.findBySymbol("SBER")).thenReturn(Mono.just(sber));

        StepVerifier.create(stockService.findBySymbol("sber"))
                .expectNextMatches(stock -> stock.getSymbol().equals("SBER"))
                .verifyComplete();
    }

    @Test
    void create_newStock_savesSuccessfully() {
        Stock stock = new Stock("GAZP", "Газпром", "MOEX");
        Stock saved = new Stock("GAZP", "Газпром", "MOEX");
        saved.setId(1L);

        when(stockRepository.existsBySymbol("GAZP")).thenReturn(Mono.just(false));
        when(stockRepository.save(any(Stock.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(stockService.create(stock))
                .expectNextMatches(s -> s.getId() != null && s.getSymbol().equals("GAZP"))
                .verifyComplete();
    }

    @Test
    void create_duplicateSymbol_returnsError() {
        Stock stock = new Stock("SBER", "Сбер дубль", "MOEX");
        when(stockRepository.existsBySymbol("SBER")).thenReturn(Mono.just(true));

        StepVerifier.create(stockService.create(stock))
                .expectErrorMatches(e -> e instanceof IllegalArgumentException
                        && e.getMessage().contains("already exists"))
                .verify();
    }

    @Test
    void deleteBySymbol_existingStock_deletesSuccessfully() {
        Stock sber = new Stock("SBER", "Сбербанк", "MOEX");
        sber.setId(1L);
        when(stockRepository.findBySymbol("SBER")).thenReturn(Mono.just(sber));
        when(stockRepository.deleteById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(stockService.deleteBySymbol("SBER"))
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
