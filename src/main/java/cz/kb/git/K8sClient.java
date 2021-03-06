package cz.kb.git;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
public class K8sClient {

    public static final String K8S_LOGIN_PATH = "/api/v1/login";
    public static final String K8S_CSRF_PATH = "/api/v1/csrftoken/login";
    public static final String K8S_LOGIN_COOKIE_NAME = "jweToken";
    public static final String K8S_LOGIN_CSRF_HEADER_NAME = "X-CSRF-TOKEN";
    public static final String K8S_LOGIN_CSRF_TOKEN_NAME = "token";


    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private HttpClient httpClient;

    public JSONObject requestK8sApi(Environment environment, String apiPath) throws Exception {
        String k8sLoginCookie = getK8sLoginCookie(environment);

        if (StringUtils.isEmpty(k8sLoginCookie)) {
            RetryCommand<Environment, String> retryK8sLoginCmd = new RetryCommand<>((env) -> this.k8sLogin(env));
            k8sLoginCookie = retryK8sLoginCmd.execute(environment);
        } else {
            k8sLoginCookie = URLDecoder.decode(k8sLoginCookie, StandardCharsets.UTF_8.toString());
        }

        Url k8sUrlApi = Url.builder().ssl(true).host(environment.k8sHost).port(environment.k8sApiPort).path(apiPath).build();
        try {
            return httpClient.getJsonRequest(k8sUrlApi, Map.of(K8S_LOGIN_COOKIE_NAME, k8sLoginCookie));
        } catch (AuthenticationException authenticationException) {
            httpClient.removeExpiredCookie();
            return requestK8sApi(environment, apiPath);
        } catch (Exception e) {
            LOG.error("Error requesting {} : {}", k8sUrlApi, e.getMessage());
            return null;
        }
    }

    private String k8sLogin(Environment environment) throws Exception {
        Url csrfUrl = Url.builder().ssl(true).host(environment.k8sHost).port(environment.k8sApiPort).path(K8S_CSRF_PATH).build();
        String csrfToken = httpClient.getJsonRequest(csrfUrl, null).getString(K8S_LOGIN_CSRF_TOKEN_NAME);

        Url loginUrl = Url.builder().ssl(true).host(environment.k8sHost).port(environment.k8sApiPort).path(K8S_LOGIN_PATH).build();
        String body = "{\"" + K8S_LOGIN_CSRF_TOKEN_NAME + "\":\"" + tokenGenerator.generateApiToken(environment) + "\"}";

        String jweToken = httpClient.postJsonRequest(loginUrl, Map.of(K8S_LOGIN_CSRF_HEADER_NAME, csrfToken), body).getString(K8S_LOGIN_COOKIE_NAME);
        httpClient.setCookie(K8S_LOGIN_COOKIE_NAME, URLEncoder.encode(jweToken, StandardCharsets.UTF_8.toString()), environment.k8sHost);
        LOG.trace("K8s session cookie {} is {}", K8S_LOGIN_COOKIE_NAME, httpClient.getCookie(K8S_LOGIN_COOKIE_NAME, environment.k8sHost));
        return jweToken;
    }

    private String getK8sLoginCookie(Environment environment) {
        return httpClient.getCookie(K8S_LOGIN_COOKIE_NAME, environment.k8sHost);
    }


}
