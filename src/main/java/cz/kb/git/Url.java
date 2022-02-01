package cz.kb.git;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Url {
    private boolean ssl;
    private String host;
    private int port;
    private String path;

    public int getPort() {
        if (port == 0){
            if (ssl) {
                return 443;
            } else {
                return 80;
            }
        } else {
            return port;
        }
    }

    public String getProtocol() {
        if (ssl) {
            return "https://";
        } else {
            return "http://";
        }
    }

    public String getPath() {
        if (path.startsWith("/")) {
            return path;
        } else {
            return "/" + path;
        }

    }

    public Url withPath(final String path) {
        return Url.builder().ssl(ssl).host(host).port(port).path(path).build();
    }

    public String getUrl() {
        return getProtocol() + host + ":" + getPort() + getPath();
    }

    @Override
    public String toString() {
        return getUrl();
    }
}
