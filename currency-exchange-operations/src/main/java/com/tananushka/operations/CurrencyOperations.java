package com.tananushka.operations;

import com.tananushka.exception.FundsErrorException;
import com.tananushka.model.Account;
import com.tananushka.model.Currency;
import com.tananushka.model.ExchangeRate;
import com.tananushka.service.AccountService;
import com.tananushka.service.ExchangeRateService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyOperations {
   @Getter
   private final AccountService accountService;
   private final ExchangeRateService exchangeRateService;
   private final ExecutorService executorService = Executors.newFixedThreadPool(5);

   public void registerNewAccount(String ownerId, String ownerName, Map<String, BigDecimal> initialBalances) throws IOException {
      Account account = createAccount(ownerId, ownerName, initialBalances);
      accountService.createAccount(account);
      logAccountRegistration(ownerName, initialBalances.size());
   }

   public void registerExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate) {
      ExchangeRate exchangeRate = createExchangeRate(fromCurrency, toCurrency, rate);
      ExchangeRate inverseRate = createInverseExchangeRate(fromCurrency, toCurrency, rate);

      exchangeRateService.addExchangeRate(exchangeRate);
      exchangeRateService.addExchangeRate(inverseRate);

      log.info("Registered exchange rate: 1 {} = {} {}", fromCurrency, rate, toCurrency);
   }

   public void performExchange(String accountId, String fromCurrency, String toCurrency, BigDecimal amount) {
      CompletableFuture.supplyAsync(() -> {
         try {
            accountService.exchange(accountId, fromCurrency, toCurrency, amount);
            return true;
         } catch (Exception e) {
            log.error("Exchange operation failed for account {}", accountId);
            return false;
         }
      }, executorService);
   }

   public void transferFunds(String fromAccountId, String toAccountId, String currency, BigDecimal amount) throws IOException {
      Account fromAccount = getAccount(fromAccountId);
      Account toAccount = getAccount(toAccountId);

      validateSufficientFunds(fromAccount, currency, amount, fromAccountId);

      synchronized (this) {
         try {
            accountService.exchange(fromAccountId, currency, currency, amount);

            Currency targetCurrency = toAccount.getCurrencies().computeIfAbsent(currency, k -> createCurrency(currency));

            targetCurrency.setAmount(targetCurrency.getAmount().add(amount));
            accountService.updateAccount(toAccount);

            log.info("Transferred {} {} from account {} to account {}",
                  amount, currency, fromAccountId, toAccountId);
         } catch (Exception e) {
            log.error("Fund transfer failed: {}", e.getMessage());
            throw new FundsErrorException("Fund transfer failed: " + e.getMessage());
         }
      }
   }

   public void shutdown() {
      executorService.shutdown();
   }

   private Account createAccount(String ownerId, String ownerName, Map<String, BigDecimal> initialBalances) {
      Account account = new Account();
      account.setId(ownerId);
      account.setOwnerName(ownerName);
      initialBalances.forEach((currencyCode, amount) -> {
         Currency currency = createCurrency(currencyCode, amount);
         account.getCurrencies().put(currencyCode, currency);
      });
      return account;
   }

   private void logAccountRegistration(String ownerName, int numCurrencies) {
      log.info("Registered new account for {} with {} currencies", ownerName, numCurrencies);
   }

   private ExchangeRate createExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate) {
      ExchangeRate exchangeRate = new ExchangeRate();
      exchangeRate.setFromCurrency(fromCurrency);
      exchangeRate.setToCurrency(toCurrency);
      exchangeRate.setRate(rate);
      return exchangeRate;
   }

   private ExchangeRate createInverseExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate) {
      ExchangeRate inverseRate = new ExchangeRate();
      inverseRate.setFromCurrency(toCurrency);
      inverseRate.setToCurrency(fromCurrency);
      inverseRate.setRate(BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP));
      return inverseRate;
   }

   private Account getAccount(String accountId) throws IOException {
      return accountService.getAccount(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
   }

   private void validateSufficientFunds(Account fromAccount, String currency, BigDecimal amount, String fromAccountId) {
      Currency sourceCurrency = fromAccount.getCurrencies().get(currency);
      if (sourceCurrency == null || sourceCurrency.getAmount().compareTo(amount) < 0) {
         throw new FundsErrorException(
               String.format("Insufficient %s funds in account %s. Required: %s, Available: %s",
                     currency, fromAccountId, amount,
                     sourceCurrency != null ? sourceCurrency.getAmount() : "0")
         );
      }
   }

   private Currency createCurrency(String currencyCode) {
      return createCurrency(currencyCode, BigDecimal.ZERO);
   }

   private Currency createCurrency(String currencyCode, BigDecimal amount) {
      Currency currency = new Currency();
      currency.setCode(currencyCode);
      currency.setAmount(amount);
      return currency;
   }
}