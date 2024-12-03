package com.tananushka.service;

import com.tananushka.dao.AccountDao;
import com.tananushka.exception.AccountErrorException;
import com.tananushka.exception.AccountStatusException;
import com.tananushka.exception.FundsErrorException;
import com.tananushka.model.Account;
import com.tananushka.model.AccountStatus;
import com.tananushka.model.Currency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
   private final AccountDao accountDao;
   private final ExchangeRateService exchangeRateService;
   private final ConcurrentHashMap<String, Lock> accountLocks = new ConcurrentHashMap<>();

   public void validateAccountActive(Account account) {
      if (account.getStatus() != AccountStatus.ACTIVE) {
         throw new AccountStatusException(
               String.format("Account %s is %s and cannot perform operations",
                     account.getId(), account.getStatus())
         );
      }
   }

   public void setAccountStatus(String accountId, AccountStatus status) throws IOException {
      Lock accountLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
      accountLock.lock();
      try {
         Account account = accountDao.getAccount(accountId)
               .orElseThrow(() -> new AccountErrorException("Account not found: " + accountId));

         if (status == AccountStatus.CLOSED && account.getStatus() != AccountStatus.FROZEN) {
            throw new AccountStatusException("Account must be frozen before closing");
         }

         account.setStatus(status);
         accountDao.saveAccount(account);
         log.info("Account {} status changed to {}", accountId, status);
      } finally {
         accountLock.unlock();
      }
   }

   public void createAccount(Account account) throws IOException {
      accountDao.saveAccount(account);
      log.info("Created new account: {}", account.getId());
   }

   public synchronized void exchange(String accountId, String fromCurrency, String toCurrency,
                                     BigDecimal amount) throws IOException {
      Lock accountLock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
      accountLock.lock();
      try {
         Account account = accountDao.getAccount(accountId)
               .orElseThrow(() -> new AccountErrorException("Account not found: " + accountId));

         validateAccountActive(account);

         Currency sourceCurrency = account.getCurrencies().get(fromCurrency);
         if (sourceCurrency == null || sourceCurrency.getAmount().compareTo(amount) < 0) {
            throw new FundsErrorException("Insufficient funds for currency: " + fromCurrency);
         }

         BigDecimal rate = exchangeRateService.getExchangeRate(fromCurrency, toCurrency);
         BigDecimal convertedAmount = amount.multiply(rate);

         sourceCurrency.setAmount(sourceCurrency.getAmount().subtract(amount));
         account.getCurrencies().compute(toCurrency, (k, v) -> {
            if (v == null) {
               Currency newCurrency = new Currency();
               newCurrency.setCode(toCurrency);
               newCurrency.setAmount(convertedAmount);
               return newCurrency;
            }
            v.setAmount(v.getAmount().add(convertedAmount));
            return v;
         });

         accountDao.saveAccount(account);
         log.info("Exchanged {} {} to {} {} for account {}",
               amount, fromCurrency, convertedAmount, toCurrency, accountId);
      } finally {
         accountLock.unlock();
      }
   }

   public Optional<Account> getAccount(String accountId) throws IOException {
      return accountDao.getAccount(accountId);
   }

   public void updateAccount(Account account) throws IOException {
      accountDao.saveAccount(account);
      log.info("Updated account: {}", account.getId());
   }
}
