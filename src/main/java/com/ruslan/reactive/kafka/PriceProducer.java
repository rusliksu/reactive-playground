package com.ruslan.reactive.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslan.reactive.client.CoinGeckoClient;
import com.ruslan.reactive.client.MoexClient;
import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PriceProducer {

    private static final Logger log = LoggerFactory.getLogger(PriceProducer.class);

    private final KafkaSender<String, String> kafkaSender;
    private final StockRepository stockRepository;
    private final MoexClient moexClient;
    private final CoinGeckoClient coinGeckoClient;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long moexIntervalMs;
    private final long cryptoIntervalMs;

    private Disposable moexSubscription;
    private Disposable cryptoSubscription;

    public PriceProducer(KafkaSender<String, String> kafkaSender,
                         StockRepository stockRepository,
                         MoexClient moexClient,
                         CoinGeckoClient coinGeckoClient,
                         ObjectMapper objectMapper,
                         @Value("${app.kafka.topic}") String topic,
                         @Value("${app.fetcher.moex-interval-ms:15000}") long moexIntervalMs,
                         @Value("${app.fetcher.crypto-interval-ms:30000}") long cryptoIntervalMs) {
        this.kafkaSender = kafkaSender;
        this.stockRepository = stockRepository;
        this.moexClient = moexClient;
        this.coinGeckoClient = coinGeckoClient;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.moexIntervalMs = moexIntervalMs;
        this.cryptoIntervalMs = cryptoIntervalMs;
    }

    @PostConstruct
    public void start() {
        moexSubscription = Flux.interval(Duration.ZERO, Duration.ofMillis(moexIntervalMs))
                .flatMap(tick -> fetchMoexPrices()
                        .onErrorResume(e -> {
                            log.warn("MOEX fetch error: {}", e.getMessage());
                            return Flux.empty();
                        }))
                .subscribe();

        cryptoSubscription = Flux.interval(Duration.ZERO, Duration.ofMillis(cryptoIntervalMs))
                .flatMap(tick -> fetchCryptoPrices()
                        .onErrorResume(e -> {
                            log.warn("Crypto fetch error: {}", e.getMessage());
                            return Flux.empty();
                        }))
                .subscribe();

        log.info("Price fetcher started: MOEX every {}ms, Crypto every {}ms", moexIntervalMs, cryptoIntervalMs);
    }

    @PreDestroy
    public void stop() {
        if (moexSubscription != null && !moexSubscription.isDisposed()) {
            moexSubscription.dispose();
        }
        if (cryptoSubscription != null && !cryptoSubscription.isDisposed()) {
            cryptoSubscription.dispose();
        }
    }

    private Flux<Void> fetchMoexPrices() {
        return stockRepository.findAll()
                .filter(stock -> "MOEX".equals(stock.getExchange()))
                .collectList()
                .flatMapMany(stocks -> {
                    if (stocks.isEmpty()) return Flux.empty();
                    List<String> symbols = stocks.stream()
                            .map(Stock::getExternalId)
                            .collect(Collectors.toList());
                    return moexClient.fetchPrices(symbols)
                            .flatMap(data -> sendToKafka(data.symbol(), data.price(), data.change(), data.changePercent()));
                });
    }

    private Flux<Void> fetchCryptoPrices() {
        return stockRepository.findAll()
                .filter(stock -> "CRYPTO".equals(stock.getExchange()))
                .collectList()
                .flatMapMany(stocks -> {
                    if (stocks.isEmpty()) return Flux.empty();
                    Map<String, String> idToSymbol = new LinkedHashMap<>();
                    for (Stock stock : stocks) {
                        idToSymbol.put(stock.getExternalId(), stock.getSymbol());
                    }
                    return coinGeckoClient.fetchPrices(idToSymbol)
                            .flatMap(data -> sendToKafka(data.symbol(), data.price(), data.change(), data.changePercent()));
                });
    }

    private Mono<Void> sendToKafka(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "symbol", symbol,
                    "price", price,
                    "change", change,
                    "changePercent", changePercent,
                    "timestamp", LocalDateTime.now().toString()
            ));

            SenderRecord<String, String, String> record = SenderRecord.create(
                    new ProducerRecord<>(topic, symbol, json), symbol);

            return kafkaSender.send(Mono.just(record))
                    .doOnNext(result -> log.info("Fetched {} = {} ({}%)", symbol, price, changePercent))
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
