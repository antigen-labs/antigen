package io.antigen.core.coverage;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class EndpointCall {
    @JsonProperty("test")
    private String testName;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("url")
    private String url;

    @JsonProperty("headers")
    private Map<String, String> headers;

    @JsonProperty("body")
    private Object body;

    @JsonProperty("urlParameters")
    private Map<String, String> urlParameters;

    @JsonProperty("response_status_code")
    private Integer responseStatusCode;

    @JsonProperty("response_headers")
    private Map<String, String> responseHeaders;

    @JsonProperty("response_body")
    private Object responseBody;
}
