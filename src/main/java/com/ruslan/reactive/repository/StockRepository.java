package com.ruslan.reactive.repository;

import com.ruslan.reactive.model.Stock;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface StockRepository extends ReactiveCrudRepository<Stock, Long> {

    Mono<Stock> findBySymbol(String symbol);

    Mono<Void> deleteBySymbol(String symbol);

    @Query("SELECT EXISTS(SELECT 1 FROM stock WHERE symbol = :symbol)")
    Mono<Boolean> existsBySymbol(String symbol);
}
