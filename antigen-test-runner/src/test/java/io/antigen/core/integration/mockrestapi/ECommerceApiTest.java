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
public class ECommerceApiTest {

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

        Assertions.assertEquals(201, response.getStatusCode());
        Assertions.assertNotNull(response.jsonPath().getString("id"));
        Assertions.assertEquals("pending", response.jsonPath().getString("status"));
        Assertions.assertEquals(2500, response.jsonPath().getInt("amount"));
        Assertions.assertEquals("usd", response.jsonPath().getString("currency"));
        Assertions.assertNotNull(response.jsonPath().getString("customer.email"));
        Assertions.assertNotNull(response.jsonPath().getString("paymentMethod.card.last4"));
    }

    @Test
    public void testGetPaymentDetails() {
        String paymentId = "pay_1Nz3Q82eZvKYlo2C9EbE7PKr";

        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .when()
                .get("/api/v1/payments/" + paymentId);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals(paymentId, response.jsonPath().getString("id"));
        Assertions.assertEquals("succeeded", response.jsonPath().getString("status"));
        Assertions.assertEquals(2500, response.jsonPath().getInt("amount"));

        Assertions.assertNotNull(response.jsonPath().getString("customer.name"));
        Assertions.assertNotNull(response.jsonPath().getString("billing.address.city"));
        Assertions.assertEquals("visa", response.jsonPath().getString("paymentMethod.card.brand"));

        Assertions.assertEquals(0, response.jsonPath().getInt("refunds.total"));
        Assertions.assertTrue(response.jsonPath().getList("refunds.data").isEmpty());
    }

    @Test
    public void testCreateOrder() {
        String requestBody = """
            {
                "customerId": "cus_N4qFJ3gTQd8fR2",
                "items": [
                    {
                        "productId": "prod_laptop_stand",
                        "quantity": 1
                    }
                ],
                "shipping": {
                    "address": {
                        "line1": "456 Oak Avenue",
                        "city": "Portland",
                        "state": "OR",
                        "postalCode": "97201",
                        "country": "US"
                    }
                }
            }
            """;

        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/api/v1/orders");

        Assertions.assertEquals(201, response.getStatusCode());
        Assertions.assertNotNull(response.jsonPath().getString("id"));
        Assertions.assertEquals("pending", response.jsonPath().getString("status"));
        Assertions.assertNotNull(response.jsonPath().getString("customer.email"));
        Assertions.assertTrue(response.jsonPath().getInt("totals.total") > 0);
        Assertions.assertFalse(response.jsonPath().getList("items").isEmpty());
    }

    @Test
    public void testGetOrdersList() {
        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .queryParam("limit", 50)
                .queryParam("offset", 0)
                .when()
                .get("/api/v1/orders");

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertFalse(response.jsonPath().getList("data").isEmpty());
        Assertions.assertEquals(50, response.jsonPath().getInt("pagination.limit"));
        Assertions.assertEquals(0, response.jsonPath().getInt("pagination.offset"));
        Assertions.assertTrue(response.jsonPath().getInt("pagination.total") > 0);

        Assertions.assertNotNull(response.jsonPath().getString("data[0].id"));
        Assertions.assertNotNull(response.jsonPath().getString("data[0].customer.email"));
        Assertions.assertFalse(response.jsonPath().getList("data[0].items").isEmpty());
    }

    @Test
    public void testGetProductDetails() {
        String productId = "prod_wireless_headphones";

        Response response = given()
                .when()
                .get("/api/v1/products/" + productId);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals(productId, response.jsonPath().getString("id"));
        Assertions.assertNotNull(response.jsonPath().getString("name"));
        Assertions.assertTrue(response.jsonPath().getInt("price.amount") > 0);
        Assertions.assertEquals("usd", response.jsonPath().getString("price.currency"));

        Assertions.assertTrue(response.jsonPath().getInt("inventory.quantity") >= 0);
        Assertions.assertNotNull(response.jsonPath().getString("specifications.brand"));
        Assertions.assertFalse(response.jsonPath().getList("specifications.features").isEmpty());
        Assertions.assertTrue(response.jsonPath().getFloat("ratings.average") > 0);

        Boolean isFeatured = response.jsonPath().getBoolean("metadata.isFeatured");
        Assertions.assertNotNull(isFeatured);
    }

    @Test
    public void testGetUserDetails() {
        int userId = 1001;

        Response response = given()
                .when()
                .get("/api/v1/users/" + userId);

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertEquals(userId, response.jsonPath().getInt("id"));
        Assertions.assertNotNull(response.jsonPath().getString("username"));
        Assertions.assertNotNull(response.jsonPath().getString("email"));
        
        Assertions.assertNotNull(response.jsonPath().getString("profile.firstName"));
        Assertions.assertNotNull(response.jsonPath().getString("profile.lastName"));
        
        Boolean smsNotifications = response.jsonPath().get("preferences.notifications.sms");

        Assertions.assertFalse(response.jsonPath().getList("subscription.features").isEmpty());
        Assertions.assertTrue(response.jsonPath().getInt("metadata.loginCount") > 0);
    }

    @Test
    public void testGetUsersList() {
        Response response = given()
                .header("Authorization", BEARER_TOKEN)
                .queryParam("page", 1)
                .queryParam("limit", 50)
                .queryParam("role", "user")
                .when()
                .get("/api/v1/users");

        Assertions.assertEquals(200, response.getStatusCode());
        Assertions.assertFalse(response.jsonPath().getList("data").isEmpty());
        Assertions.assertEquals(3, response.jsonPath().getList("data").size());
        
        Assertions.assertEquals(1, response.jsonPath().getInt("pagination.page"));
        Assertions.assertEquals(50, response.jsonPath().getInt("pagination.limit"));
        Assertions.assertTrue(response.jsonPath().getInt("pagination.total") > 0);
        
        String firstUserRole = response.jsonPath().getString("data[0].role");
        Assertions.assertTrue(firstUserRole.matches("(user|admin|moderator)"));
        
        String thirdUserAvatar = response.jsonPath().getString("data[2].profile.avatar");
        Assertions.assertNull(thirdUserAvatar);
    }
}