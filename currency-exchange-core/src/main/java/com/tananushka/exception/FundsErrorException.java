package com.tananushka.exception;

public class FundsErrorException extends RuntimeException {
   public FundsErrorException(String message) {
      super(message);
   }
}