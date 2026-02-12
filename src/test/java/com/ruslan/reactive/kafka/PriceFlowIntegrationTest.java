package com.ruslan.reactive.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.model.StockPrice;
import com.ruslan.reactive.repository.StockPriceRepository;
import com.ruslan.reactive.repository.StockRepository;
import com.ruslan.reactive.service.StockPriceService;
import com.ruslan.reactive.service.StockService;
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
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@SpringBootTest(properties = "app.price-simulator.enabled=false")
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
        Stock stock = stockRepository.findBySymbol("AAPL").block();
        if (stock == null) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of(
                    "symbol", "AAPL",
                    "price", "180.50",
                    "change", "2.00",
                    "changePercent", "1.12",
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

        testSender.send(reactor.core.publisher.Mono.just(
                        SenderRecord.create(new ProducerRecord<>("stock-prices", "AAPL", json), "AAPL")))
                .blockLast(Duration.ofSeconds(10));
        testSender.close();

        StepVerifier.create(
                        reactor.core.publisher.Mono.delay(Duration.ofSeconds(3))
                                .then(priceRepository.findLastByStockId(stock.getId()))
                )
                .expectNextMatches(price ->
                        price.getPrice().compareTo(new java.math.BigDecimal("180.50")) == 0
                                && price.getStockId().equals(stock.getId()))
                .verifyComplete();
    }

    @Test
    void stockCrud_reactiveFlow_worksCorrectly() {
        StepVerifier.create(stockRepository.findAll())
                .expectNextCount(5)
                .verifyComplete();

        StepVerifier.create(stockRepository.findBySymbol("BTC"))
                .expectNextMatches(stock -> stock.getName().equals("Bitcoin"))
                .verifyComplete();
    }

    @Test
    void priceService_saveAndStream_worksCorrectly() {
        Stock stock = stockRepository.findBySymbol("ETH").block();
        if (stock == null) {
            return;
        }

        StockPrice price = new StockPrice(stock.getId(),
                new java.math.BigDecimal("2300.00"),
                new java.math.BigDecimal("20.00"),
                new java.math.BigDecimal("0.88"));

        StepVerifier.create(priceService.savePrice(price))
                .expectNextMatches(saved -> saved.getId() != null)
                .verifyComplete();

        StepVerifier.create(priceService.getLastPrice(stock.getId()))
                .expectNextMatches(p -> p.getPrice().compareTo(new java.math.BigDecimal("2300.00")) == 0)
                .verifyComplete();
    }
}
