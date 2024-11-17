package com.tananushka;

import com.tananushka.exception.FundsErrorException;
import com.tananushka.operations.AccountOperations;
import com.tananushka.operations.CurrencyOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@SpringBootApplication
@Slf4j
public class CurrencyExchangeApplication {
   private static final String[] NAMES = {
         "Giorgi", "Davit", "Nikoloz", "Luka", "Sandro",
         "Nino", "Mariam", "Tamar", "Salome", "Ana"
   };
   private static final String[] SURNAMES = {
         "Beridze", "Giorgadze", "Maisuradze", "Kapanadze", "Tsintsadze",
         "Gelashvili", "Tsiklauri", "Chavchavadze", "Javakhishvili", "Lomidze"
   };
   private static final String ACCOUNT = "ACC%03d";
   private final Random random = new Random();

   public static void main(String[] args) {
      SpringApplication.run(CurrencyExchangeApplication.class, args);
   }

   @Bean
   public CommandLineRunner demo(CurrencyOperations currencyOps, AccountOperations accountOps) {
      return args -> {
         setupExchangeRates(currencyOps);
         createTestAccounts(currencyOps);
         performConcurrentExchanges(currencyOps);
         performSingleAccountConcurrentExchanges(currencyOps);
         performBulkOperations(currencyOps);
         demonstrateAccountOperations(accountOps);
         currencyOps.shutdown();
      };
   }

   private void setupExchangeRates(CurrencyOperations currencyOps) {
      log.info("Setting up exchange rates...");

      currencyOps.registerExchangeRate("USD", "EUR", new BigDecimal("0.85"));
      currencyOps.registerExchangeRate("USD", "GBP", new BigDecimal("0.73"));
      currencyOps.registerExchangeRate("USD", "GEL", new BigDecimal("2.65"));

      currencyOps.registerExchangeRate("EUR", "GBP", new BigDecimal("0.86"));
      currencyOps.registerExchangeRate("EUR", "GEL", new BigDecimal("3.12"));
      currencyOps.registerExchangeRate("GBP", "GEL", new BigDecimal("3.63"));
      log.info("Exchange rates setup completed");
   }

   private void createTestAccounts(CurrencyOperations currencyOps) throws IOException {
      log.info("Creating test accounts...");

      for (int i = 0; i < 20; i++) {
         String name = NAMES[random.nextInt(NAMES.length)];
         String surname = SURNAMES[random.nextInt(SURNAMES.length)];
         String accountId = String.format(ACCOUNT, i + 1);

         Map<String, BigDecimal> balance = new HashMap<>();
         balance.put("USD", new BigDecimal(random.nextInt(10000) + 1000));
         balance.put("EUR", new BigDecimal(random.nextInt(8000) + 1000));
         balance.put("GBP", new BigDecimal(random.nextInt(7000) + 1000));
         balance.put("GEL", new BigDecimal(random.nextInt(20000) + 5000));

         currencyOps.registerNewAccount(accountId, name + " " + surname, balance);
         log.info("Created account for {} {} with ID: {}", name, surname, accountId);
      }

      log.info("Test accounts created successfully");
   }

   private void performConcurrentExchanges(CurrencyOperations currencyOps) {
      log.info("Starting concurrent exchange operations...");
      List<String> currencies = Arrays.asList("USD", "EUR", "GBP", "GEL");

      List<CompletableFuture<Boolean>> exchangeOperations = IntStream.range(0, 50)
            .mapToObj(i -> {
               String accountId = String.format(ACCOUNT, random.nextInt(20) + 1);
               String fromCurrency = currencies.get(random.nextInt(currencies.size()));
               String toCurrency = currencies.get(random.nextInt(currencies.size()));
               BigDecimal amount = new BigDecimal(random.nextInt(1000) + 100);

               return performExchangeSafely(currencyOps, accountId, fromCurrency, toCurrency, amount);
            })
            .toList();

      CompletableFuture.allOf(exchangeOperations.toArray(new CompletableFuture[0]))
            .thenRun(() -> log.info("All exchange operations completed"))
            .join();
   }

   private void performSingleAccountConcurrentExchanges(CurrencyOperations currencyOps) {
      log.info("Starting single-account concurrent exchange operations test...");
      List<String> currencies = Arrays.asList("USD", "EUR", "GBP", "GEL");

      String testAccountId = String.format(ACCOUNT, 1);
      Map<String, AtomicInteger> operationCounter = new ConcurrentHashMap<>();
      currencies.forEach(currency -> operationCounter.put(currency, new AtomicInteger(0)));

      List<CompletableFuture<Boolean>> singleAccountOperations = IntStream.range(0, 100)
            .mapToObj(i -> {
               String fromCurrency = currencies.get(i % currencies.size());
               String toCurrency = currencies.get((i + 1) % currencies.size());
               BigDecimal amount = new BigDecimal("100.00");

               operationCounter.get(fromCurrency).incrementAndGet();

               return performExchangeSafely(currencyOps, testAccountId, fromCurrency, toCurrency, amount);
            })
            .toList();

      CompletableFuture.allOf(singleAccountOperations.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
               try {
                  verifyAccountBalances(testAccountId, operationCounter, currencyOps);
               } catch (IOException e) {
                  log.error("Failed to verify account balances: {}", e.getMessage());
               }
            })
            .join();

      log.info("Single-account concurrent exchange operations completed");
   }

   private void verifyAccountBalances(
         String accountId,
         Map<String, AtomicInteger> operationCounter,
         CurrencyOperations currencyOps) throws IOException {
      log.info("Verifying final balances for account {}...", accountId);

      AccountOperations accountOps = new AccountOperations(currencyOps.getAccountService());
      for (Map.Entry<String, AtomicInteger> entry : operationCounter.entrySet()) {
         String currency = entry.getKey();
         int operations = entry.getValue().get();

         Optional<BigDecimal> finalBalance = accountOps.getBalance(accountId, currency);
         finalBalance.ifPresent(balance ->
               log.info("Final balance for {} {}: {} after {} operations",
                     accountId, currency, balance, operations)
         );
      }
   }

   private void performBulkOperations(CurrencyOperations currencyOps) throws IOException {
      log.info("Performing bulk transfer operations...");

      for (int i = 0; i < 30; i++) {
         String fromAccount = String.format(ACCOUNT, random.nextInt(20) + 1);
         String toAccount;
         do {
            toAccount = String.format(ACCOUNT, random.nextInt(20) + 1);
         } while (toAccount.equals(fromAccount));

         String[] currencies = {"USD", "EUR", "GBP", "GEL"};
         String currency = currencies[random.nextInt(currencies.length)];
         BigDecimal amount = new BigDecimal(random.nextInt(500) + 50);

         try {
            currencyOps.transferFunds(fromAccount, toAccount, currency, amount);
            log.info("Successfully transferred {} {} from {} to {}",
                  amount, currency, fromAccount, toAccount);
         } catch (FundsErrorException e) {
            log.error("Insufficient funds: {}", e.getMessage());
         } catch (IllegalArgumentException e) {
            log.error("Invalid account: {}", e.getMessage());
         }
      }
   }

   private CompletableFuture<Boolean> performExchangeSafely(
         CurrencyOperations ops, String accountId, String fromCurrency,
         String toCurrency, BigDecimal amount) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            if (!fromCurrency.equals(toCurrency)) {
               ops.performExchange(accountId, fromCurrency, toCurrency, amount);
               log.info("Exchanged {} {} to {} for account {}",
                     amount, fromCurrency, toCurrency, accountId);
               return true;
            }
            return false;
         } catch (Exception e) {
            log.error("Exchange operation failed: {} {} -> {} {}: {}",
                  amount, fromCurrency, toCurrency, accountId, e.getMessage());
            return false;
         }
      });
   }

   private void demonstrateAccountOperations(AccountOperations accountOps) throws IOException {
      log.info("Demonstrating account operations...");
      String[] currencies = {"USD", "EUR", "GBP", "GEL"};

      for (int j = 0; j < 10; j++) {
         String accountId = String.format(ACCOUNT, random.nextInt(20) + 1);
         String currency = currencies[random.nextInt(currencies.length)];

         accountOps.getBalance(accountId, currency)
               .ifPresent(balance -> log.info("{} {} balance: {}",
                     accountId, currency, balance));
      }

      for (int i = 0; i < 5; i++) {
         String accountId = String.format(ACCOUNT, random.nextInt(20) + 1);
         accountOps.freezeAccount(accountId);
         accountOps.unfreezeAccount(accountId);
      }

      log.info("Account operations demonstration completed");
   }
}