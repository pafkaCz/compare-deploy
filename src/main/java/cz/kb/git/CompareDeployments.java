package cz.kb.git;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cz.kb.git.Environment.*;
import static java.util.Arrays.asList;
import static org.apache.http.util.TextUtils.isBlank;


@Slf4j
@Component
public class CompareDeployments {

    @Autowired
    private K8sClient k8sClient;

    @Autowired
    private CompareConfiguration configuration;

    public static final String VERSION_CATALOGUES_DIR = "version-catalogues";
    public static final String SERVICES_DIR = "services";
    public static final String GIT_SESSION_ID = "BITBUCKETSESSIONID";
    public static final String SNAPSHOT_VERSION = "-SNAPSHOT";
    public static final String REPO_BRANCH = "develop";
    public static final String CATALOGUE_VERSION_BRANCH = "1.12";
    private static final boolean SKIP_GIT_CMDS = true;
    private static HttpClientContext httpClientContext = null;

    public void compare() {
        try {
            LOG.debug("Compare deployments started @" + ZonedDateTime.now());
            setSystemSslProperties();
            List<Environment> environmentsToCompare = asList(FAT, UAT, PERF);

            final List<String> bsscGitRepos = parseRepositoryNames(readRepositoriesFromGit());
            Map<String, String> latestTaggedCatalogues = getLibrariesFromLatestTagVersionCatalogue(CATALOGUE_VERSION_BRANCH, bsscGitRepos);
            Map<String, String> latestCatalogues = getLibrariesFromVersionCatalogue(CATALOGUE_VERSION_BRANCH, bsscGitRepos);
            Map<String, String> repo = getVersionsFromGitRepo(REPO_BRANCH, bsscGitRepos);
            List<EnvironmentDeployment> environmentsDeployments = new ArrayList<>();
            for (Environment environment : environmentsToCompare) {
                EnvironmentDeployment environmentsDeployment = new EnvironmentDeployment(environment);
                environmentsDeployment.setDeployments(getLibrariesFromK8Deployment(readDeploymentsFromK8s(environment)));
                environmentsDeployments.add(environmentsDeployment);
            }
//             Map<String, String> fat = getLibrariesFromK8Deployment(readFromFile("c:\\Users\\e_pzeman\\Projects\\Sandbox\\compare-deployments\\FAT_deployments.json"));
//            Map<String, String> fat = getLibrariesFromK8Deployment(readDeploymentsFromK8s("vncub2296.os.kb.cz", 30000, "bssc-fat-01-app"));
            // Map<String, String> sit = getLibrariesFromK8Deployment(readFromFile("C:\\Users\\e_pzeman\\KB\\Projects\\BSSC\\int-simple-case\\sit.json"));
//            Map<String, String> sit = new HashMap<>();
//            Map<String, String> sit = getLibrariesFromK8Deployment(readDeploymentsFromK8s("vncub2242.os.kb.cz", 30000, "bssc-sit-01-app"));
//            Map<String, String> uat = getLibrariesFromK8Deployment(readDeploymentsFromK8s("vncub2296.os.kb.cz", 30000, "bssc-uat-01-app"));
//            Map<String, String> uat = getLibrariesFromK8Deployment(readFromFile("c:\\Users\\e_pzeman\\Projects\\Sandbox\\compare-deployments\\UAT_deployments.json"));
//            Map<String, String> perf = getLibrariesFromK8Deployment(readDeploymentsFromK8s("10.96.45.41", 30000, "bssc-perf-01-app"));
//            Map<String, String> perf = getLibrariesFromK8Deployment(readDeploymentsFromK8s("vncub2613.os.kb.cz", 30000, "bssc-perf-01-app"));
//            Map<String, String> prod = getLibrariesFromK8Deployment(readDeploymentsFromK8s("vccub2820.os.kb.cz", 30000, "bssc-prod-01-app"));
//            Map<String, String> prod = getLibrariesFromK8Deployment(readFromFile("c:\\Users\\e_pzeman\\Projects\\Sandbox\\compare-deployments\\PROD_deployments.json"));
            List<String> allLibs = Stream.of(
                                    latestCatalogues.keySet().stream().collect(Collectors.toSet()),
                                    latestTaggedCatalogues.keySet().stream().collect(Collectors.toSet()),
                                    repo.keySet().stream().collect(Collectors.toSet()),
                                    environmentsDeployments.stream().flatMap(e -> e.getDeployments().stream()).map(Deployment::getServiceName).collect(Collectors.toList())
//                                    fat.keySet().stream().collect(Collectors.toSet()),
//                                    sit.keySet().stream().collect(Collectors.toSet()),
//                                    uat.keySet().stream().collect(Collectors.toSet()),
//                                    perf.keySet().stream().collect(Collectors.toSet())
                    )
                                .flatMap(Collection::stream)
                                .collect(Collectors.toSet())
                                .stream()
                                .sorted()
                                .collect(Collectors.toList());
            final HtmlBuilder html = new HtmlBuilder();
            html.startTags("html", "head");
            html.addTag("title", environmentsToCompare.toString());
            html.addTag("style", "table, th, td {border: 1px solid black;}");
            html.endTag();
            html.startTags("body", "table", "tr");
            html.addTag("th", "#");
            html.addTag("th", "Service Name");
            for (EnvironmentDeployment environmentDeployment : environmentsDeployments) {
                html.addTag("th", environmentDeployment.getEnvironment().name());
            }
            html.addTag("th", "Catalogue " + CATALOGUE_VERSION_BRANCH);
            html.addTag("th", "Catalogue Release" + CATALOGUE_VERSION_BRANCH);
            html.addTag("th", "Repo " + REPO_BRANCH + "branch");
            html.endTag();
            int i = 1;
            for (String serviceName : allLibs) {
                html.startTag("tr");
                html.addTag("td",   "" + i++ );
                html.addTag("td",serviceName);
                String refValue = null;
                for (final EnvironmentDeployment environmentDeployment : environmentsDeployments) {
                    refValue = Optional.ofNullable(refValue).orElse(environmentDeployment.getDeployedServiceVersion(serviceName));
                    html.addTag("td", html.formatValue(environmentDeployment.getDeployedServiceVersion(serviceName), refValue));
                }
                html.addTag("td", html.formatValue(latestCatalogues.get(serviceName), refValue));
                html.addTag("td", html.formatValue(latestTaggedCatalogues.get(serviceName), refValue));
                html.addTag("td", html.formatValue(repo.get(serviceName), refValue));
                html.endTag();
            }
            html.endTag();
            html.addTag("div", "Generated:" + new Date());
            html.finish();
        } catch (Exception e) {
            LOG.error("Error while generating report", e);
        }
    }



    private JSONObject readDeploymentsFromK8s(Environment environment) throws Exception {
        return requestK8sApi(environment, "/api/v1/deployment/" + environment.k8sNamespace + "?itemsPerPage=60&page=1&sortBy=d,name");
    }

    private static JSONObject requestApiWithCookie(String url, HttpClientContext context) throws IOException, JSONException {
        final HttpClientContext contextWithGitSessionCookie = createContextWithGitSessionCookie("64E3659EF3338633DC0C772493CBE889");
        LOG.debug("Request GET {}", url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse httpResponse = httpClient.execute(new HttpGet(url), context)) {
                String response = EntityUtils.toString(httpResponse.getEntity());
                LOG.trace("Response: {}", response);
                JSONObject jsonResponse = new JSONObject(response);
                return jsonResponse;
            }
        }
    }

    private static HttpClientContext createContextWithGitSessionCookie(String value) {
        BasicCookieStore cookieStore = new BasicCookieStore();
        BasicClientCookie cookie = new BasicClientCookie(GIT_SESSION_ID, value);
        cookie.setDomain("git.kb.cz");
        cookie.setAttribute(ClientCookie.DOMAIN_ATTR, "true");
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
        HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
        return context;
    }

    private JSONObject requestK8sApi(Environment environment, String apiPath) throws Exception {
        checkSslConnection(environment.k8sHost, environment.k8sApiPort);
        return k8sClient.requestK8sApi(environment, apiPath);
    }

    private static void checkSslConnection(String host, int port) throws Exception {
        LOG.debug("Checking connection {}:{}", host, port);
        HttpUriRequest httpRequest = new HttpGet("https://" + host + ":" + port);
        httpRequest.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0");
        try {
            CloseableHttpResponse httpResponse = HttpClientBuilder.create().build().execute(httpRequest);
            assert httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
        } catch (SSLHandshakeException ex) {
            if (ex.getMessage().startsWith("PKIX path building failed")) {
                InstallCert.addServerCertificateToTrusted(host, port, null);
                setSystemSslProperties();
            }
            else throw ex;
        } catch (Exception e) {
            LOG.error("Unable to check connection {}:{} with error {}", host, port, e.getMessage());
        }
    }

    private static String getCookie(String cookieName) {
        if (httpClientContext == null ) {
            httpClientContext = HttpClientContext.create();
            httpClientContext.setAttribute(HttpClientContext.COOKIE_STORE, new BasicCookieStore());
        }
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

    private static boolean isGitLoggedIn() {
        return !isBlank(getCookie(GIT_SESSION_ID));
    }


    private HttpClientContext gitLogin() {
        if (isGitLoggedIn()) {
            return httpClientContext;
        }
        String url = "https://git.kb.cz/j_atl_security_check";
        HttpPost httpLoginRequest = new HttpPost(url);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("j_username", configuration.getUsername()));
        params.add(new BasicNameValuePair("j_password", configuration.getPwd()));
        try {
            httpLoginRequest.setEntity(new UrlEncodedFormEntity(params));
            LOG.info("Request POST {}", url);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                try (CloseableHttpResponse httpResponse = httpClient.execute(httpLoginRequest, httpClientContext)) {
                    LOG.trace(httpResponse.toString());
                    LOG.info("Git session cookie {} is {}", GIT_SESSION_ID, getCookie(GIT_SESSION_ID));
                    return httpClientContext;
                }
            }
        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }



    private JSONObject readRepositoriesFromGit() throws Exception {
        String url = "https://git.kb.cz/rest/api/latest/projects/BSSC/repos?start=0&limit=100";
        checkSslConnection("git.kb.cz", 443);
        return requestApiWithCookie(url, gitLogin());
    }

    private List<String> readFileLinesFromGit(String repoName, String fileName, String tagName) throws Exception {
        String url = "https://git.kb.cz/rest/api/latest/projects/BSSC/repos/" +  repoName + "/browse/" + fileName + "?at=refs%2Ftags%2F" + tagName + "&start=0&limit=20000";
        checkSslConnection("git.kb.cz", 443);
        final JSONObject file = requestApiWithCookie(url, gitLogin());
        final JSONArray linesArray = file.getJSONArray("lines");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < linesArray.length(); i++) {
            lines.add(linesArray.getJSONObject(i).get("text").toString());
        }
        return lines;
    }

    private void lunchVersionCatalogRelease() {
        // Request URL: http://vncub2026.os.kb.cz:32021/job/version-catalog-K8S_BPMREPORTINGSTORAGE/job/release/build?delay=0sec
        // FORM DATA
        // name: RELEASE_TYPE
        // value: patch
        // name: BRANCH
        // value: origin/release/1.9
        // statusCode: 303
        // redirectTo: .
        // json: {"parameter": [{"name": "RELEASE_TYPE", "value": "patch"}, {"name": "BRANCH", "value": "origin/release/1.9"}], "statusCode": "303", "redirectTo": "."}
        // Submit: Build
    }

    private static List<String> parseRepositoryNames(JSONObject gitResponse) throws JSONException {
        List<String> bsscRepos = new ArrayList<>();
        final JSONArray values = gitResponse.getJSONArray("values");
        for (int i = 0; i < values.length(); i++) {
            final String repoName = values.getJSONObject(i).getString("name");
            bsscRepos.add(repoName);
        }
        Collections.sort(bsscRepos);
        return bsscRepos;
    }

    private List<Deployment> getLibrariesFromK8Deployment(JSONObject json) throws JSONException {
        if (json == null ) {
            return new ArrayList<>();
        }
        List<String> k8Deploy = new ArrayList<>();
        final JSONArray deployments = json.getJSONArray("deployments");
        for (int i = 0; i < deployments.length(); i++) {
            final JSONObject deployment = deployments.getJSONObject(i);
            String image = deployment.getJSONArray("containerImages").getString(0).replace("nexus3.kb.cz:18443/", "");
            image = image.substring(image.indexOf("/")+1);
            k8Deploy.add(image);
        }
        Collections.sort(k8Deploy);
        // k8Deploy.stream().collect(Collectors.toMap(lib -> lib.substring(0, lib.indexOf(":")), lib -> lib.substring(lib.indexOf(":")+1)));
        return librariesToDeployment(k8Deploy);
    }

    private Map<String, String> listToMap(List<String> list) {
        Map<String, String> map = new HashMap<>();
        for (String item : list) {
            String[] split = item.split(":");
            String serviceName = split[0];
            if (!configuration.getIgnoredArtefacts().contains(serviceName)) {
                map.put(serviceName, split[1]);
            }
        }
        return map;
    }

    private List<Deployment> librariesToDeployment(List<String> list) {
        List<Deployment> deployments = new ArrayList<>();
        for (String item : list) {
            final String[] split = item.split(":");
            String serviceName = split[0];
            if (!configuration.getIgnoredArtefacts().contains(serviceName)) {
                deployments.add(new Deployment(serviceName, split[1].replace(SNAPSHOT_VERSION, "")));
            }
        }
        return deployments;
    }

    private static JSONObject readFromFile(String fileName) throws IOException, JSONException {
        String content = new String(Files.readAllBytes(Paths.get(fileName)));
        JSONObject json = new JSONObject(content);
        return json;
    }

    private Map<String, String> getLibrariesFromLatestTagVersionCatalogue(String catalogVersionBranch, List<String> bsscRepos) throws Exception {
        final Map<String, String> tagMap = new HashMap<>();
        final HttpClientContext httpClientContext = gitLogin();
        List<String> result = new ArrayList<>();
        for (String repoName : bsscRepos) {
            if(repoName.startsWith(GitClient.VERSION_CATALOG_PREFIX) && !configuration.getIgnoredArtefacts().contains(repoName)) {
                String latestTag = findLatestTag(repoName, catalogVersionBranch, httpClientContext);
                if (latestTag == null) {
                    continue;
                }
                tagMap.put(repoName, latestTag);
                final List<String> fileLines = readFileLinesFromGit(repoName, "deployment.descriptor", latestTag);
                fileLines.stream().map(line -> line.substring(line.indexOf(":")+1).replace(" ", "")).collect(Collectors.toCollection(() -> result));
            }
        }
        Collections.sort(result);
        final Map<String, String> tagVersions = listToMap(result);
        tagVersions.putAll(tagMap);
        return tagVersions;
    }

    private static String findLatestTag(final String catalogue, final String catalogVersionBranch, final  HttpClientContext context) throws IOException, JSONException {
        String url = "https://git.kb.cz/rest/api/latest/projects/BSSC/repos/" + catalogue + "/tags?start=0&limit=100&filterText=&boostMatches=true";
        LOG.info("Request GET {}", url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse httpResponse = httpClient.execute(new HttpGet(url), context)) {
                assert httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
                String response = EntityUtils.toString(httpResponse.getEntity());
                LOG.trace("Response: {}", response);
                JSONObject jsonResponse = new JSONObject(response);
                final JSONArray values = jsonResponse.getJSONArray("values");
                final List<String> tags = new ArrayList<>();
                for (int i = 0; i < values.length(); i++) {
                    tags.add(((JSONObject) values.get(i)).getString("displayId"));
                }
                int latest =  tags.stream().filter(t -> t.startsWith("v" + catalogVersionBranch)).map(e -> Integer.valueOf(e.replace("v" + catalogVersionBranch + ".", ""))).sorted().reduce((first, second) -> second).orElse(-1);
                if(latest == -1) {
                    LOG.warn("Latest version not found for {}", catalogue);
                    return null;
                }
                return "v" + catalogVersionBranch + "." + latest;
            }
        }
    }

    private Map<String, String> getLibrariesFromVersionCatalogue(String catalogVersion, List<String> bsscRepos) throws IOException {
        new GitClient().fetchAllVersionCataloguesFromGit(catalogVersion, bsscRepos);
        final List<String> catalogues = findFilesInSubDir(VERSION_CATALOGUES_DIR, "deployment.descriptor");
        List<String> result = new ArrayList<>();
        for (String catalogue : catalogues) {
            final List<String> versionCatalogueLines = Files.readAllLines(Paths.get(catalogue));
            versionCatalogueLines.stream().map(line -> line.substring(line.indexOf(":")+1).replace(" ", "")).collect(Collectors.toCollection(() -> result));
        }
        Collections.sort(result);
        return listToMap(result);
    }

    private  Map<String, String> getVersionsFromGitRepo(String branch, List<String> bsscRepos) throws IOException {
        new GitClient().fetchAllServicesFromGit(branch, bsscRepos);
        final List<String> poms = findFilesInSubDir(SERVICES_DIR, "pom.xml");
        Map<String, String> result = new TreeMap<>();
        for (String pom : poms) {
            String pomFileContent = Files.readString(Paths.get(pom));
            int startVersion = pomFileContent.indexOf("<version>");
            int endVersion = pomFileContent.indexOf("</version>");
            String serviceName = pom.substring((SERVICES_DIR + "\\").length(), pom.indexOf("\\pom.xml"));
            String version = pomFileContent.substring(startVersion + "<version>".length(), endVersion);
            if (version.endsWith(SNAPSHOT_VERSION)) {
                version = version.replace(SNAPSHOT_VERSION, "");
                int minorVersion = Integer.valueOf(version.split("\\.")[1]);
                version = version.replace(Integer.toString(minorVersion), Integer.toString(--minorVersion));
            }
            if (!configuration.getIgnoredArtefacts().contains(serviceName)) {
                result.put(serviceName, version);
            }
        }
        return result;
    }

    private static List<String> findFilesInSubDir(String root, String fileName) throws IOException {
        List<String> catalogues = new ArrayList<>();
        Files.walk(Paths.get(root), 2)
             .filter(Files::isRegularFile)
             .forEach((f)->{
                 String file = f.toString();
                 if (file.endsWith(fileName))
                     catalogues.add(file);
             });
        return catalogues;
    }

    private static void setSystemSslProperties() {
        System.setProperty("javax.net.ssl.trustStore", InstallCert.LOCAL_CA_CERT_STORE);
        System.setProperty("javax.net.ssl.trustStorePassword", InstallCert.STORAGE_PSSWD);
    }

    static class RetryCommand {

        private final int maxRetries;

        RetryCommand() {
            this(1);
        }
        RetryCommand(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        // Takes a function and executes it, if fails, passes the function to the retry command
        public <T> T runWithRetry(Supplier<T> function) {
            try {
                return function.get();
            } catch (Exception e) {
                LOG.warn("FAILED - Command failed, will be retried " + maxRetries + " times.");
                return retry(function);
            }
        }

        private <T> T retry(Supplier<T> function) throws RuntimeException {

            int retryCounter = 0;
            while (retryCounter < maxRetries) {
                try {
                    return function.get();
                } catch (Exception ex) {
                    retryCounter++;
                    LOG.warn("FAILED - Command failed on retry " + retryCounter + " of " + maxRetries, ex);
                    if (retryCounter >= maxRetries) {
                        LOG.error("Max retries exceeded.");
                        throw ex;
                    }
                }
            }
            return null;
        }
    }

    class GitClient{
        public static final String VERSION_CATALOG_PREFIX = "version-catalog-K8S_";

        public void main(String[] args) {
            try {
                final List<String> bsscRepos = parseRepositoryNames(readRepositoriesFromGit());
                // final List<String> bsscRepos = Arrays.asList("be-task");
                fetchAllServicesFromGit("develop", bsscRepos);
                // fetchAllVersionCataloguesFromGit("1.8", bsscRepos);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void fetchAllVersionCataloguesFromGit(String catalogueVersion, List<String> bsscRepos) {
            bsscRepos.stream().filter(r -> r.startsWith(VERSION_CATALOG_PREFIX))
             .forEach(catalogueName -> {
                 try {
                     fetchRepoFromGit(catalogueName, "release/" + catalogueVersion, VERSION_CATALOGUES_DIR);
                 } catch (IOException e) {
                     LOG.error("Fetching from Git failed", e);
                 }
             });
        }

        public void fetchAllServicesFromGit(String branchName, List<String> bsscRepos) {
            try {
                for (String item : bsscRepos) {
                    if (!item.startsWith(VERSION_CATALOG_PREFIX) && !configuration.getIgnoredArtefacts().contains(item)) {
                        fetchRepoFromGit(item, branchName,SERVICES_DIR);
                    }
                }
            } catch (Exception e) {
                LOG.error("Fetching from Git failed", e);
            }
        }

       public void fetchRepoFromGit(String repoName, String branch, String rootDir) throws IOException {
            Path path = Paths.get(rootDir);
            Files.createDirectories(path);
            File serviceRepoDir = new File(rootDir, repoName);
            if (!serviceRepoDir.exists() || serviceRepoDir.list().length == 0) {
                cloneBsscRepository(rootDir, repoName);
            } else {
                checkoutRepository(rootDir, repoName, branch);
            }
        }

        public void checkoutRepository(String parentDir, String repoName, String branch) {
            LOG.info("Checking out repository [{}] version [{}]", repoName, branch);
            String checkoutCommand = "git --git-dir=" + parentDir + "/" + repoName + "/.git --work-tree=" + parentDir + "/" + repoName + " checkout --force -B " + branch + " origin/" + branch;
            String pullCommand = "git --git-dir=" + parentDir + "/" + repoName + "/.git pull";
            String fetchCommand = "git --git-dir=" + parentDir + "/" + repoName + "/.git fetch";
            try {
                runGitCommand(fetchCommand);
                runGitCommand(checkoutCommand);
                runGitCommand(pullCommand);
            } catch (IOException | IllegalThreadStateException | InterruptedException e) {
                LOG.error("Git checkout {} failed: {}", repoName, e.getMessage());
            }
        }

        public void cloneBsscRepository(String parentDir, String repoName) {
            LOG.info("Cloning version catalogue [{}] repository ", repoName);
            String cloneCommand = "git clone \"ssh://git@git.kb.cz:7999/bssc/" + repoName + ".git\" " + parentDir + "/" + repoName;
            try {
                runGitCommand(cloneCommand);
            } catch (IOException | IllegalThreadStateException | InterruptedException e) {
                LOG.error("Git clone {} failed", repoName, e);
            }
        }

        private void runGitCommand(String command) throws InterruptedException, IOException {
            if (SKIP_GIT_CMDS) {
                return;
            }
            LOG.debug("Executing command: {}", command);
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(command);
            waitForProcess(process);
            int size;
            byte[] buffer = new byte[1024];
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                if (exitCode != 0 ) {
                    while ((size = process.getErrorStream().read(buffer)) != -1) System.err.write(buffer, 0, size);
                    throw  new IOException("Git command finished with exit code " + exitCode);
                }
            }
            if (process.getInputStream().available() > 0) {
                while ((size = process.getInputStream().read(buffer)) != -1) {
                    System.out.write(buffer, 0, size);
                }
            }
        }

        private void waitForProcess(Process process) throws InterruptedException {
            // process.waitFor(); // does not work
            Thread.sleep(1000); // just wait for a while to finish
        }
    }
}
