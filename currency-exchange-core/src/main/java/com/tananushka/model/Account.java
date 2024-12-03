package com.tananushka.model;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Account {
   private String id;
   private String ownerName;
   private AccountStatus status = AccountStatus.ACTIVE;
   private Map<String, Currency> currencies = new ConcurrentHashMap<>();
}