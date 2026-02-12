package com.ruslan.reactive.repository;

import com.ruslan.reactive.model.StockPrice;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StockPriceRepository extends ReactiveCrudRepository<StockPrice, Long> {

    Flux<StockPrice> findByStockIdOrderByTimestampDesc(Long stockId);

    @Query("SELECT * FROM stock_price WHERE stock_id = :stockId ORDER BY timestamp DESC LIMIT :limit")
    Flux<StockPrice> findLatestByStockId(Long stockId, int limit);

    @Query("SELECT * FROM stock_price WHERE stock_id = :stockId ORDER BY timestamp DESC LIMIT 1")
    Mono<StockPrice> findLastByStockId(Long stockId);
}
