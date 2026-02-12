package com.ruslan.reactive.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.model.StockPrice;
import com.ruslan.reactive.repository.StockPriceRepository;
import com.ruslan.reactive.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "app.price-simulator.enabled", havingValue = "true")
public class PriceProducer {

    private static final Logger log = LoggerFactory.getLogger(PriceProducer.class);

    private final KafkaSender<String, String> kafkaSender;
    private final StockRepository stockRepository;
    private final StockPriceRepository priceRepository;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long intervalMs;

    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();
    private Disposable subscription;

    private static final Map<String, BigDecimal> DEFAULT_PRICES = Map.of(
            "AAPL", new BigDecimal("178.50"),
            "GOOGL", new BigDecimal("141.80"),
            "BTC", new BigDecimal("43250.00"),
            "ETH", new BigDecimal("2280.00"),
            "SBER", new BigDecimal("271.50")
    );

    public PriceProducer(KafkaSender<String, String> kafkaSender,
                         StockRepository stockRepository,
                         StockPriceRepository priceRepository,
                         ObjectMapper objectMapper,
                         @Value("${app.kafka.topic}") String topic,
                         @Value("${app.price-simulator.interval-ms}") long intervalMs) {
        this.kafkaSender = kafkaSender;
        this.stockRepository = stockRepository;
        this.priceRepository = priceRepository;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.intervalMs = intervalMs;
    }

    @PostConstruct
    public void start() {
        subscription = Flux.interval(Duration.ofMillis(intervalMs))
                .flatMap(tick -> stockRepository.findAll()
                        .flatMap(this::generatePrice))
                .subscribe(
                        result -> {},
                        error -> log.error("Price simulator error", error)
                );
        log.info("Price simulator started with interval {}ms", intervalMs);
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private Mono<Void> generatePrice(Stock stock) {
        return getLastPrice(stock)
                .map(lastPrice -> simulatePrice(stock.getSymbol(), lastPrice))
                .flatMap(price -> sendToKafka(stock.getSymbol(), price));
    }

    private Mono<BigDecimal> getLastPrice(Stock stock) {
        BigDecimal cached = lastPrices.get(stock.getSymbol());
        if (cached != null) {
            return Mono.just(cached);
        }
        return priceRepository.findLastByStockId(stock.getId())
                .map(StockPrice::getPrice)
                .defaultIfEmpty(DEFAULT_PRICES.getOrDefault(stock.getSymbol(), new BigDecimal("100.00")));
    }

    private StockPrice simulatePrice(String symbol, BigDecimal lastPrice) {
        double changePercent = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
        BigDecimal change = lastPrice.multiply(BigDecimal.valueOf(changePercent / 100))
                .setScale(8, RoundingMode.HALF_UP);
        BigDecimal newPrice = lastPrice.add(change).max(BigDecimal.valueOf(0.01));

        lastPrices.put(symbol, newPrice);

        StockPrice stockPrice = new StockPrice();
        stockPrice.setPrice(newPrice);
        stockPrice.setChange(change);
        stockPrice.setChangePercent(BigDecimal.valueOf(changePercent).setScale(4, RoundingMode.HALF_UP));
        stockPrice.setTimestamp(LocalDateTime.now());
        return stockPrice;
    }

    private Mono<Void> sendToKafka(String symbol, StockPrice price) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "symbol", symbol,
                    "price", price.getPrice(),
                    "change", price.getChange(),
                    "changePercent", price.getChangePercent(),
                    "timestamp", price.getTimestamp().toString()
            ));

            SenderRecord<String, String, String> record = SenderRecord.create(
                    new ProducerRecord<>(topic, symbol, json), symbol);

            return kafkaSender.send(Mono.just(record))
                    .doOnNext(result -> log.debug("Sent price for {}: {}", symbol, price.getPrice()))
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
