package cz.kb.git;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

import static cz.kb.git.CompareDeployments.USERNAME;

@Slf4j
@Component
public class TokenGenerator {

    public static final String MWS_GENERATE_TOKEN_URL = "https://mws.kb.cz/MWSPortal/kubeNaming/generateToken";
    public static final String MWS_LOGIN_URL = "https://mws.kb.cz/MWSPortal/login/authenticate";
    public static final String MWS_LOGIN_COOKIE_NAME = "JSESSIONID";

    @Autowired
    private HttpClient httpClient;

    @Value("${cz.kb.pwd}")
    private String pwd;

    public String generateApiToken(Environment environment) throws IOException {
        if (!isMwsLoggedIn()) {
            mwsPortalLogin();
        }
        final String response = httpClient.postRequest(MWS_GENERATE_TOKEN_URL, null, Map.of("tokenUsername", USERNAME, "tokenPassword", pwd, "clusterNamingId", environment.clusterNamingId));
        return response;
    }

    private void mwsPortalLogin() throws IOException {
       httpClient.postRequest(MWS_LOGIN_URL, null, Map.of("username", USERNAME, "password", pwd));
       LOG.info("MWS session cookie {} is {}", MWS_LOGIN_COOKIE_NAME, httpClient.getCookie(MWS_LOGIN_COOKIE_NAME));
    }

    private boolean isMwsLoggedIn() {
        return !StringUtils.isEmpty(httpClient.getCookie(MWS_LOGIN_COOKIE_NAME));
    }
}
