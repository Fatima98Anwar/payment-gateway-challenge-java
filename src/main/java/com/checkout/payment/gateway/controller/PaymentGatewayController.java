package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.checkout.payment.gateway.enums.PaymentStatus.REJECTED;

@Tag(name = "Payments", description = "Process and retrieve payments")
@RestController
@RequestMapping("/api")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @Operation(summary = "Create a payment")
  @PostMapping("/payments")
  public ResponseEntity<?> createPayment(@RequestBody PostPaymentRequest request){
    //stops request here if invalid
    if(!paymentGatewayService.isValidRequest(request)){
      return ResponseEntity.badRequest().body(Map.of("status", REJECTED.getName()));
    }

    UUID id = paymentGatewayService.processPayment(request);
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.CREATED);
  }

  @Operation(summary = "Get a payment by id")
  @GetMapping("/payments/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }
}
