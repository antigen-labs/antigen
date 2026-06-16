package io.antigen.core.unit;
import io.antigen.core.http.apache.ApacheHTTPRequest;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URI;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApacheHTTPRequestTest {

    @Mock
    private HttpRequestBase mockRequestBase;

    @Mock
    private HttpEntityEnclosingRequestBase mockEntityRequest;

    @Mock
    private HttpEntity mockEntity;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void constructor_setsUrlFromHttpRequestBase() {
        // Arrange
        String expectedUrl = "http://example.com/resource";
        HttpRequestBase mockHttpRequest = mock(HttpRequestBase.class);
        when(mockHttpRequest.getURI()).thenReturn(URI.create(expectedUrl));

        // Act
        ApacheHTTPRequest apacheRequest = new ApacheHTTPRequest(mockHttpRequest);

        // Assert
        assertEquals(expectedUrl, apacheRequest.getUrl());
    }

    @Test
    void getUrl_returnsCorrectUrl() {
        // Arrange
        String expectedUrl = "https://api.test/data";
        HttpRequestBase mockHttpRequest = mock(HttpRequestBase.class);
        when(mockHttpRequest.getURI()).thenReturn(URI.create(expectedUrl));
        ApacheHTTPRequest apacheRequest = new ApacheHTTPRequest(mockHttpRequest);

        // Act
        String actualUrl = apacheRequest.getUrl();

        // Assert
        assertEquals(expectedUrl, actualUrl);
    }

    @Test
    void getHeaders_returnsNullByDefault() {
        // Arrange
        HttpRequestBase mockHttpRequest = mock(HttpRequestBase.class);
        when(mockHttpRequest.getURI()).thenReturn(URI.create("http://dummy"));
        ApacheHTTPRequest apacheRequest = new ApacheHTTPRequest(mockHttpRequest);

        // Act
        Map<String, Object> headers = apacheRequest.getHeaders();

        // Assert
        assertTrue(headers.isEmpty());
    }

    @Test
    void getBody_returnsNullByDefault() {
        // Arrange
        HttpRequestBase mockHttpRequest = mock(HttpRequestBase.class);
        when(mockHttpRequest.getURI()).thenReturn(URI.create("http://dummy"));
        ApacheHTTPRequest apacheRequest = new ApacheHTTPRequest(mockHttpRequest);
        // Act
        String body = apacheRequest.getBody();

        // Assert
        assertNull(body);
    }

    @Test
    public void testConstructorWithUrlOnly() throws Exception {
        // Arrange
        String expectedUrl = "http://example.com";
        when(mockRequestBase.getURI()).thenReturn(new URI(expectedUrl));
        when(mockRequestBase.getAllHeaders()).thenReturn(new Header[0]);

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(mockRequestBase);

        // Assert
        assertEquals(expectedUrl, request.getUrl());
        assertTrue(request.getHeaders().isEmpty());
        assertNull(request.getBody());
    }

    @Test
    public void testConstructorWithUrlAndHeaders() throws Exception {
        // Arrange
        String expectedUrl = "http://example.com";
        Header header1 = mock(Header.class);
        Header header2 = mock(Header.class);

        when(header1.getName()).thenReturn("Content-Type");
        when(header1.getValue()).thenReturn("application/json");
        when(header2.getName()).thenReturn("Authorization");
        when(header2.getValue()).thenReturn("Bearer token123");

        Header[] headers = new Header[]{header1, header2};

        when(mockRequestBase.getURI()).thenReturn(new URI(expectedUrl));
        when(mockRequestBase.getAllHeaders()).thenReturn(headers);

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(mockRequestBase);

        // Assert
        assertEquals(expectedUrl, request.getUrl());
        assertNotNull(request.getHeaders());
        assertEquals(2, request.getHeaders().size());
        assertEquals("application/json", request.getHeaders().get("Content-Type"));
        assertEquals("Bearer token123", request.getHeaders().get("Authorization"));
        assertNull(request.getBody());
    }

    @Test
    public void testConstructorWithEntityAndBody() throws Exception {
        // Arrange
        String expectedUrl = "http://example.com";
        String expectedBody = "{\"key\":\"value\"}";

        when(mockEntityRequest.getURI()).thenReturn(new URI(expectedUrl));
        when(mockEntityRequest.getAllHeaders()).thenReturn(new Header[0]);
        when(mockEntityRequest.getEntity()).thenReturn(mockEntity);

        HttpEntityUtil.mockToString(mockEntity, expectedBody);

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(mockEntityRequest);

        // Assert
        assertEquals(expectedUrl, request.getUrl());
        assertTrue(request.getHeaders().isEmpty());
        assertEquals(expectedBody, request.getBody());
    }

    @Test
    public void testConstructorWithNullEntity() throws Exception {
        // Arrange
        String expectedUrl = "http://example.com";

        when(mockEntityRequest.getURI()).thenReturn(new URI(expectedUrl));
        when(mockEntityRequest.getAllHeaders()).thenReturn(new Header[0]);
        when(mockEntityRequest.getEntity()).thenReturn(null);

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(mockEntityRequest);

        // Assert
        assertEquals(expectedUrl, request.getUrl());
        assertTrue(request.getHeaders().isEmpty());
        assertNull(request.getBody());
    }

    @Test
    public void testConstructorWithEntityThrowingIOException() throws Exception {
        // Arrange
        String expectedUrl = "http://example.com";

        when(mockEntityRequest.getURI()).thenReturn(new URI(expectedUrl));
        when(mockEntityRequest.getAllHeaders()).thenReturn(new Header[0]);
        when(mockEntityRequest.getEntity()).thenReturn(mockEntity);

        HttpEntityUtil.mockToStringWithException(mockEntity);

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(mockEntityRequest);

        // Assert
        assertEquals(expectedUrl, request.getUrl());
        assertTrue(request.getHeaders().isEmpty());
        assertNull(request.getBody());
    }

    @Test
    public void testGetters() throws Exception {
        // Arrange
        String expectedUrl = "http://example.com";
        String expectedBody = "test body";
        Header header = mock(Header.class);
        when(header.getName()).thenReturn("Content-Type");
        when(header.getValue()).thenReturn("text/plain");

        when(mockEntityRequest.getURI()).thenReturn(new URI(expectedUrl));
        when(mockEntityRequest.getAllHeaders()).thenReturn(new Header[]{header});
        when(mockEntityRequest.getEntity()).thenReturn(mockEntity);

        HttpEntityUtil.mockToString(mockEntity, expectedBody);

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(mockEntityRequest);

        // Assert
        assertEquals(expectedUrl, request.getUrl());
        assertEquals(expectedBody, request.getBody());
        assertNotNull(request.getHeaders());
        assertEquals("text/plain", request.getHeaders().get("Content-Type"));
    }

    @Test
    public void testWithRealHttpGetRequest() throws Exception {
        // Arrange
        HttpGet httpGet = new HttpGet("http://example.com");
        httpGet.addHeader("Accept", "application/json");
        httpGet.addHeader("User-Agent", "Test-Agent");

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(httpGet);

        // Assert
        assertEquals("http://example.com", request.getUrl());
        assertNotNull(request.getHeaders());
        assertEquals(2, request.getHeaders().size());
        assertEquals("application/json", request.getHeaders().get("Accept"));
        assertEquals("Test-Agent", request.getHeaders().get("User-Agent"));
        assertNull(request.getBody());
    }

    @Test
    public void testWithRealHttpPostRequest() throws Exception {
        // Arrange
        HttpPost httpPost = new HttpPost("http://example.com");
        httpPost.addHeader("Content-Type", "application/json");
        String testBody = "{\"name\":\"test\"}";
        org.apache.http.entity.StringEntity entity = new org.apache.http.entity.StringEntity(testBody);
        httpPost.setEntity(entity);

        // Act
        ApacheHTTPRequest request = new ApacheHTTPRequest(httpPost);

        // Assert
        assertEquals("http://example.com", request.getUrl());
        assertNotNull(request.getHeaders());
        assertEquals("application/json", request.getHeaders().get("Content-Type"));
        assertNotNull(request.getBody());
        assertEquals(testBody, request.getBody());
    }

    private static class HttpEntityUtil {
        static void mockToString(HttpEntity entity, String result) throws IOException {
            when(entity.getContent()).thenAnswer(invocation -> {
                return new java.io.ByteArrayInputStream(result.getBytes());
            });
        }

        static void mockToStringWithException(HttpEntity entity) throws IOException {
            when(entity.getContent()).thenThrow(new IOException("Mocked IOException"));
        }
    }
}