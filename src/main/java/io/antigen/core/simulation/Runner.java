package io.antigen.core.simulation;

import io.antigen.core.config.SimulatorConfig;
import io.antigen.core.interceptor.TestContext;
import io.antigen.core.http.Request;
import io.antigen.core.http.Response;
import io.antigen.core.invariant.InvariantSimulator;
import io.antigen.core.normalizer.EndpointPatternNormalizer;
import org.aspectj.lang.ProceedingJoinPoint;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class Runner {

    private static final FaultSimulationReport REPORT = FaultSimulationReport.getInstance();

    private Runner() {}

    public static void executeTestWithSimulatedFaults(ProceedingJoinPoint joinPoint, TestContext context) throws Throwable {
        String testName = joinPoint.getSignature().getName();

        List<TestContext.RequestResponsePair> capturedRequests = context.getCapturedRequests();
        if (capturedRequests == null || capturedRequests.isEmpty()) {
            System.err.println("[Antigen-WARN] No requests were captured. Skipping fault simulation.");
            return;
        }

        System.out.printf("%n[Antigen-Sim] === Starting simulations for test: '%s' ===%n", testName);
        System.out.printf("[Antigen-Sim] Captured %d HTTP request(s)%n", capturedRequests.size());

        List<TestContext.RequestResponsePair> requestsToSimulate = filterRequestsByStrategy(capturedRequests);
        System.out.printf("[Antigen-Sim] Simulating %d request(s) after applying strategy%n", requestsToSimulate.size());

        for (TestContext.RequestResponsePair pair : requestsToSimulate) {
            int requestIndex = capturedRequests.indexOf(pair);
            Request originalRequest = pair.getRequest();
            Response originalResponse = pair.getResponse();

            if (originalResponse == null || originalRequest == null) {
                System.err.println("[Antigen-WARN] Incomplete request/response pair. Skipping.");
                continue;
            }

            String endpointPath = URI.create(originalRequest.getUrl()).getPath();
            String endpointPattern = EndpointPatternNormalizer.normalize(endpointPath);
            String httpMethod = originalRequest.getMethod();

            System.out.printf("%n[Antigen-Sim] --- Request #%d: '%s' (pattern: '%s') ---%n",
                    requestIndex, endpointPath, endpointPattern);

            if (!SimulatorConfig.shouldSimulateResponse(
                    originalResponse.getStatusCode(),
                    originalResponse.getResponseAsMap(),
                    originalResponse.getBody())) {
                System.out.printf("[Antigen-Sim] Skipping request #%d (status: %d)%n",
                        requestIndex, originalResponse.getStatusCode());
                continue;
            }

            context.setCurrentSimulationIndex(requestIndex);

            InvariantSimulator.simulateInvariantViolations(
                    joinPoint, context, testName, endpointPattern, httpMethod,
                    originalResponse, requestIndex);
        }

        context.setCurrentSimulationIndex(-1);
        System.out.printf("[Antigen-Sim] === Completed all simulations for test: '%s' ===%n%n", testName);
    }

    private static List<TestContext.RequestResponsePair> filterRequestsByStrategy(
            List<TestContext.RequestResponsePair> capturedRequests) {

        SimulatorConfig.MultipleEndpointsStrategy strategy = SimulatorConfig.getMultipleEndpointsStrategy();
        List<TestContext.RequestResponsePair> filtered = new ArrayList<>();

        if (strategy.test_only_last_endpoint) {
            if (!capturedRequests.isEmpty()) {
                filtered.add(capturedRequests.get(capturedRequests.size() - 1));
            }
        } else {
            filtered.addAll(capturedRequests);
        }

        if (strategy.exclude_endpoints != null && !strategy.exclude_endpoints.isEmpty()) {
            List<TestContext.RequestResponsePair> afterExclusion = new ArrayList<>();
            for (TestContext.RequestResponsePair pair : filtered) {
                String endpointPattern = EndpointPatternNormalizer.normalize(
                        URI.create(pair.getRequest().getUrl()).getPath());
                boolean excluded = strategy.exclude_endpoints.stream()
                        .anyMatch(endpointPattern::matches);
                if (!excluded) afterExclusion.add(pair);
            }
            return afterExclusion;
        }

        return filtered;
    }
}
