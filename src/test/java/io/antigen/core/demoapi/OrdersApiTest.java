package io.antigen.core.demoapi;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class OrdersApiTest extends ApiTestBase {

    private int createAccount() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"Order Tester\",\n" +
                "  \"initial_cash\": 20000.00\n" +
                "}";

        return given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createAccountBody)
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    @Test
    public void testCreateBuyOrder() {
        int accountId = createAccount();
        String orderBody = "{\n" +
                "  \"account_id\": " + accountId + ",\n" +
                "  \"symbol\": \"AAPL\",\n" +
                "  \"order_type\": \"BUY\",\n" +
                "  \"quantity\": 10\n" +
                "}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(orderBody)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("account_id", equalTo(accountId))
                .body("account_id", notNullValue())
                .body("symbol", equalTo("AAPL"))
                .body("symbol", notNullValue())
                .body("order_type", equalTo("BUY"))
                .body("order_type", notNullValue())
                .body("quantity", equalTo(10))
                .body("quantity", greaterThan(0))
                .body("price", notNullValue())
                .body("price", not(emptyString()))
                .body("total_amount", notNullValue())
                .body("total_amount", not(emptyString()))
                .body("status", equalTo("FILLED"))
                .body("status", notNullValue())
                .body("rejection_reason", nullValue())
                .body("created_at", notNullValue())
                .body("filled_at", notNullValue());
    }

    @Test
    public void testCreateSellOrder() {
        int accountId = createAccount();

        String buyOrderBody = "{\n" +
                "  \"account_id\": " + accountId + ",\n" +
                "  \"symbol\": \"AAPL\",\n" +
                "  \"order_type\": \"BUY\",\n" +
                "  \"quantity\": 10\n" +
                "}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(buyOrderBody)
                .when()
                .post("/orders")
                .then()
                .statusCode(201);

        String sellOrderBody = "{\n" +
                "  \"account_id\": " + accountId + ",\n" +
                "  \"symbol\": \"AAPL\",\n" +
                "  \"order_type\": \"SELL\",\n" +
                "  \"quantity\": 5\n" +
                "}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(sellOrderBody)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("account_id", equalTo(accountId))
                .body("account_id", notNullValue())
                .body("symbol", equalTo("AAPL"))
                .body("symbol", notNullValue())
                .body("order_type", equalTo("SELL"))
                .body("order_type", notNullValue())
                .body("quantity", equalTo(5))
                .body("quantity", greaterThan(0))
                .body("price", notNullValue())
                .body("price", not(emptyString()))
                .body("total_amount", notNullValue())
                .body("total_amount", not(emptyString()))
                .body("status", equalTo("FILLED"))
                .body("status", notNullValue())
                .body("rejection_reason", nullValue())
                .body("created_at", notNullValue())
                .body("filled_at", notNullValue());
    }

    @Test
    public void testListMyOrders() {
        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/orders")
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    @Disabled
    public void testGetOrder() {
        int accountId = createAccount();
        String orderBody = "{\n" +
                "  \"account_id\": " + accountId + ",\n" +
                "  \"symbol\": \"AAPL\",\n" +
                "  \"order_type\": \"BUY\",\n" +
                "  \"quantity\": 10\n" +
                "}";

        Integer orderId = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(orderBody)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("id", notNullValue());
    }
}
