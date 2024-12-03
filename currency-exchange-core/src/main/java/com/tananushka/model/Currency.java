package com.tananushka.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Currency {
   private String code;
   private String name;
   private BigDecimal amount;
}