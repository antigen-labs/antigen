package io.antigen.core.demoapi;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

public class TradesApiTest extends ApiTestBase {

    @Test
    public void testListMyTrades() {
        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/trades")
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    public void testGetAccountTrades() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"Trade Tester\",\n" +
                "  \"initial_cash\": 10000.00\n" +
                "}";

        Integer accountId = given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createAccountBody)
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .extract().path("id");

        String orderBody = "{\n" +
                "  \"account_id\": " + accountId + ",\n" +
                "  \"symbol\": \"TSLA\",\n" +
                "  \"order_type\": \"BUY\",\n" +
                "  \"quantity\": 5\n" +
                "}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(orderBody)
                .when()
                .post("/orders")
                .then()
                .statusCode(201);

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/trades/" + accountId)
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("size()", greaterThanOrEqualTo(0));
    }
}
