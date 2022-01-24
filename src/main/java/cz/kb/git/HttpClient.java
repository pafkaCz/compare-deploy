package cz.kb.git;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HttpClient {

    private static HttpClientContext httpClientContext = null;

    @PostConstruct
    public void initCookieStore() {
        if (httpClientContext == null ) {
            httpClientContext = HttpClientContext.create();
            httpClientContext.setAttribute(HttpClientContext.COOKIE_STORE, new BasicCookieStore());
        }
    }

    public  String getCookie(String cookieName) {
        CookieStore cookieStore = httpClientContext.getCookieStore();
        String cookieValue = cookieStore.getCookies()
                .stream()
                .peek(c -> LOG.debug("cookie name:{}", c.getName()))
                .filter(c -> cookieName.equals(c.getName()))
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


    public JSONObject getJsonRequest(String url, Map<String, String> header) throws IOException, JSONException {
        Map<String, String> headers = new HashMap<>();
        if (header != null) {
            headers.putAll(header);
        }
        headers.put("Accept", "application/json");
        JSONObject jsonResponse = new JSONObject(getRequest(url, headers));
        return jsonResponse;
    }

    public String getRequest(String url, Map<String, String> header) throws IOException {
        LOG.debug("Request GET {}", url);
        final HttpGet getRequest = new HttpGet(url);
        if (header != null) {
            List<Header> headers = new ArrayList<>();
            for (final Map.Entry<String, String> entry : header.entrySet()) {
                headers.add(new BasicHeader(entry.getKey(), entry.getValue()));
            }
            getRequest.setHeaders(headers.toArray(new Header[headers.size()]));
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse httpResponse = httpClient.execute(getRequest, httpClientContext)) {
                assert httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
                String response = EntityUtils.toString(httpResponse.getEntity());
                LOG.trace("Response: {}", response);
                return response;
            }
        }
    }

    public String postRequest(String url, Map<String, String> header, Map<String, String> postParameters) throws IOException {
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

    public JSONObject postJsonRequest(String url, Map<String, String> header, String postBody) throws IOException, JSONException {
        JSONObject jsonResponse = new JSONObject(postRequest(url, header, postBody));
        return jsonResponse;
    }

    public String postRequest(String url, Map<String, String> header, String postBody) throws IOException {
        LOG.info("Request POST {}", url);
        HttpPost postRequest = new HttpPost(url);
        if (postBody != null) {
            postRequest.setEntity(new StringEntity(postBody));
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.putAll(header);
        return post(postRequest, headers);
    }

    private String post(HttpPost postRequest, Map<String, String> header) throws IOException {
        if (header != null) {
            List<Header> headers = new ArrayList<>();
            for (final Map.Entry<String, String> entry : header.entrySet()) {
                headers.add(new BasicHeader(entry.getKey(), entry.getValue()));
            }
            postRequest.setHeaders(headers.toArray(new Header[headers.size()]));
        }

        String systemTrustStore = System.getProperty("javax.net.ssl.trustStore");
        LOG.info("Default trust store {}", systemTrustStore == null ? "JDK's" : systemTrustStore);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse httpResponse = httpClient.execute(postRequest, httpClientContext)) {
                LOG.trace(httpResponse.toString());
                assert httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
                return EntityUtils.toString(httpResponse.getEntity());
            }
        }
    }

}
