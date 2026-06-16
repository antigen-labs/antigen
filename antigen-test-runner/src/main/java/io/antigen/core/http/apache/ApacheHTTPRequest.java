package io.antigen.core.http.apache;

import io.antigen.core.http.Request;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class ApacheHTTPRequest implements Request {
    private final String url;
    private final String method;
    private final Map<String, Object> headers;
    private final String body;


    public ApacheHTTPRequest(HttpRequestBase requestBase) {
        this.url = requestBase.getURI().toString();
        this.method = requestBase.getMethod();
        this.headers = extractHeaders(requestBase);
        this.body = extractBody(requestBase);
    }


    private Map<String, Object> extractHeaders(HttpRequestBase requestBase) {
        Header[] allHeaders = requestBase.getAllHeaders();
        if (allHeaders != null && allHeaders.length > 0) {
            return Arrays.stream(allHeaders)
                    .collect(Collectors.toMap(
                            Header::getName,
                            Header::getValue,
                            // Handle duplicate header names by using the latest value
                            (existing, replacement) -> replacement
                    ));
        }
        return Collections.emptyMap();
    }


    private String extractBody(HttpRequestBase requestBase) {
        if (!(requestBase instanceof HttpEntityEnclosingRequestBase)) {
            return null;
        }

        HttpEntity entity = ((HttpEntityEnclosingRequestBase) requestBase).getEntity();
        if (entity == null) {
            return null;
        }

        try {
            return EntityUtils.toString(entity);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public Map<String, Object> getHeaders() {
        return headers;
    }

    @Override
    public String getBody() {
        return body;
    }
}