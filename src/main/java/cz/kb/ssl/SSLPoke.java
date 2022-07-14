package cz.kb.ssl;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
public class SSLPoke {

    public static final String HOST = "css-ldap-wmt-dev1.sos.kb.cz";
    public static final int PORT = 636;

    public static void main(String[] args) {
        try {
            SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket(HOST, PORT);

            InputStream in = sslsocket.getInputStream();
            OutputStream out = sslsocket.getOutputStream();

            // Write a test byte to get a reaction :)
            out.write(1);

            while (in.available() > 0) {
                LOG.debug(in.read() + "");
            }
            LOG.debug("Successfully connected");

        } catch (Exception exception) {
            LOG.error("Connection failed!", exception);
        }
    }
}
