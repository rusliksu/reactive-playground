package com.ruslan.reactive.service;

import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.repository.StockRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Flux<Stock> findAll() {
        return stockRepository.findAll();
    }

    public Mono<Stock> findBySymbol(String symbol) {
        return stockRepository.findBySymbol(symbol.toUpperCase());
    }

    public Mono<Stock> create(Stock stock) {
        stock.setSymbol(stock.getSymbol().toUpperCase());
        return stockRepository.existsBySymbol(stock.getSymbol())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Stock with symbol " + stock.getSymbol() + " already exists"));
                    }
                    return stockRepository.save(stock);
                });
    }

    public Mono<Void> deleteBySymbol(String symbol) {
        return stockRepository.findBySymbol(symbol.toUpperCase())
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Stock with symbol " + symbol + " not found")))
                .flatMap(stock -> stockRepository.deleteById(stock.getId()));
    }
}
