package io.antigen.core.interceptor;

import io.antigen.core.config.ConfigResolver;
import io.antigen.core.config.ResolvedTestConfig;
import io.antigen.core.config.SimulatorConfig;
import io.antigen.core.config.TestScopedConfig;
import io.antigen.core.config.TestScopedConfigCache;
import io.antigen.core.http.apache.HTTPFactory;
import io.antigen.core.http.Request;
import io.antigen.core.http.RequestResponsePair;
import io.antigen.core.http.Response;
import io.antigen.core.runner.Runner;

import java.util.List;
import java.util.Optional;
import org.apache.http.HttpVersion;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

@Aspect
public class AspectExecutor {

    @Around("execution(@org.junit.jupiter.api.Test * *(..))")
    public Object interceptTestMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Class<?> testClass = joinPoint.getSignature().getDeclaringType();

        // Resolve per-test config (lazy-loaded, cached per class)
        Optional<TestScopedConfig> classConfig = TestScopedConfigCache.getInstance().get(testClass);
        ResolvedTestConfig resolvedConfig = ConfigResolver.resolve(testClass, classConfig, methodName);

        // Short-circuit: test is excluded from simulation via .antigen.yml
        if (resolvedConfig.isSkip()) {
            System.out.println("[Antigen] Simulation excluded via .antigen.yml for: " + methodName);
            return joinPoint.proceed();
        }

        TestContext context = new TestContext();
        context.setTestName(methodName);
        context.setResolvedTestConfig(resolvedConfig);
        TestContextManager.setContext(context);
        Object originalTestResult;

        try {
            System.out.println("Intercepting test method: " + methodName);
            System.out.println("Executing original test run to capture baseline...");

            originalTestResult = joinPoint.proceed();

            if (context.getOriginalResponse() == null) {
                System.out.println("No interceptable HTTP response was captured. Skipping fault simulation for this test.");
                return originalTestResult;
            }
            System.out.println("Original test run completed. Baseline response captured.");

            String endpointUrl = context.getOriginalRequest() != null ? context.getOriginalRequest().getUrl() : "";

            if (!SimulatorConfig.isTestExcluded(methodName)
                    && !SimulatorConfig.isEndpointExcluded(endpointUrl)
                    && !resolvedConfig.isEndpointExcluded(endpointUrl)) {
                Runner.executeTestWithSimulatedFaults(joinPoint, context);
            } else {
                System.out.println("Skipping fault simulation for this test due to exclusion rules.");
            }

        } finally {
            // Clean up the context for the current thread to prevent memory leaks
            // and state bleeding between tests in the same thread.
            TestContextManager.clearContext();
            System.out.println("Test method execution finished: " + methodName);
        }

        return originalTestResult;
    }

    @Around("execution(* org.apache.http.impl.client.CloseableHttpClient.execute(..))")
    public Object interceptApacheHttpClient(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!TestContextManager.hasContext()) {
            return joinPoint.proceed(joinPoint.getArgs());
        }

        TestContext context = TestContextManager.getContext();
        Object[] args = joinPoint.getArgs();

        HttpRequestBase httpRequest = null;
        if (args.length > 0 && args[0] instanceof HttpRequestBase) {
            httpRequest = (HttpRequestBase) args[0];
        }

        // ── Baseline run ──────────────────────────────────────────────────────
        // Make the real HTTP call, capture every request/response pair for replay.
        if (context.getCurrentSimulationIndex() == -1) {
            Object result = joinPoint.proceed(args);

            if (result instanceof HttpResponse httpResponse) {
                Response responseWrapper = HTTPFactory.createResponseFrom(httpResponse);
                httpResponse.setEntity(new StringEntity(responseWrapper.getBody()));

                if (context.getOriginalResponse() == null) {
                    context.setOriginalRequest(httpRequest != null ? HTTPFactory.createRequestFrom(httpRequest) : null);
                    context.setOriginalResponse(responseWrapper);
                }

                if (httpRequest != null) {
                    Request requestWrapper = HTTPFactory.createRequestFrom(httpRequest);
                    context.addCapturedRequest(requestWrapper, responseWrapper);
                    System.out.printf("Captured request #%d: %s%n",
                            context.getCapturedRequests().size() - 1, requestWrapper.getUrl());
                    Logger.parseResponse(httpRequest, context.getTestName(), responseWrapper);
                }
            }

            return result;
        }

        // ── Simulation re-run ─────────────────────────────────────────────────
        // No real HTTP call. Serve the cached baseline response for every request,
        // swapping in the mutated body only for the target request index.
        // This prevents server-side state mutations and eliminates network round-trips.
        int requestIndex = context.getAndIncrementRequestCounter();
        List<RequestResponsePair> captured = context.getCapturedRequests();

        Response cached = requestIndex < captured.size()
                ? captured.get(requestIndex).getResponse()
                : null;

        String body;
        if (context.getSimulatedResponse() != null && requestIndex == context.getCurrentSimulationIndex()) {
            body = context.getSimulatedResponse().getBody();
            System.out.printf("    [RESPONSE-INJECTION] request #%d: %s%n", requestIndex, body);
        } else {
            body = cached != null ? cached.getBody() : "{}";
        }

        int statusCode = cached != null ? cached.getStatusCode() : 200;
        // Must return a CloseableHttpResponse — CloseableHttpClient.execute(..) is
        // declared to return that type, and callers (e.g. RestAssured) cast to it.
        // A plain BasicHttpResponse triggers a ClassCastException that surfaces as a
        // false "caught" for every simulated fault.
        CloseableBasicHttpResponse synthetic = new CloseableBasicHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, statusCode, "OK"));
        synthetic.setEntity(new StringEntity(body));
        // Carry the original Content-Type forward, otherwise RestAssured has no parser
        // and fails to read the body — another false "caught" for every fault.
        synthetic.setHeader("Content-Type", contentTypeOf(cached));
        return synthetic;
    }

    /** Resolves the Content-Type of the cached response, defaulting to JSON. */
    private static String contentTypeOf(Response cached) {
        if (cached != null && cached.getHeaders() != null) {
            for (var entry : cached.getHeaders().entrySet()) {
                if ("content-type".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                    return entry.getValue().toString();
                }
            }
        }
        return "application/json";
    }

    /** Synthetic response served during the simulation re-run (no real network call). */
    private static final class CloseableBasicHttpResponse extends BasicHttpResponse
            implements CloseableHttpResponse {
        CloseableBasicHttpResponse(StatusLine statusline) {
            super(statusline);
        }

        @Override
        public void close() {
            // No underlying connection to release — body is an in-memory StringEntity.
        }
    }


    @Around("execution(* okhttp3.Call.execute(..))")
    public Object interceptOkHttpClient(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("Intercepted OkHttpClient call (placeholder, no fault injection)");
        Object result = joinPoint.proceed();

        if (result instanceof okhttp3.Response) {
            okhttp3.Response response = (okhttp3.Response) result;
            String originalResponse = response.peekBody(Long.MAX_VALUE).string();
            System.out.println("Response peeked for OkHttpClient: " + originalResponse.substring(0, Math.min(originalResponse.length(), 150)) + "...");
        }

        return result;
    }


    @Around("execution(* java.net.HttpURLConnection.connect(..))")
    public Object interceptHttpURLConnection(ProceedingJoinPoint joinPoint) throws Throwable {
        System.out.println("Intercepted HttpURLConnection call (placeholder, no fault injection)");
        Object result = joinPoint.proceed();
        HttpURLConnection connection = (HttpURLConnection) joinPoint.getTarget();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
        } catch (Exception e) {
        }

        return result;
    }
}