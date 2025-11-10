package com.checkout.payment.gateway.model;

public record BankRequest(
    String card_number,
    String expiry_date,
    String currency,
    int amount,
    String cvv
) {
  public static BankRequest from(PostPaymentRequest request){
    return new BankRequest(
        request.getCardNumber(),
        String.format("%02d/%04d", Integer.parseInt(request.getExpiryMonth()),
            request.getExpiryYear()),
        request.getCurrency(),
        request.getAmount(),
        String.valueOf(request.getCvv())
    );
  }

}
