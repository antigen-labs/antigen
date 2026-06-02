package io.antigen.core.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;

public class HTTPFactory {

    public static Request createRequestFrom(Object request){
        if(request instanceof org.apache.http.client.methods.HttpRequestBase){
            return new ApacheHTTPRequest((HttpRequestBase) request);
        }
        return null;
    }

    public static Response createResponseFrom(Object request){
        if(request instanceof org.apache.http.HttpResponse){
            try {
                return new ApacheHTTPResponse((HttpResponse) request);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
