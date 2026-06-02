package io.antigen.core.demoapi;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class StocksApiTest extends ApiTestBase {

    @Test
    public void testListAllStocks() {
        given()
                .when()
                .get("/stocks")
                .then()
                .statusCode(200)
                .body("$", not(empty()))
                .body("[0].id", notNullValue())
                .body("[0].symbol", notNullValue())
                .body("[0].current_price", notNullValue())
                .body("[0].last_updated", notNullValue());
    }

    @Test
    public void testGetStockPrice() {
        given()
                .when()
                .get("/stocks/AAPL")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("symbol", equalTo("AAPL"))
                .body("symbol", notNullValue())
                .body("current_price", notNullValue())
                .body("current_price", not(emptyString()))
                .body("last_updated", notNullValue());
    }

    @Test
    public void testUpdateStockPrice() {
        String requestBody = "{\n" +
                "  \"current_price\": 160.00\n" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .put("/stocks/AAPL")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("symbol", equalTo("AAPL"))
                .body("symbol", notNullValue())
                .body("current_price", equalTo("160.00"))
                .body("current_price", notNullValue())
                .body("last_updated", notNullValue());
    }
}
