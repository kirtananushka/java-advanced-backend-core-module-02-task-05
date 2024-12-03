package com.tananushka.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExchangeRate {
   private String fromCurrency;
   private String toCurrency;
   private BigDecimal rate;
}