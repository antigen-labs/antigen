package io.antigen.core.http;

import java.util.Map;

public interface Request {

    String getUrl();
    String getMethod();
    Map<String, Object> getHeaders();
    String getBody();


}
