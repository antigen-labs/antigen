package io.antigen.core.demoapi;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class AccountsApiTest extends ApiTestBase {

    @Test
    public void testCreateAccount() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"John Doe\",\n" +
                "  \"initial_cash\": 10000.00\n" +
                "}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(createAccountBody)
                .when()
                .post("/accounts")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("account_number", notNullValue())
                .body("holder_name", equalTo("John Doe"))
                .body("holder_name", notNullValue())
                .body("cash_balance", equalTo("10000.00"))
                .body("cash_balance", notNullValue())
                .body("total_value", equalTo("10000.00"))
                .body("total_value", notNullValue())
                .body("status", equalTo("ACTIVE"))
                .body("created_at", notNullValue())
                .body("updated_at", notNullValue());
    }

    @Test
    public void testListMyAccounts() {
        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/accounts")
                .then()
                .statusCode(200)
                .body("$", notNullValue())
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    public void testGetAccount() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"Test Account\",\n" +
                "  \"initial_cash\": 5000.00\n" +
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

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/accounts/" + accountId)
                .then()
                .statusCode(200)
                .body("id", equalTo(accountId))
                .body("id", notNullValue())
                .body("account_number", notNullValue())
                .body("holder_name", equalTo("Test Account"))
                .body("holder_name", notNullValue())
                .body("cash_balance", notNullValue())
                .body("total_value", notNullValue())
                .body("status", equalTo("ACTIVE"))
                .body("created_at", notNullValue())
                .body("updated_at", notNullValue());
    }

    @Test
    public void testDepositCash() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"Deposit Test\",\n" +
                "  \"initial_cash\": 1000.00\n" +
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

        String depositBody = "{\n" +
                "  \"amount\": 5000.00\n" +
                "}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(depositBody)
                .when()
                .post("/accounts/" + accountId + "/deposit")
                .then()
                .statusCode(200)
                .body("id", equalTo(accountId))
                .body("id", notNullValue())
                .body("account_number", notNullValue())
                .body("holder_name", equalTo("Deposit Test"))
                .body("holder_name", notNullValue())
                .body("cash_balance", equalTo("6000.00"))
                .body("cash_balance", notNullValue())
                .body("total_value", equalTo("6000.00"))
                .body("total_value", notNullValue())
                .body("status", equalTo("ACTIVE"))
                .body("status", notNullValue())
                .body("created_at", notNullValue())
                .body("updated_at", notNullValue());
    }

    @Test
    public void testWithdrawCash() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"Withdraw Test\",\n" +
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

        String withdrawBody = "{\n" +
                "  \"amount\": 1000.00\n" +
                "}";

        given()
                .header("Authorization", "Bearer " + authToken)
                .contentType(ContentType.JSON)
                .body(withdrawBody)
                .when()
                .post("/accounts/" + accountId + "/withdraw")
                .then()
                .statusCode(200)
                .body("id", equalTo(accountId))
                .body("id", notNullValue())
                .body("account_number", notNullValue())
                .body("holder_name", equalTo("Withdraw Test"))
                .body("holder_name", notNullValue())
                .body("cash_balance", equalTo("9000.00"))
                .body("cash_balance", notNullValue())
                .body("total_value", equalTo("9000.00"))
                .body("total_value", notNullValue())
                .body("status", equalTo("ACTIVE"))
                .body("status", notNullValue())
                .body("created_at", notNullValue())
                .body("updated_at", notNullValue());
    }

    @Test
    @Disabled
    public void testGetAccountPerformance() {
        String createAccountBody = "{\n" +
                "  \"holder_name\": \"Performance Test\",\n" +
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

        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/accounts/" + accountId + "/performance")
                .then()
                .statusCode(200)
                .body("account_id", notNullValue())
                .body("total_value", notNullValue());
    }
}
