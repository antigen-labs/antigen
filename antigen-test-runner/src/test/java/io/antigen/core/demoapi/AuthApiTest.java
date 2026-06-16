package io.antigen.core.demoapi;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class AuthApiTest extends ApiTestBase {

    @Test
    public void testLogin() {
        String requestBody = "{\n" +
                "  \"username\": \"test\",\n" +
                "  \"password\": \"test123\"\n" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("access_token", notNullValue())
                .body("access_token", not(emptyString()));
    }

    @Test
    public void testGetCurrentUser() {
        given()
                .header("Authorization", "Bearer " + authToken)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("username", notNullValue())
                .body("username", not(emptyString()))
                .body("email", notNullValue())
                .body("email", not(emptyString()))
                .body("is_active", equalTo(true));
    }
}
