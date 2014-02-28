/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.okhttp.internal.http;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.internal.SslContextBuilder;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A white-box test for {@link ResponseCacheAdapter}. See also {@link ResponseCacheTest} for
 * black-box tests that check that {@link ResponseCache} classes are called correctly by OkHttp.
 */
public class ResponseCacheAdapterTest {

  private static final SSLContext sslContext = SslContextBuilder.localhost();
  private static final HostnameVerifier NULL_HOSTNAME_VERIFIER = new HostnameVerifier() {
    public boolean verify(String hostname, SSLSession session) {
      return true;
    }
  };

  private MockWebServer server;

  private OkHttpClient client;

  private HttpURLConnection connection;

  @Before
  public void setUp() throws Exception {
    server = new MockWebServer();
    client = new OkHttpClient();
  }

  @After
  public void tearDown() throws Exception {
    if (connection != null) {
      connection.disconnect();
    }
    server.shutdown();
  }

  @Test public void get_allParameters() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());
    assertEquals("http", serverUrl.getProtocol());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheResponse get(URI uri, String method, Map<String, List<String>> headers) throws IOException {
        assertEquals(toUri(serverUrl), uri);
        assertEquals("GET", method);
        assertTrue("Arbitrary standard header not present", headers.containsKey("User-Agent"));
        assertEquals(Collections.singletonList("value1"), headers.get("key1"));
        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);
    connection.setRequestProperty("key1", "value1");

    executeGet(connection);
  }

  @Test public void put_uriAndClass() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        assertTrue(urlConnection instanceof HttpURLConnection);
        assertFalse(urlConnection instanceof HttpsURLConnection);
        assertEquals(toUri(serverUrl), uri);
        assertEquals(serverUrl, urlConnection.getURL());
        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);
    executeGet(connection);
  }

  @Test public void put_requestHeadersPartlyUnavailable() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        // This is to be compatible with OkHttp's HttpURLConnectionImpl and the RI.
        try {
          urlConnection.getRequestProperties();
          fail();
        } catch (UnsupportedOperationException expected) {
        }

        assertEquals("value", urlConnection.getRequestProperty("key"));

        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);
    connection.setRequestProperty("key", "value");

    executeGet(connection);
  }

  @Test public void put_requestChangesForbidden() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
        // Check an arbitrary (not complete) set of methods that can be used to modify the
        // request.
        try {
          httpUrlConnection.setRequestProperty("key", "value");
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.setFixedLengthStreamingMode(1234);
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.setRequestMethod("PUT");
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.getHeaderFields().put("key", Collections.singletonList("value"));
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.getOutputStream();
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);

    executeGet(connection);
  }

  @Test public void connectionChangesForbidden() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
        try {
          httpUrlConnection.connect();
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.disconnect();
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);

    executeGet(connection);
  }

  @Test public void put_responseChangesForbidden() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
        // Check an arbitrary (not complete) set of methods that can be used to access the response
        // body.
        try {
          httpUrlConnection.getInputStream();
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.getContent();
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.setFixedLengthStreamingMode(1234);
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpUrlConnection.setRequestMethod("PUT");
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          urlConnection.getHeaderFields().put("key", Collections.singletonList("value"));
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);

    executeGet(connection);
  }

  @Test public void put_responseHeadersOk() throws Exception {
    final String expectedStatusLine = "HTTP/1.1 200 Fantastic";
    final URL serverUrl = configureServer(
        new MockResponse()
            .setStatus(expectedStatusLine)
            .addHeader("responseKey1", "responseValue1"));

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        // Status Line is treated as a special header by the Java APIs.
        assertEquals(expectedStatusLine, urlConnection.getHeaderField(0));
        assertNull(urlConnection.getHeaderFieldKey(0));
        assertEquals(expectedStatusLine, urlConnection.getHeaderField(null));
        Map.Entry<String, List<String>> firstHeader =
            urlConnection.getHeaderFields().entrySet().iterator().next();
        assertNull(firstHeader.getKey());
        assertEquals(Collections.singletonList(expectedStatusLine), firstHeader.getValue());

        // Other headers
        assertEquals("responseValue1", urlConnection.getHeaderField("responseKey1"));
        assertEquals(200, ((HttpURLConnection) urlConnection).getResponseCode());
        assertEquals(0, urlConnection.getContentLength());

        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);

    executeGet(connection);
  }

  @Test public void put_accessibleRequestInfo_GET() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        // Status Line is treated as a special header by the Java APIs.
        HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
        assertEquals("GET", httpUrlConnection.getRequestMethod());
        assertTrue(httpUrlConnection.getDoInput());
        assertFalse(httpUrlConnection.getDoOutput());

        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);

    executeGet(connection);
  }

  @Test public void put_accessibleRequestInfo_POST() throws Exception {
    final URL serverUrl = configureServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        // Status Line is treated as a special header by the Java APIs.
        HttpURLConnection httpUrlConnection = (HttpURLConnection) urlConnection;
        assertEquals("POST", httpUrlConnection.getRequestMethod());
        assertTrue(httpUrlConnection.getDoInput());
        assertTrue(httpUrlConnection.getDoOutput());
        return null;
      }
    };
    client.setResponseCache(responseCache);

    connection = client.open(serverUrl);

    executePost(connection);
  }

  @Test public void get_https_allParameters() throws Exception {
    final URL serverUrl = configureHttpsServer(new MockResponse());
    assertEquals("https", serverUrl.getProtocol());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheResponse get(URI uri, String method, Map<String, List<String>> headers) throws IOException {
        assertEquals("https", uri.getScheme());
        assertEquals(toUri(serverUrl), uri);
        assertEquals("GET", method);
        assertTrue("Arbitrary standard header not present", headers.containsKey("User-Agent"));
        assertEquals(Collections.singletonList("value1"), headers.get("key1"));
        return null;
      }
    };
    client.setResponseCache(responseCache);
    client.setSslSocketFactory(sslContext.getSocketFactory());
    client.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);

    connection = client.open(serverUrl);
    connection.setRequestProperty("key1", "value1");

    executeGet(connection);
  }

  @Test public void put_https_uriAndClass() throws Exception {
    final URL serverUrl = configureHttpsServer(new MockResponse());
    assertEquals("https", serverUrl.getProtocol());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        assertTrue(urlConnection instanceof HttpsURLConnection);
        assertEquals(toUri(serverUrl), uri);
        assertEquals(serverUrl, urlConnection.getURL());
        return null;
      }
    };
    client.setResponseCache(responseCache);
    client.setSslSocketFactory(sslContext.getSocketFactory());
    client.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);

    connection = client.open(serverUrl);
    executeGet(connection);
  }

  @Test public void put_https_extraHttpsMethods() throws Exception {
    final URL serverUrl = configureHttpsServer(new MockResponse());
    assertEquals("https", serverUrl.getProtocol());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        HttpsURLConnection cacheHttpsUrlConnection = (HttpsURLConnection) urlConnection;
        HttpsURLConnection realHttpsUrlConnection = (HttpsURLConnection) connection;
        assertEquals(realHttpsUrlConnection.getCipherSuite(),
            cacheHttpsUrlConnection.getCipherSuite());
        assertEquals(realHttpsUrlConnection.getPeerPrincipal(),
            cacheHttpsUrlConnection.getPeerPrincipal());
        assertArrayEquals(realHttpsUrlConnection.getLocalCertificates(),
            cacheHttpsUrlConnection.getLocalCertificates());
        assertArrayEquals(realHttpsUrlConnection.getServerCertificates(),
            cacheHttpsUrlConnection.getServerCertificates());
        assertEquals(realHttpsUrlConnection.getLocalPrincipal(),
            cacheHttpsUrlConnection.getLocalPrincipal());
        return null;
      }
    };
    client.setResponseCache(responseCache);
    client.setSslSocketFactory(sslContext.getSocketFactory());
    client.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);

    connection = client.open(serverUrl);
    executeGet(connection);

    RecordedRequest recordedRequest = server.takeRequest();
    recordedRequest.getSslProtocol();
  }

  @Test public void put_https_forbiddenFields() throws Exception {
    final URL serverUrl = configureHttpsServer(new MockResponse());

    ResponseCache responseCache = new NoOpResponseCache() {
      @Override
      public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        HttpsURLConnection httpsUrlConnection = (HttpsURLConnection) urlConnection;
        try {
          httpsUrlConnection.getHostnameVerifier();
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        try {
          httpsUrlConnection.getSSLSocketFactory();
          fail();
        } catch (UnsupportedOperationException expected) {
        }
        return null;
      }
    };
    client.setResponseCache(responseCache);
    client.setSslSocketFactory(sslContext.getSocketFactory());
    client.setHostnameVerifier(NULL_HOSTNAME_VERIFIER);

    connection = client.open(serverUrl);
    executeGet(connection);

    RecordedRequest recordedRequest = server.takeRequest();
    recordedRequest.getSslProtocol();
  }

  private void executeGet(HttpURLConnection connection) throws IOException {
    connection.connect();
    connection.getHeaderFields();
    connection.disconnect();
  }

  private void executePost(HttpURLConnection connection) throws IOException {
    connection.setDoOutput(true);
    connection.connect();
    connection.getOutputStream().write("Hello World".getBytes());
    connection.disconnect();
  }

  private URL configureServer(MockResponse mockResponse) throws Exception {
    server.enqueue(mockResponse);
    server.play();
    return server.getUrl("/");
  }

  private URL configureHttpsServer(MockResponse mockResponse) throws Exception {
    server.useHttps(sslContext.getSocketFactory(), false /* tunnelProxy */);
    server.enqueue(mockResponse);
    server.play();
    return server.getUrl("/");
  }

  private static class NoOpResponseCache extends ResponseCache {

    @Override
    public CacheResponse get(URI uri, String s, Map<String, List<String>> stringListMap)
        throws IOException {
      return null;
    }

    @Override
    public CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
      return null;
    }
  }

  private static URI toUri(URL serverUrl) {
    try {
      return serverUrl.toURI();
    } catch (URISyntaxException e) {
      fail(e.getMessage());
      return null;
    }
  }
}
