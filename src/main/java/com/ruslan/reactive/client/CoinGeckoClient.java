package com.ruslan.reactive.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Component
public class CoinGeckoClient {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoClient.class);
    private static final String BASE_URL = "https://api.coingecko.com/api/v3";

    private final WebClient webClient;

    public CoinGeckoClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    /**
     * Получает текущие цены крипто через CoinGecko.
     * @param idToSymbol маппинг coingecko_id → наш символ (bitcoin → BTC)
     */
    public Flux<PriceData> fetchPrices(Map<String, String> idToSymbol) {
        String ids = String.join(",", idToSymbol.keySet());

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/simple/price")
                        .queryParam("ids", ids)
                        .queryParam("vs_currencies", "usd")
                        .queryParam("include_24hr_change", "true")
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(json -> Flux.fromIterable(idToSymbol.entrySet())
                        .filter(entry -> json.has(entry.getKey()))
                        .map(entry -> {
                            JsonNode coin = json.get(entry.getKey());
                            BigDecimal price = new BigDecimal(coin.get("usd").asText());
                            BigDecimal changePercent = coin.has("usd_24h_change") && !coin.get("usd_24h_change").isNull()
                                    ? new BigDecimal(coin.get("usd_24h_change").asText()).setScale(4, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO;
                            BigDecimal change = price.multiply(changePercent)
                                    .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

                            return new PriceData(entry.getValue(), price, change, changePercent);
                        }))
                .doOnError(e -> log.warn("CoinGecko API error: {}", e.getMessage()));
    }

    public record PriceData(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent) {}
}
