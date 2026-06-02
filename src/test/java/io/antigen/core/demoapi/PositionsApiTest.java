package io.antigen.core.demoapi;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

public class PositionsApiTest extends ApiTestBase {

    @Test
    public void testListMyPositions() {
        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/positions")
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    public void testGetAccountPositions() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"Position Tester\",\n" +
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
                "  \"symbol\": \"MSFT\",\n" +
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
                .statusCode(201);

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/positions/" + accountId)
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("size()", greaterThanOrEqualTo(0));
    }
}
