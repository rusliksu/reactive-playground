package com.ruslan.reactive.router;

import com.ruslan.reactive.handler.StockHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
public class StockRouter {

    @Bean
    public RouterFunction<ServerResponse> stockRoutes(StockHandler handler) {
        return RouterFunctions.route()
                .path("/api/stocks", builder -> builder
                        .GET("/stream", accept(MediaType.TEXT_EVENT_STREAM), handler::streamAllPrices)
                        .GET("/{symbol}/prices/stream", accept(MediaType.TEXT_EVENT_STREAM), handler::streamPricesForStock)
                        .GET("/{symbol}/prices", accept(MediaType.APPLICATION_JSON), handler::getStockPriceHistory)
                        .GET("/{symbol}", accept(MediaType.APPLICATION_JSON), handler::getStockBySymbol)
                        .GET("", accept(MediaType.APPLICATION_JSON), handler::getAllStocks)
                        .POST("", accept(MediaType.APPLICATION_JSON), handler::createStock)
                        .DELETE("/{symbol}", handler::deleteStock)
                )
                .build();
    }
}
