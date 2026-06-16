package io.antigen.core.integration.mockrestapi;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static io.restassured.RestAssured.given;

@Execution(ExecutionMode.CONCURRENT)
public class PaymentTest {

    private static final String BEARER_TOKEN = "Bearer 123";

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "http://localhost:8089";
    }

    @Test
    public void testCreatePayment() {
        String requestBody = """
            {
                "amount": 2500,
                "currency": "usd", 
                "orderId": "order_456789",
                "paymentMethod": {
                    "type": "card",
                    "cardId": "pm_1Nz3Q72eZvKYlo2CvJEwRG4c"
                }
            }
            """;

        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/v1/payments");

        Assertions.assertEquals(201, response.getStatusCode(), "Check status code");
        Assertions.assertNotNull(response.jsonPath().getString("id"), "check payment id field");
        Assertions.assertEquals("pending", response.jsonPath().getString("status"), "check status field");
        Assertions.assertEquals(2500, response.jsonPath().getInt("amount"), "check amount field");
        Assertions.assertEquals("usd", response.jsonPath().getString("currency"), "check currency field");
    }

    @Test
    public void testGetPayment() {
        String paymentId = "pay_1Nz3Q82eZvKYlo2C9EbE7PKr";

        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .when()
                .get("/api/v1/payments/" + paymentId);

        Assertions.assertEquals(200, response.getStatusCode(), "Check status code");
        Assertions.assertEquals(paymentId, response.jsonPath().getString("id"), "check payment id field");
        Assertions.assertEquals("succeeded", response.jsonPath().getString("status"), "check status field");
        Assertions.assertEquals(2500, response.jsonPath().getInt("amount"), "check amount field");
        Assertions.assertNotNull(response.jsonPath().getString("customer.email"), "check customer email");
        Assertions.assertNotNull(response.jsonPath().getString("paymentMethod.card.last4"), "check card last4");
    }

    @Test
    public void testPaymentMethodValidation() {
        String paymentId = "pay_1Nz3Q82eZvKYlo2C9EbE7PKr";

        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .when()
                .get("/api/v1/payments/" + paymentId);

        Assertions.assertEquals("card", response.jsonPath().getString("paymentMethod.type"));
        Assertions.assertEquals("visa", response.jsonPath().getString("paymentMethod.card.brand"));
        Assertions.assertEquals("4242", response.jsonPath().getString("paymentMethod.card.last4"));
        Assertions.assertEquals(12, response.jsonPath().getInt("paymentMethod.card.expiryMonth"));
        Assertions.assertEquals(2025, response.jsonPath().getInt("paymentMethod.card.expiryYear"));
        Assertions.assertEquals("US", response.jsonPath().getString("paymentMethod.card.country"));
    }

    @Test
    public void testPaymentTimestamps() {
        String paymentId = "pay_1Nz3Q82eZvKYlo2C9EbE7PKr";

        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .when()
                .get("/api/v1/payments/" + paymentId);

        Assertions.assertNotNull(response.jsonPath().getString("timestamps.createdAt"));
        Assertions.assertNotNull(response.jsonPath().getString("timestamps.updatedAt"));
        Assertions.assertNotNull(response.jsonPath().getString("timestamps.succeededAt"));
        
        String createdAt = response.jsonPath().getString("timestamps.createdAt");
        Assertions.assertTrue(createdAt.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"));
    }

    @Test
    public void testPaymentRefundsArray() {
        String paymentId = "pay_1Nz3Q82eZvKYlo2C9EbE7PKr";

        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .when()
                .get("/api/v1/payments/" + paymentId);

        Assertions.assertEquals(0, response.jsonPath().getInt("refunds.total"));
        Assertions.assertTrue(response.jsonPath().getList("refunds.data").isEmpty());
    }
}
