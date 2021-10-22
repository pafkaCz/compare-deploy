package cz.kb.git;

public enum Environment {

    DEV("NA", "NA"),
    FAT("vncub2296.os.kb.cz", "bssc-fat-01-app"),
    SIT("vncub2242.os.kb.cz", "bssc-sit-01-app"),
    UAT("vncub2296.os.kb.cz", "bssc-uat-01-app"),
    PERF("vncub2613.os.kb.cz", "bssc-perf-01-app"),
    PROD("vccub2820.os.kb.cz","bssc-prod-01-app");

    String k8sHost;
    String k8sNamespace;
    int port = 3000;

    Environment(String k8sHost, String k8sNamespace) {
        this.k8sNamespace = k8sNamespace;
        this.k8sHost = k8sHost;
    }

}
