package io.antigen.core.http;

import lombok.Data;

/**
 * One captured baseline exchange: the request that was issued and the response it produced.
 *
 * <p>Engine-side capture contract (pairs the pure {@link Request}/{@link Response} interfaces).
 * The pure decision half ({@code io.antigen.core.plan.FaultPlanner}) consumes a list of these;
 * it must not depend on the runtime {@code TestContext} that produces them. See
 * {@code refactor/phase2.md}.
 */
@Data
public class RequestResponsePair {
    private Request request;
    private Response response;

    public RequestResponsePair(Request request, Response response) {
        this.request = request;
        this.response = response;
    }
}
