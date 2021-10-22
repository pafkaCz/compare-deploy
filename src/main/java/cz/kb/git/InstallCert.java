package cz.kb.git;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Slf4j
class InstallCert {

    public static final String JDK_CA_CERT_STORE = "cacerts";
    public static final String JDK_JSSE_CA_CERT_STORE = "jssecacerts";
    public static final String LOCAL_CA_CERT_STORE = "cacerts.jks";
    public static final String STORAGE_PSSWD = "changeit";

    public static void main(String[] args) throws Exception {
        String host = "www.kb.cz";
        int port = 443;
        String psswd = null;
        if ((args.length == 1) || (args.length == 2)) {
            String[] c = args[0].split(":");
            host = c[0];
            port = (c.length == 1) ? 443 : Integer.parseInt(c[1]);
            psswd = (args.length == 1) ? "changeit" : args[1];
        }
        addServerCertificateToTrusted(host, port, psswd);
    }

    public static void addServerCertificateToTrusted(String host, int port, String password) throws Exception {
        char[] passphrase = password == null ? STORAGE_PSSWD.toCharArray() : password.toCharArray();
        InputStream fis = getCaCertStoreStream(false);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(fis, passphrase);
        if (fis != null) {
            fis.close();
        }

        SSLContext context = SSLContext.getInstance("TLS");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
        SavingTrustManager tm = new SavingTrustManager(defaultTrustManager);
        context.init(null, new TrustManager[]{tm}, null);

        SSLSocketFactory factory = context.getSocketFactory();

        LOG.info("Opening connection to {}:{}", host, port);
        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
        socket.setSoTimeout(5000);
        try {
            LOG.debug("Starting SSL handshake");
            socket.startHandshake();
            socket.close();
            LOG.info("No errors, certificate is already trusted");
            return;
        } catch (SSLException e) {
            LOG.error("SSL Error", e);
        }

        X509Certificate[] chain = tm.chain;
        if (chain == null) {
            LOG.warn("Could not obtain server certificate chain");
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        LOG.info("Server sent {} certificate(s):", chain.length);
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        for (int i = 0; i < chain.length; i++) {
            X509Certificate cert = chain[i];
            LOG.debug(" " + (i + 1) + " Subject " + cert.getSubjectDN());
            LOG.debug("   Issuer  " + cert.getIssuerDN());
            sha1.update(cert.getEncoded());
            LOG.debug("   sha1    " + toHexString(sha1.digest()));
            md5.update(cert.getEncoded());
            LOG.debug("   md5     " + toHexString(md5.digest()));

            String alias = host + "-" + (i + 1);
            ks.setCertificateEntry(alias, cert);

            OutputStream out = new FileOutputStream(LOCAL_CA_CERT_STORE);
            ks.store(out, passphrase);
            out.close();

            LOG.info("Certificate: [{}]", cert);
            LOG.info("New certificate added to keystore [{}] using alias [{}]", LOCAL_CA_CERT_STORE, alias);
        }
    }

    private static InputStream getCaCertStoreStream(final boolean useJdkCaCertStore) throws FileNotFoundException {
        File file = new File(LOCAL_CA_CERT_STORE);
        if (!file.isFile()) {
            if (useJdkCaCertStore) {
                char separatorChar = File.separatorChar;
                File dir = new File(System.getProperty("java.home") + separatorChar + "lib" + separatorChar + "security");
                file = new File(dir, JDK_JSSE_CA_CERT_STORE);
                if (!file.isFile()) {
                    file = new File(dir, JDK_CA_CERT_STORE);
                }
            } else {
                return null;
            }
        }
        LOG.info("Loading KeyStore {}", file.getAbsolutePath());
        return new FileInputStream(file);
    }

    private static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int b : bytes) {
            b &= 0xff;
            sb.append(HEXDIGITS[b >> 4]);
            sb.append(HEXDIGITS[b & 15]);
            sb.append(' ');
        }
        return sb.toString();
    }

    private static class SavingTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        private X509Certificate[] chain;

        SavingTrustManager(X509TrustManager tm) {
            this.tm = tm;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.chain = chain;
            tm.checkServerTrusted(chain, authType);
        }
    }
}
