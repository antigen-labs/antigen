package io.antigen.core.coverage;

import io.antigen.core.config.CoverageConfig;
import io.antigen.core.http.Response;
import io.antigen.core.normalizer.EndpointPatternNormalizer;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Logger {

    public static void parseResponse(HttpRequestBase httpRequestBase, String testName, Response response) {
        CoverageConfig config = CoverageConfig.getInstance();

        // Check if coverage is enabled
        if (!config.isEnabled()) {
            return;
        }

        CollectorData collectorData = Collector.getData();
        String method = httpRequestBase.getMethod() != null ? httpRequestBase.getMethod().toUpperCase() : "UNKNOWN";

        try {
            String requestUri = httpRequestBase.getURI() != null ? httpRequestBase.getURI().toString() : "";
            URI uriObj = new URI(requestUri);
            String literalPath = uriObj.getPath();

            // Check if URL is tracked
            if (!config.isUrlTracked(requestUri)) {
                return;
            }

            String baseUri = httpRequestBase.getURI() != null ? httpRequestBase.getURI().getHost() : "";
            URI baseUriObj = new URI(baseUri);
            String basePath = baseUriObj.getPath();

            if (literalPath.startsWith(basePath)) {
                literalPath = literalPath.substring(basePath.length());
                if (literalPath.isEmpty()) {
                    literalPath = "/";
                }
            }

            // Normalize endpoint pattern
            String endpointPattern = config.shouldAggregateByPattern()
                ? EndpointPatternNormalizer.normalize(literalPath)
                : literalPath;

            // Check if endpoint is excluded
            if (config.isEndpointExcluded(endpointPattern)) {
                return;
            }

            // Synchronize initialization to prevent race conditions
            synchronized (collectorData) {
                if (collectorData.getDateCollected() == null) {
                    collectorData.setDateCollected(String.valueOf(System.currentTimeMillis()));
                    collectorData.setHost(baseUriObj.getScheme() + "://" + baseUriObj.getHost());
                }
            }

            Map<String, Map<String, EndpointMethodCoverage>> paths = collectorData.getPaths();
            Map<String, EndpointMethodCoverage> methodsMap = paths.computeIfAbsent(endpointPattern, k -> new ConcurrentHashMap<>());

            // Get or create coverage for this method
            EndpointMethodCoverage methodCoverage = methodsMap.computeIfAbsent(method, k -> new EndpointMethodCoverage());

            // Prepare endpoint call data
            Map<String, String> headers = new ConcurrentHashMap<>();
            if (httpRequestBase.getAllHeaders() != null) {
                Arrays.stream(httpRequestBase.getAllHeaders()).forEach(header -> {
                    if (header != null && header.getName() != null && header.getValue() != null) {
                        headers.put(header.getName(), header.getValue());
                    }
                });
            }

            Object body = config.shouldIncludeRequestBody() ? getRequestBody(httpRequestBase) : null;
            Map<String, String> urlParams = getQueryParams(httpRequestBase);

            // Extract response data
            Integer responseStatusCode = null;
            Map<String, String> responseHeaders = new ConcurrentHashMap<>();
            Object responseBody = null;

            if (response != null) {
                // Get status code
                responseStatusCode = response.getStatusCode();

                // Get response headers
                if (response.getHeaders() != null) {
                    response.getHeaders().forEach((key, value) -> {
                        if (key != null && value != null) {
                            responseHeaders.put(key, value.toString());
                        }
                    });
                }

                // Get response body if configured
                if (config.shouldIncludeResponseBody()) {
                    responseBody = response.getBody();
                }
            }

            // Create endpoint call
            EndpointCall endpointCall = new EndpointCall();
            endpointCall.setTestName(testName);
            endpointCall.setTimestamp(Instant.now().toString());
            endpointCall.setUrl(literalPath);  // Store literal path
            endpointCall.setHeaders(headers);
            endpointCall.setBody(body);
            endpointCall.setUrlParameters(urlParams);
            endpointCall.setResponseStatusCode(responseStatusCode);
            endpointCall.setResponseHeaders(responseHeaders);
            endpointCall.setResponseBody(responseBody);

            // Add call to coverage (aggregates automatically)
            methodCoverage.addCall(endpointCall);

        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    public static String getRequestBody(HttpRequestBase request) throws IOException {
        if (request instanceof org.apache.http.client.methods.HttpEntityEnclosingRequestBase) {
            org.apache.http.client.methods.HttpEntityEnclosingRequestBase entityRequest = (org.apache.http.client.methods.HttpEntityEnclosingRequestBase) request;
            HttpEntity entity = entityRequest.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        }
        return null;
    }

    public static Map<String, String> getQueryParams(HttpRequestBase request) throws URISyntaxException {
        Map<String, String> queryParams = new ConcurrentHashMap<>();
        URI uri = request.getURI();
        String query = uri.getQuery();

        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    queryParams.put(pair.substring(0, idx), pair.substring(idx + 1));
                } else if (idx == 0){
                    queryParams.put("", pair.substring(idx + 1));
                } else {
                    queryParams.put(pair, "");
                }
            }
        }
        return queryParams;
    }

    public static String getResponseBody(org.apache.http.HttpResponse response) {
        try {
            if (response != null && response.getEntity() != null) {
                return EntityUtils.toString(response.getEntity());
            }
        } catch (IOException e) {
            System.err.println("[Coverage] Failed to read response body: " + e.getMessage());
        }
        return null;
    }

}