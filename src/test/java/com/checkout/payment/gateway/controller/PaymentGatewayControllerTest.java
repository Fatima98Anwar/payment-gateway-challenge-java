package com.checkout.payment.gateway.controller;


import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;
  @Autowired
  private RestTemplate restTemplate;

  private MockRestServiceServer bankServer;

  @BeforeEach
  void setup() {
    // Bind a server to the same RestTemplate the service uses
    bankServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2027);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/api/payments/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/payments/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenPaymentCreated_itCanBeRetrieved() throws Exception {
    bankServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"authorized\":true}", APPLICATION_JSON));

    String payload = """
    {"card_number":"4111111111111111","expiry_month":12,"expiry_year":2029,
     "currency":"USD","amount":1050,"cvv":123}
  """;

    var result = mvc.perform(post("/api/payments")
            .contentType(APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn();

    // Extract id with JsonPath to keep it lightweight
    String body = result.getResponse().getContentAsString();
    String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

    mvc.perform(MockMvcRequestBuilders.get("/api/payments/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(1111));
  }


  @Test
  void whenPaymentGetsAuthorisedByBank_AuthorisedIsReturned() throws Exception{
    bankServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"authorized\":true,\"authorization_code\":\"abc\"}",
            APPLICATION_JSON));

    String payload = """
      { "card_number":"4111111111111111",
       "expiry_month":12,
       "expiry_year":2029,
       "currency":"USD",
        "amount":1050,
        "cvv":123 }
      """;

    mvc.perform(post("/api/payments")
        .contentType(APPLICATION_JSON)
        .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(1111))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2029))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1050));

    bankServer.verify();
  }

  @Test
  void whenPaymentGetsDeclinedByBank_DeclinedIsReturned() throws Exception{
    bankServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"authorized\":false}", APPLICATION_JSON));

    String payload = """
      { "card_number":"4111111111111112",
       "expiry_month":12,
       "expiry_year":2029,
       "currency":"USD",
        "amount":1050,
        "cvv":123 }
      """;

    mvc.perform(post("/api/payments")
            .contentType(APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(1112))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2029))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1050));

    bankServer.verify();
  }

  @Test
  void whenPaymentIsInValid_RejectedIsReturned() throws Exception{
    String badPayload = """
      { "card_number":"4111-1111-1111-1111",
       "expiry_month":12,
       "expiry_year":2029,
       "currency":"USD",
        "amount":1050,
        "cvv":123 }
      """;

    mvc.perform(post("/api/payments")
            .contentType(APPLICATION_JSON)
            .content(badPayload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));


    bankServer.verify(); //verifies no call was made
  }

  @Test
  void whenBankReturns503_DeclinedIsReturned() throws Exception {
    bankServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    String payload = """
    {"card_number":"4111111111111110","expiry_month":12,"expiry_year":2029,
     "currency":"USD","amount":1050,"cvv":123}
  """;

    mvc.perform(post("/api/payments").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"));

    bankServer.verify();
  }

  @Test
  void whenRequestInvalid_gatewayRejects_andBankIsNotCalled() throws Exception {
    String bad = """
    {"card_number":"4111-1111-1111-1111","expiry_month":12,"expiry_year":2029,
     "currency":"USD","amount":1050,"cvv":123}
  """;

    mvc.perform(post("/api/payments").contentType(APPLICATION_JSON).content(bad))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));

    // If the gateway had called the bank, MockRestServiceServer would throw
    bankServer.verify();
  }

  @Test
  void whenAmountNotPositive_Rejected() throws Exception {
    String payload = """
    {"card_number":"4111111111111111","expiry_month":12,"expiry_year":2029,
     "currency":"USD","amount":0,"cvv":123}
  """;

    mvc.perform(post("/api/payments").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"));

    bankServer.verify(); // ensures no bank call
  }


  @Test
  void whenCurrencyLowercase_usd_isAccepted_andUppercasedInResponse() throws Exception {
    bankServer.expect(requestTo("http://localhost:8080/payments"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"authorized\":true}", APPLICATION_JSON));

    String payload = """
    {"card_number":"4111111111111111","expiry_month":12,"expiry_year":2029,
     "currency":"usd","amount":1050,"cvv":123}
  """;

    mvc.perform(post("/api/payments").contentType(APPLICATION_JSON).content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.currency").value("USD"));

    bankServer.verify();
  }
}



