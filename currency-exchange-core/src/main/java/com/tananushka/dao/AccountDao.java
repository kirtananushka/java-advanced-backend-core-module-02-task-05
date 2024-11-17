package com.tananushka.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tananushka.model.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Repository
public class AccountDao {
   private static final String ACCOUNTS_DIR = "accounts/";
   private final ObjectMapper objectMapper;
   private final ConcurrentHashMap<String, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();

   public AccountDao(ObjectMapper objectMapper) throws IOException {
      this.objectMapper = objectMapper;
      Path accountsPath = Paths.get(ACCOUNTS_DIR);
      if (!Files.exists(accountsPath)) {
         Files.createDirectories(accountsPath);
         log.info("Created accounts directory at: {}", accountsPath);
      }
   }

   public void saveAccount(Account account) throws IOException {
      ReentrantReadWriteLock lock = fileLocks.computeIfAbsent(
            account.getId(), k -> new ReentrantReadWriteLock()
      );
      lock.writeLock().lock();
      try {
         Path filePath = Paths.get(ACCOUNTS_DIR + account.getId() + ".json");
         objectMapper.writeValue(filePath.toFile(), account);
         log.info("Account saved: {} at path: {}", account.getId(), filePath);
      } finally {
         lock.writeLock().unlock();
      }
   }

   public Optional<Account> getAccount(String accountId) throws IOException {
      ReentrantReadWriteLock lock = fileLocks.computeIfAbsent(
            accountId, k -> new ReentrantReadWriteLock()
      );
      lock.readLock().lock();
      try {
         Path filePath = Paths.get(ACCOUNTS_DIR + accountId + ".json");
         if (!Files.exists(filePath)) {
            log.warn("Account file not found: {}", filePath);
            return Optional.empty();
         }

         Account account = objectMapper.readValue(filePath.toFile(), Account.class);
         return Optional.of(account);
      } finally {
         lock.readLock().unlock();
      }
   }
}