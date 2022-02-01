package cz.kb.git;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.auth.AuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.Map;

@Slf4j
@Component
public class TokenGenerator {
    private static final Url MWS_PORTAL = Url.builder().ssl(true).host("mws.kb.cz").build();
    public static final Url MWS_GENERATE_TOKEN_URL = MWS_PORTAL.withPath("/MWSPortal/kubeNaming/generateToken");
    public static final Url MWS_LOGIN_URL =  MWS_PORTAL.withPath("/MWSPortal/login/authenticate");
    public static final String MWS_LOGIN_COOKIE_NAME = "JSESSIONID";

    @Autowired
    private HttpClient httpClient;

    @Autowired
    private CompareConfiguration configuration;

    public String generateApiToken(Environment environment) throws Exception {
        if (!isMwsLoggedIn()) {
            mwsPortalLogin();
        }
        try {
            String response = httpClient.postRequest(MWS_GENERATE_TOKEN_URL,
                    Map.of("tokenUsername", configuration.getUsername(),
                            "tokenPassword", configuration.getPwd(),
                            "clusterNamingId", environment.clusterNamingId));
            return response;
        } catch (AuthenticationException authenticationException) {
            mwsPortalLogin();
            return generateApiToken(environment);
        }
    }

    private void mwsPortalLogin() throws Exception {
       httpClient.postRequest(MWS_LOGIN_URL,
               Map.of("username", configuration.getUsername(),
                      "password", configuration.getPwd()));
       LOG.info("MWS session cookie {} is {}", MWS_LOGIN_COOKIE_NAME, httpClient.getCookie(MWS_LOGIN_COOKIE_NAME));
    }

    private boolean isMwsLoggedIn() {
        return !StringUtils.isEmpty(httpClient.getCookie(MWS_LOGIN_COOKIE_NAME));
    }
}
