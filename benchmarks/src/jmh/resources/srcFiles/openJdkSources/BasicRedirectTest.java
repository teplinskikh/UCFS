/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Basic test for redirect and redirect policies
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.httpclient.test.lib.common.HttpServerAdapters jdk.test.lib.net.SimpleSSLContext
 * @run testng/othervm
 *       -Djdk.httpclient.HttpClient.log=trace,headers,requests
 *       BasicRedirectTest
 */

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import java.net.http.HttpResponse.BodyHandlers;
import javax.net.ssl.SSLContext;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class BasicRedirectTest implements HttpServerAdapters {

    SSLContext sslContext;
    HttpTestServer httpTestServer;        
    HttpTestServer httpsTestServer;       
    HttpTestServer http2TestServer;       
    HttpTestServer https2TestServer;      
    String httpURI;
    String httpURIToMoreSecure; 
    String httpsURI;
    String httpsURIToLessSecure; 
    String http2URI;
    String http2URIToMoreSecure; 
    String https2URI;
    String https2URIToLessSecure; 

    static final String MESSAGE = "Is fearr Gaeilge briste, na Bearla cliste";
    static final int ITERATIONS = 3;

    @DataProvider(name = "positive")
    public Object[][] positive() {
        return new Object[][] {
                { httpURI,               Redirect.ALWAYS        },
                { httpsURI,              Redirect.ALWAYS        },
                { http2URI,              Redirect.ALWAYS        },
                { https2URI,             Redirect.ALWAYS        },
                { httpURIToMoreSecure,   Redirect.ALWAYS        },
                { http2URIToMoreSecure,  Redirect.ALWAYS        },
                { httpsURIToLessSecure,  Redirect.ALWAYS        },
                { https2URIToLessSecure, Redirect.ALWAYS        },

                { httpURI,               Redirect.NORMAL        },
                { httpsURI,              Redirect.NORMAL        },
                { http2URI,              Redirect.NORMAL        },
                { https2URI,             Redirect.NORMAL        },
                { httpURIToMoreSecure,   Redirect.NORMAL        },
                { http2URIToMoreSecure,  Redirect.NORMAL        },
        };
    }

    @Test(dataProvider = "positive")
    void test(String uriString, Redirect redirectPolicy) throws Exception {
        out.printf("%n---- starting positive (%s, %s) ----%n", uriString, redirectPolicy);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(redirectPolicy)
                .sslContext(sslContext)
                .build();

        URI uri = URI.create(uriString);
        HttpRequest request = HttpRequest.newBuilder(uri).build();
        out.println("Initial request: " + request.uri());

        for (int i=0; i< ITERATIONS; i++) {
            out.println("iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            out.println("  Got response: " + response);
            out.println("  Got body Path: " + response.body());
            out.println("  Got response.request: " + response.request());

            assertEquals(response.statusCode(), 200);
            assertEquals(response.body(), MESSAGE);
            assertTrue(response.uri().getPath().endsWith("message"));
            assertPreviousRedirectResponses(request, response);
        }
    }

    static void assertPreviousRedirectResponses(HttpRequest initialRequest,
                                                HttpResponse<?> finalResponse) {
        finalResponse.previousResponse()
                .orElseThrow(() -> new RuntimeException("no previous response"));

        HttpResponse<?> response = finalResponse;
        do {
            URI uri = response.uri();
            response = response.previousResponse().get();
            assertTrue(300 <= response.statusCode() && response.statusCode() <= 309,
                       "Expected 300 <= code <= 309, got:" + response.statusCode());
            assertEquals(response.body(), null, "Unexpected body: " + response.body());
            String locationHeader = response.headers().firstValue("Location")
                      .orElseThrow(() -> new RuntimeException("no previous Location"));
            assertTrue(uri.toString().endsWith(locationHeader),
                      "URI: " + uri + ", Location: " + locationHeader);

        } while (response.previousResponse().isPresent());

        assertEquals(initialRequest, response.request(),
                String.format("Expected initial request [%s] to equal last prev req [%s]",
                              initialRequest, response.request()));
    }


    @DataProvider(name = "negative")
    public Object[][] negative() {
        return new Object[][] {
                { httpURI,               Redirect.NEVER         },
                { httpsURI,              Redirect.NEVER         },
                { http2URI,              Redirect.NEVER         },
                { https2URI,             Redirect.NEVER         },
                { httpURIToMoreSecure,   Redirect.NEVER         },
                { http2URIToMoreSecure,  Redirect.NEVER         },
                { httpsURIToLessSecure,  Redirect.NEVER         },
                { https2URIToLessSecure, Redirect.NEVER         },

                { httpsURIToLessSecure,  Redirect.NORMAL        },
                { https2URIToLessSecure, Redirect.NORMAL        },
        };
    }

    @Test(dataProvider = "negative")
    void testNegatives(String uriString,Redirect redirectPolicy) throws Exception {
        out.printf("%n---- starting negative (%s, %s) ----%n", uriString, redirectPolicy);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(redirectPolicy)
                .sslContext(sslContext)
                .build();

        URI uri = URI.create(uriString);
        HttpRequest request = HttpRequest.newBuilder(uri).build();
        out.println("Initial request: " + request.uri());

        for (int i=0; i< ITERATIONS; i++) {
            out.println("iteration: " + i);
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            out.println("  Got response: " + response);
            out.println("  Got body Path: " + response.body());
            out.println("  Got response.request: " + response.request());

            assertEquals(response.statusCode(), 302);
            assertEquals(response.body(), "XY");
            assertTrue(response.uri().equals(uri));
            assertFalse(response.previousResponse().isPresent());
        }
    }



    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        httpTestServer = HttpTestServer.create(HTTP_1_1);
        httpTestServer.addHandler(new BasicHttpRedirectHandler(), "/http1/same/");
        httpURI = "http:
        httpsTestServer = HttpTestServer.create(HTTP_1_1, sslContext);
        httpsTestServer.addHandler(new BasicHttpRedirectHandler(),"/https1/same/");
        httpsURI = "https:

        http2TestServer = HttpTestServer.create(HTTP_2);
        http2TestServer.addHandler(new BasicHttpRedirectHandler(), "/http2/same/");
        http2URI = "http:
        https2TestServer = HttpTestServer.create(HTTP_2, sslContext);
        https2TestServer.addHandler(new BasicHttpRedirectHandler(), "/https2/same/");
        https2URI = "https:


        httpTestServer.addHandler(new ToSecureHttpRedirectHandler(httpsURI), "/http1/toSecure/");
        httpURIToMoreSecure = "http:
        http2TestServer.addHandler(new ToSecureHttpRedirectHandler(https2URI), "/http2/toSecure/");
        http2URIToMoreSecure = "http:

        httpsTestServer.addHandler(new ToLessSecureRedirectHandler(httpURI), "/https1/toLessSecure/");
        httpsURIToLessSecure = "https:
        https2TestServer.addHandler(new ToLessSecureRedirectHandler(http2URI), "/https2/toLessSecure/");
        https2URIToLessSecure = "https:

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop();
        httpsTestServer.stop();
        http2TestServer.stop();
        https2TestServer.stop();
    }

    static class BasicHttpRedirectHandler implements HttpTestHandler {
        volatile int count;

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("BasicHttpRedirectHandler for: " + t.getRequestURI());
            readAllRequestData(t);

            if (t.getRequestURI().getPath().endsWith("redirect")) {
                String url = t.getRequestURI().resolve("message").toString();
                t.getResponseHeaders().addHeader("Location", url);
                int len = count % 2 == 0 ? 2 : -1;
                t.sendResponseHeaders(302, len);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(new byte[]{'X', 'Y'});  
                }
            } else {
                try (OutputStream os = t.getResponseBody()) {
                    byte[] bytes = MESSAGE.getBytes(UTF_8);
                    t.sendResponseHeaders(200, bytes.length);
                    os.write(bytes);
                }
            }
        }
    }

    static class ToSecureHttpRedirectHandler implements HttpTestHandler {
        final String targetURL;
        ToSecureHttpRedirectHandler(String targetURL) {
            this.targetURL = targetURL;
        }
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("ToSecureHttpRedirectHandler for: " + t.getRequestURI());
            readAllRequestData(t);

            if (t.getRequestURI().getPath().endsWith("redirect")) {
                t.getResponseHeaders().addHeader("Location", targetURL);
                System.out.println("ToSecureHttpRedirectHandler redirecting to: " + targetURL);
                t.sendResponseHeaders(302, 2); 
                try (OutputStream os = t.getResponseBody()) {
                    os.write(new byte[]{'X', 'Y'});
                }
            } else {
                Throwable ex = new RuntimeException("Unexpected request");
                ex.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
        }
    }

    static class ToLessSecureRedirectHandler implements HttpTestHandler {
        final String targetURL;
        ToLessSecureRedirectHandler(String targetURL) {
            this.targetURL = targetURL;
        }
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.out.println("ToLessSecureRedirectHandler for: " + t.getRequestURI());
            readAllRequestData(t);

            if (t.getRequestURI().getPath().endsWith("redirect")) {
                t.getResponseHeaders().addHeader("Location", targetURL);
                System.out.println("ToLessSecureRedirectHandler redirecting to: " + targetURL);
                t.sendResponseHeaders(302, -1);  
                try (OutputStream os = t.getResponseBody()) {
                    os.write(new byte[]{'X', 'Y'});
                }
            } else {
                Throwable ex = new RuntimeException("Unexpected request");
                ex.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
        }
    }

    static void readAllRequestData(HttpTestExchange t) throws IOException {
        try (InputStream is = t.getRequestBody()) {
            is.readAllBytes();
        }
    }
}