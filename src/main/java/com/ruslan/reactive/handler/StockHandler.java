package com.ruslan.reactive.handler;

import com.ruslan.reactive.model.Stock;
import com.ruslan.reactive.model.StockPrice;
import com.ruslan.reactive.service.StockPriceService;
import com.ruslan.reactive.service.StockService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class StockHandler {

    private final StockService stockService;
    private final StockPriceService priceService;

    public StockHandler(StockService stockService, StockPriceService priceService) {
        this.stockService = stockService;
        this.priceService = priceService;
    }

    public Mono<ServerResponse> getAllStocks(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(stockService.findAll(), Stock.class);
    }

    public Mono<ServerResponse> getStockBySymbol(ServerRequest request) {
        String symbol = request.pathVariable("symbol");
        return stockService.findBySymbol(symbol)
                .flatMap(stock -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(stock))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> createStock(ServerRequest request) {
        return request.bodyToMono(Stock.class)
                .flatMap(stockService::create)
                .flatMap(stock -> ServerResponse
                        .created(URI.create("/api/stocks/" + stock.getSymbol()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(stock))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest().bodyValue(e.getMessage()));
    }

    public Mono<ServerResponse> deleteStock(ServerRequest request) {
        String symbol = request.pathVariable("symbol");
        return stockService.deleteBySymbol(symbol)
                .then(ServerResponse.noContent().build())
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> streamAllPrices(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(priceService.streamAllPrices(), StockPrice.class);
    }

    public Mono<ServerResponse> streamPricesForStock(ServerRequest request) {
        String symbol = request.pathVariable("symbol");
        return stockService.findBySymbol(symbol)
                .flatMap(stock -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(priceService.streamPricesForStock(stock.getId()), StockPrice.class))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getStockPriceHistory(ServerRequest request) {
        String symbol = request.pathVariable("symbol");
        int limit = request.queryParam("limit").map(Integer::parseInt).orElse(50);
        return stockService.findBySymbol(symbol)
                .flatMap(stock -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(priceService.getLatestPrices(stock.getId(), limit), StockPrice.class))
                .switchIfEmpty(ServerResponse.notFound().build());
    }
}
