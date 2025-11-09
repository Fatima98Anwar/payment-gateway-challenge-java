package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;

public record BankResponse(
    boolean authorized,
    String authorization_code
) {
  public PaymentStatus getStatus() {
    return authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
  }
}
