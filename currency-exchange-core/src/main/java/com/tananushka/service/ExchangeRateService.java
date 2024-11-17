package com.tananushka.service;

import com.tananushka.exception.ExchangeErrorException;
import com.tananushka.model.ExchangeRate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExchangeRateService {
   private final Map<String, ExchangeRate> exchangeRates = new ConcurrentHashMap<>();

   public void addExchangeRate(ExchangeRate rate) {
      String key = rate.getFromCurrency() + "-" + rate.getToCurrency();
      exchangeRates.put(key, rate);
   }

   public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
      String key = fromCurrency + "-" + toCurrency;
      ExchangeRate rate = exchangeRates.get(key);
      if (rate == null) {
         throw new ExchangeErrorException(
               String.format("Exchange rate not found for pair: %s-%s", fromCurrency, toCurrency)
         );
      }
      return rate.getRate();
   }

   public boolean hasExchangeRate(String fromCurrency, String toCurrency) {
      String key = fromCurrency + "-" + toCurrency;
      return exchangeRates.containsKey(key);
   }
}