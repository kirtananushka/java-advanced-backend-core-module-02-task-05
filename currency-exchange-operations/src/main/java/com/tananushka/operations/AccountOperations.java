package com.tananushka.operations;

import com.tananushka.model.AccountStatus;
import com.tananushka.model.Currency;
import com.tananushka.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountOperations {
   private final AccountService accountService;

   public Optional<BigDecimal> getBalance(String accountId, String currency) throws IOException {
      return accountService.getAccount(accountId)
            .map(account -> account.getCurrencies().get(currency))
            .map(Currency::getAmount);
   }

   public void freezeAccount(String accountId) throws IOException {
      accountService.setAccountStatus(accountId, AccountStatus.FROZEN);
      log.info("Account {} has been frozen", accountId);
   }

   public void unfreezeAccount(String accountId) throws IOException {
      accountService.setAccountStatus(accountId, AccountStatus.ACTIVE);
      log.info("Account {} has been unfrozen", accountId);
   }

   public void closeAccount(String accountId) throws IOException {
      accountService.setAccountStatus(accountId, AccountStatus.CLOSED);
      log.info("Account {} has been closed", accountId);
   }
}