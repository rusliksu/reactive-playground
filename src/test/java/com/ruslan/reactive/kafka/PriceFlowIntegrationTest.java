package com.ruslan.reactive.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.model.StockPrice;
import com.ruslan.reactive.repository.StockPriceRepository;
import com.ruslan.reactive.repository.StockRepository;
import com.ruslan.reactive.service.StockPriceService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@SpringBootTest(properties = {
        "app.fetcher.moex-interval-ms=999999",
        "app.fetcher.crypto-interval-ms=999999"
})
@Testcontainers
class PriceFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_stocks")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/test_stocks");
        registry.add("spring.r2dbc.username", () -> "test");
        registry.add("spring.r2dbc.password", () -> "test");
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", () -> "test");
        registry.add("spring.liquibase.password", () -> "test");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceRepository priceRepository;

    @Autowired
    private StockPriceService priceService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        priceRepository.deleteAll().block();
    }

    @Test
    void priceFlow_messageConsumed_savedToDatabase() {
        Stock stock = stockRepository.findBySymbol("SBER").block();
        if (stock == null) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of(
                    "symbol", "SBER",
                    "price", "285.50",
                    "change", "3.20",
                    "changePercent", "1.13",
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> producerProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );

        KafkaSender<String, String> testSender = KafkaSender.create(
                SenderOptions.create(producerProps));

        testSender.send(Mono.just(
                        SenderRecord.create(new ProducerRecord<>("stock-prices", "SBER", json), "SBER")))
                .blockLast(Duration.ofSeconds(10));
        testSender.close();

        StepVerifier.create(
                        Mono.delay(Duration.ofSeconds(3))
                                .then(priceRepository.findLastByStockId(stock.getId()))
                )
                .expectNextMatches(price ->
                        price.getPrice().compareTo(new BigDecimal("285.50")) == 0
                                && price.getStockId().equals(stock.getId()))
                .verifyComplete();
    }

    @Test
    void stockCrud_reactiveFlow_worksCorrectly() {
        StepVerifier.create(stockRepository.findAll())
                .expectNextCount(6)
                .verifyComplete();

        StepVerifier.create(stockRepository.findBySymbol("BTC"))
                .expectNextMatches(stock -> stock.getName().equals("Bitcoin")
                        && "USD".equals(stock.getCurrency())
                        && "bitcoin".equals(stock.getExternalId()))
                .verifyComplete();

        StepVerifier.create(stockRepository.findBySymbol("SBER"))
                .expectNextMatches(stock -> stock.getName().equals("Сбербанк")
                        && "RUB".equals(stock.getCurrency())
                        && "MOEX".equals(stock.getExchange()))
                .verifyComplete();
    }

    @Test
    void priceService_saveAndStream_worksCorrectly() {
        Stock stock = stockRepository.findBySymbol("ETH").block();
        if (stock == null) {
            return;
        }

        StockPrice price = new StockPrice(stock.getId(),
                new BigDecimal("2300.00"),
                new BigDecimal("20.00"),
                new BigDecimal("0.88"));

        StepVerifier.create(priceService.savePrice(price))
                .expectNextMatches(saved -> saved.getId() != null)
                .verifyComplete();

        StepVerifier.create(priceService.getLastPrice(stock.getId()))
                .expectNextMatches(p -> p.getPrice().compareTo(new BigDecimal("2300.00")) == 0)
                .verifyComplete();
    }
}
