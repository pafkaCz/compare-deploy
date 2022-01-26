package cz.kb.git;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.http.util.TextUtils.isBlank;

@Slf4j
@Component
public class HttpClient {

    private static final String COOKIE_STORE_FILE = "cookieStore.ser";
    private static HttpClientContext httpClientContext = null;

    @PostConstruct
    public void initCookieStore() {
        if (httpClientContext == null ) {
            httpClientContext = HttpClientContext.create();
            httpClientContext.setAttribute(HttpClientContext.COOKIE_STORE, new BasicCookieStore());
            LOG.info("New http client context has been initialized.");
        }
        String systemTrustStore = System.getProperty("javax.net.ssl.trustStore");
        LOG.info("Default trust store {}", systemTrustStore == null ? "JDK's" : systemTrustStore);
        try {
            FileInputStream fileInputStream = new FileInputStream(COOKIE_STORE_FILE);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            CookieStore cookieStore = (CookieStore) objectInputStream.readObject();
            objectInputStream.close();
            httpClientContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
            LOG.info("Cookie store has been restored from {}", COOKIE_STORE_FILE);
        } catch (IOException | ClassNotFoundException ioException) {
            LOG.error("Restoring cookie store from failed.", ioException);
        }
    }

    @PreDestroy
    public void saveCookieStore() {
        if (httpClientContext == null || httpClientContext.getCookieStore() == null) {
            LOG.info("No cookie store to save.");
            return;
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(COOKIE_STORE_FILE);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(httpClientContext.getCookieStore());
            objectOutputStream.flush();
            objectOutputStream.close();
            LOG.info("Cookie store has been saved in {}", COOKIE_STORE_FILE);
        } catch (IOException ioException) {
            LOG.error("Saving cookie store failed.", ioException);
        }
    }

    public  String getCookie(String cookieName) {
        return getCookie(cookieName, null);
    }

    public  String getCookie(String cookieName, String domain) {
        CookieStore cookieStore = httpClientContext.getCookieStore();
        String cookieValue = cookieStore.getCookies()
                .stream()
                .peek(c -> LOG.debug("cookie name:{}", c.getName()))
                .filter(c -> cookieName.equals(c.getName()) &&
                            (isBlank(domain) || domain.equals(c.getDomain())))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
        return cookieValue;
    }


    public void setCookie(String name, String value, String domain) {
        CookieStore cookieStore = httpClientContext.getCookieStore();
        final BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
    }


    public JSONObject getJsonRequest(String url, Map<String, String> header) throws IOException, JSONException, AuthenticationException {
        Map<String, String> headers = new HashMap<>();
        if (header != null) {
            headers.putAll(header);
        }
//        headers.put("Accept", "application/json");
        JSONObject jsonResponse = new JSONObject(getRequest(url, headers));
        return jsonResponse;
    }

    public String getRequest(String url, Map<String, String> header) throws IOException, AuthenticationException {
        LOG.debug("Request GET {}", url);
        final HttpGet getRequest = new HttpGet(url);
        setHeaders(getRequest, header);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse httpResponse = httpClient.execute(getRequest, httpClientContext)) {
                checkStatusCode(httpResponse);
                String response = EntityUtils.toString(httpResponse.getEntity());
                LOG.trace("Response: {}", response);
                return response;
            }
        }
    }

    public String postRequest(String url, Map<String, String> header, Map<String, String> postParameters) throws IOException, AuthenticationException {
        LOG.info("Request POST {}", url);
        HttpPost postRequest = new HttpPost(url);
        if (postParameters != null) {
            List<NameValuePair> params = new ArrayList<>();
            for (final Map.Entry<String, String> entry : postParameters.entrySet()) {
                params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            postRequest.setEntity(new UrlEncodedFormEntity(params));
        }
        return post(postRequest, header);
    }

    public JSONObject postJsonRequest(String url, Map<String, String> header, String postBody) throws IOException, JSONException, AuthenticationException {
        JSONObject jsonResponse = new JSONObject(postRequest(url, header, postBody));
        return jsonResponse;
    }

    public String postRequest(String url, Map<String, String> header, String postBody) throws IOException, AuthenticationException {
        LOG.info("Request POST {}", url);
        HttpPost postRequest = new HttpPost(url);
        if (postBody != null) {
            postRequest.setEntity(new StringEntity(postBody));
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json"); // otherwise, 415. Unsupported Media Type
        headers.putAll(header);
        return post(postRequest, headers);
    }

    private String post(HttpPost postRequest, Map<String, String> header) throws IOException, AuthenticationException {
        setHeaders(postRequest, header);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse httpResponse = httpClient.execute(postRequest, httpClientContext)) {
                LOG.trace(httpResponse.toString());
                checkStatusCode(httpResponse);
                return EntityUtils.toString(httpResponse.getEntity());
            }
        }
    }

    private void checkStatusCode(final CloseableHttpResponse httpResponse) throws AuthenticationException {
        String errMsg = "Status code: " + httpResponse.getStatusLine().getStatusCode() + ". " + httpResponse.getStatusLine().getReasonPhrase();
        switch (httpResponse.getStatusLine().getStatusCode()) {
            case HttpStatus.SC_OK :
                return;
            case HttpStatus.SC_MOVED_TEMPORARILY:
                LOG.debug(errMsg + httpResponse.getFirstHeader("Location"));
                if (httpResponse.getFirstHeader("Location").getValue().contains("login")) {
                    throw new AuthenticationException(errMsg);
                }
                return;
            case HttpStatus.SC_UNAUTHORIZED:
                LOG.warn(errMsg);
                throw new AuthenticationException(errMsg);
        }
        throw new RuntimeException(errMsg);
    }

    private void setHeaders(final HttpRequestBase httpRequest, final Map<String, String> header) {
        if (header != null) {
            List<Header> headers = new ArrayList<>();
            for (final Map.Entry<String, String> entry : header.entrySet()) {
                headers.add(new BasicHeader(entry.getKey(), entry.getValue()));
            }
            httpRequest.setHeaders(headers.toArray(new Header[headers.size()]));
        }
    }
}
