package com.ruslan.reactive.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class TinkoffClient {

    private static final Logger log = LoggerFactory.getLogger(TinkoffClient.class);
    private static final String BASE_URL = "https://api.tinkoff.ru";

    private final WebClient webClient;

    public TinkoffClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Получает текущие цены для списка тикеров через Tinkoff API.
     * Вызывает отдельный запрос для каждого тикера и объединяет результаты.
     */
    public Flux<PriceData> fetchPrices(List<String> symbols) {
        return Flux.fromIterable(symbols)
                .flatMap(this::fetchPrice, 4)
                .doOnError(e -> log.warn("Tinkoff API error: {}", e.getMessage()));
    }

    private Mono<PriceData> fetchPrice(String ticker) {
        return webClient.get()
                .uri("/trading/stocks/get?ticker={ticker}", ticker)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .filter(json -> "Ok".equals(json.path("status").asText()))
                .map(json -> {
                    JsonNode payload = json.path("payload");
                    String symbol = payload.path("symbol").path("ticker").asText();

                    BigDecimal price = extractDecimal(payload.path("prices").path("last").path("value"));
                    BigDecimal change = extractDecimal(payload.path("earnings").path("absolute").path("value"));
                    BigDecimal relative = extractDecimal(payload.path("earnings").path("relative"));
                    BigDecimal changePercent = relative.multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);

                    return new PriceData(symbol, price, change, changePercent);
                })
                .doOnNext(data -> log.debug("Tinkoff: {} = {} ({}%)", data.symbol(), data.price(), data.changePercent()))
                .onErrorResume(e -> {
                    log.warn("Tinkoff fetch failed for {}: {}", ticker, e.getMessage());
                    return Mono.empty();
                });
    }

    private BigDecimal extractDecimal(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(node.asText());
    }

    public record PriceData(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent) {}
}
