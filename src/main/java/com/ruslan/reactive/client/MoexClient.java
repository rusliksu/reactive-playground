package com.ruslan.reactive.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class MoexClient {

    private static final Logger log = LoggerFactory.getLogger(MoexClient.class);
    private static final String BASE_URL = "https://iss.moex.com/iss";

    private final WebClient webClient;

    public MoexClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Получает текущие цены для списка тикеров MOEX.
     * MOEX ISS marketdata: SECID, LAST, CHANGE, LASTTOPREVPRICE
     */
    public Flux<PriceData> fetchPrices(java.util.List<String> symbols) {
        String securities = String.join(",", symbols);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/engines/stock/markets/shares/boards/TQBR/securities.json")
                        .queryParam("iss.meta", "off")
                        .queryParam("iss.only", "marketdata")
                        .queryParam("marketdata.columns", "SECID,LAST,CHANGE,LASTTOPREVPRICE")
                        .queryParam("securities", securities)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(json -> {
                    JsonNode data = json.path("marketdata").path("data");
                    return Flux.fromIterable(() -> data.elements())
                            .filter(row -> !row.get(1).isNull())
                            .map(row -> new PriceData(
                                    row.get(0).asText(),
                                    new BigDecimal(row.get(1).asText()),
                                    row.get(2).isNull() ? BigDecimal.ZERO : new BigDecimal(row.get(2).asText()),
                                    row.get(3).isNull() ? BigDecimal.ZERO : new BigDecimal(row.get(3).asText())
                            ));
                })
                .doOnError(e -> log.warn("MOEX API error: {}", e.getMessage()));
    }

    public record PriceData(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent) {}
}
