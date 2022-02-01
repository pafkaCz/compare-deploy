package cz.kb.git;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.http.util.TextUtils.isBlank;

@Slf4j
@AllArgsConstructor
@Component
public class BitBucketClient {

    public static final String GIT_SESSION_ID = "BITBUCKETSESSIONID";
    private static final Url GIT_URL = Url.builder().ssl(true).host("git.kb.cz").build();
    private static final Url GIT_LOGIN_URL = GIT_URL.withPath("/j_atl_security_check");

    private final HttpClient httpClient;
    private final CompareConfiguration configuration;

    public JSONObject readRepositoriesFromGit() throws Exception {
        if (!isGitLoggedIn()) {
            gitLogin();
        }
        Url url = GIT_URL.withPath("/rest/api/latest/projects/BSSC/repos?start=0&limit=100");
        return httpClient.getJsonRequest(url, null);
    }

    public Map<String, String> getLibrariesFromLatestTagVersionCatalogue(String catalogVersionBranch, List<String> bsscRepos) throws Exception {
        if (!isGitLoggedIn()) {
            gitLogin();
        }
        final Map<String, String> tagMap = new HashMap<>();
        List<String> result = new ArrayList<>();
        for (String repoName : bsscRepos) {
            if(repoName.startsWith(CompareDeployments.GitClient.VERSION_CATALOG_PREFIX) && !configuration.getIgnoredArtefacts().contains(repoName)) {
                String latestTag = findLatestTag(repoName, catalogVersionBranch);
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

    private String findLatestTag(final String catalogue, final String catalogVersionBranch) throws Exception {
        Url url = GIT_URL.withPath("/rest/api/latest/projects/BSSC/repos/" + catalogue + "/tags?start=0&limit=100&filterText=&boostMatches=true");
        JSONObject jsonResponse = httpClient.getJsonRequest(url, null);
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

    private List<String> readFileLinesFromGit(String repoName, String fileName, String tagName) throws Exception {
        if (!isGitLoggedIn()) {
            gitLogin();
        }
        Url url = Url.builder().ssl(true).host("git.kb.cz").path("/rest/api/latest/projects/BSSC/repos/" +  repoName + "/browse/" + fileName + "?at=refs%2Ftags%2F" + tagName + "&start=0&limit=20000").build();
        final JSONObject file = httpClient.getJsonRequest(url, null);
        final JSONArray linesArray = file.getJSONArray("lines");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < linesArray.length(); i++) {
            lines.add(linesArray.getJSONObject(i).get("text").toString());
        }
        return lines;
    }

    private void gitLogin() throws Exception {
        httpClient.postRequest(GIT_LOGIN_URL,
                Map.of("j_username", configuration.getUsername(),
                       "j_password", configuration.getPwd()));
        LOG.info("Bitbucket session cookie {} is {}", GIT_SESSION_ID, httpClient.getCookie(GIT_SESSION_ID));
    }

    private boolean isGitLoggedIn() {
        return !isBlank(httpClient.getCookie(GIT_SESSION_ID));
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
}
