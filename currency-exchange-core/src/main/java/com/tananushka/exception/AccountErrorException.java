package com.tananushka.exception;

public class AccountErrorException extends RuntimeException {
   public AccountErrorException(String message) {
      super(message);
   }
}