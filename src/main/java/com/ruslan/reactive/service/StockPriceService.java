package com.ruslan.reactive.service;

import com.ruslan.reactive.model.StockPrice;
import com.ruslan.reactive.repository.StockPriceRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Service
public class StockPriceService {

    private final StockPriceRepository priceRepository;
    private final Sinks.Many<StockPrice> priceSink;

    public StockPriceService(StockPriceRepository priceRepository) {
        this.priceRepository = priceRepository;
        this.priceSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    public Flux<StockPrice> getLatestPrices(Long stockId, int limit) {
        return priceRepository.findLatestByStockId(stockId, limit);
    }

    public Mono<StockPrice> getLastPrice(Long stockId) {
        return priceRepository.findLastByStockId(stockId);
    }

    public Mono<StockPrice> savePrice(StockPrice price) {
        return priceRepository.save(price)
                .doOnNext(saved -> priceSink.tryEmitNext(saved));
    }

    public Flux<StockPrice> streamAllPrices() {
        return priceSink.asFlux();
    }

    public Flux<StockPrice> streamPricesForStock(Long stockId) {
        return priceSink.asFlux()
                .filter(price -> price.getStockId().equals(stockId));
    }
}
