package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankRequest;
import com.checkout.payment.gateway.model.BankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static com.checkout.payment.gateway.enums.PaymentStatus.DECLINED;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  @Value("${bank.base-url:http://localhost:8080}")
  private String bankUrl;

  private final PaymentsRepository paymentsRepository;

  private final RestTemplate restTemplate;

  private static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "EUR", "GBP");

  public PaymentGatewayService(PaymentsRepository paymentsRepository, RestTemplate restTemplate) {
    this.paymentsRepository = paymentsRepository;
    this.restTemplate = restTemplate;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public UUID processPayment(PostPaymentRequest paymentRequest) {
    LOG.info("Received payment request for card ending in {}", toLastFour(paymentRequest.getCardNumber()));
    PaymentStatus status = authoriseWithBank(paymentRequest);

    UUID id = UUID.randomUUID();
    PostPaymentResponse response = buildResponse(paymentRequest, status, id);
    paymentsRepository.add(response);

    LOG.info("Processed payment id={} status={}", id, status.getName());
    return id;
  }

  //************************************** Helpers ***********************************************

  //sets the status according to the bank's response
  private PaymentStatus authoriseWithBank(PostPaymentRequest request){
    final String url = bankUrl + "/payments";
    try{
      var payload = BankRequest.from(request);
      var response = restTemplate.postForEntity(url, payload, BankResponse.class);
      var body = response.getBody();

      if (response.getStatusCode().is2xxSuccessful() && body != null) {
        var status = body.getStatus();
        LOG.info("Bank authorization result={}", status.getName());
        return status;
      }

      LOG.warn("Bank non-OK response: httpStatus={}, bodyPresent={}",
          response.getStatusCode(), body!= null);
      return DECLINED;

    } catch (RestClientException e){
      LOG.warn("Bank call failed: {}", e.getMessage());
      return DECLINED;
    }
  }

  //builds the response
  private PostPaymentResponse buildResponse(PostPaymentRequest request, PaymentStatus status, UUID id){
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(id);
    response.setStatus(status);
    response.setCardNumberLastFour(toLastFour(request.getCardNumber()));
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency().toUpperCase());
    response.setAmount(request.getAmount());

    return response;
  }

  //********************************** Validations ********************************************

  public boolean isValidRequest(PostPaymentRequest request){
    return isValidCardNumber(request.getCardNumber())
        && isExpiryMonthValid(request.getExpiryMonth())
        && isExpiryMonthAndYearValid(request.getExpiryYear(), request.getExpiryMonth())
        && isCurrencyValid(request.getCurrency())
        && isAmountValid(request.getAmount())
        && isCvvValid(request.getCvv());
  }
  private boolean isValidCardNumber(String cardNumber){
    //checks if blank
    if(cardNumber == null || cardNumber.isEmpty()) return false;

    //checks length
    int size = cardNumber.length();
    if(size < 14 || size > 19) return false;

    //checks only numeric
    for(int i = 0; i < size; i++){
      char c = cardNumber.charAt(i);
      if(c < '0' || c > '9') return false;
    }

    return true;
  }

  private boolean isExpiryMonthValid(int month){
    return month >= 1 && month <= 12;
  }

  private boolean isExpiryMonthAndYearValid(int year, int month){
    var now = java.time.YearMonth.now();
    var expiry = java.time.YearMonth.of(year, month);
    return expiry.isAfter(now);
  }

  private boolean isCurrencyValid(String currency){
    return currency != null && currency.length() == 3 && ALLOWED_CURRENCIES.contains(currency.toUpperCase());
  }

  private boolean isAmountValid(int amount){
   return amount > 0;
  }

  private boolean isCvvValid(int cvv){
    return cvv >= 100 && cvv <= 9999;
  }

  private String toLastFour(String cardNumber){
    if (cardNumber == null || cardNumber.length() < 4) return "";
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
