package com.ruslan.reactive.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("stock_price")
public class StockPrice {

    @Id
    private Long id;
    private Long stockId;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;
    private LocalDateTime timestamp;

    public StockPrice() {
    }

    public StockPrice(Long stockId, BigDecimal price, BigDecimal change, BigDecimal changePercent) {
        this.stockId = stockId;
        this.price = price;
        this.change = change;
        this.changePercent = changePercent;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStockId() {
        return stockId;
    }

    public void setStockId(Long stockId) {
        this.stockId = stockId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getChange() {
        return change;
    }

    public void setChange(BigDecimal change) {
        this.change = change;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "StockPrice{stockId=" + stockId + ", price=" + price + ", change=" + change + "}";
    }
}
