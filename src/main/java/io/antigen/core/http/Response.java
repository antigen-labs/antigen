package io.antigen.core.http;

import java.util.Map;

public interface Response {

    String getUrl();
    Map<String, Object> getHeaders();
    String getBody();

    /**
     * Returns the HTTP status code of the response.
     * @return The HTTP status code (e.g., 200, 404, 500)
     */
    int getStatusCode();

    void setBody(String body);

    /**
     * Creates a new Response instance with a modified body.
     * @param newBody The new body for the response.
     * @return A new, immutable Response object.
     */
    Response withBody(String newBody);

    Map<String, Object> getResponseAsMap();
}