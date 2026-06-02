package io.antigen.core.demoapi;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

public class ApiTestBase {

    protected static String authToken;

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "http://localhost:8000/api/v1";
        authToken = AuthUtils.getAuthToken();
    }
}
