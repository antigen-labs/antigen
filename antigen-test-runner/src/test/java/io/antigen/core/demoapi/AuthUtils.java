package io.antigen.core.demoapi;

import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;

public class AuthUtils {

    public static String getAuthToken() {
        String requestBody = "{\n" +
                "  \"username\": \"test\",\n" +
                "  \"password\": \"test123\"\n" +
                "}";

        return given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("http://localhost:8000/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
    }
}
