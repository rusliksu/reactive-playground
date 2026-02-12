package com.ruslan.reactive.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslan.reactive.model.StockPrice;
import com.ruslan.reactive.service.StockPriceService;
import com.ruslan.reactive.service.StockService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.kafka.receiver.KafkaReceiver;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class PriceConsumer {

    private static final Logger log = LoggerFactory.getLogger(PriceConsumer.class);

    private final KafkaReceiver<String, String> kafkaReceiver;
    private final StockService stockService;
    private final StockPriceService priceService;
    private final ObjectMapper objectMapper;
    private Disposable subscription;

    public PriceConsumer(KafkaReceiver<String, String> kafkaReceiver,
                         StockService stockService,
                         StockPriceService priceService,
                         ObjectMapper objectMapper) {
        this.kafkaReceiver = kafkaReceiver;
        this.stockService = stockService;
        this.priceService = priceService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        subscription = kafkaReceiver.receive()
                .flatMap(record -> {
                    try {
                        JsonNode json = objectMapper.readTree(record.value());
                        String symbol = json.get("symbol").asText();

                        return stockService.findBySymbol(symbol)
                                .flatMap(stock -> {
                                    StockPrice price = new StockPrice();
                                    price.setStockId(stock.getId());
                                    price.setPrice(new BigDecimal(json.get("price").asText()));
                                    price.setChange(new BigDecimal(json.get("change").asText()));
                                    price.setChangePercent(new BigDecimal(json.get("changePercent").asText()));
                                    price.setTimestamp(LocalDateTime.parse(json.get("timestamp").asText()));
                                    return priceService.savePrice(price);
                                })
                                .doOnNext(saved -> log.debug("Saved price for {}: {}", symbol, saved.getPrice()))
                                .doFinally(sig -> record.receiverOffset().acknowledge());
                    } catch (Exception e) {
                        log.error("Error processing Kafka record: {}", record.value(), e);
                        record.receiverOffset().acknowledge();
                        return reactor.core.publisher.Mono.empty();
                    }
                })
                .subscribe(
                        result -> {},
                        error -> log.error("Kafka consumer error", error)
                );
        log.info("Kafka price consumer started");
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
