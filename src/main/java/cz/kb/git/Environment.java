package cz.kb.git;

public enum Environment {

    DEV("NA", "NA", "5144"),
    FAT("vncub2296.os.kb.cz", "bssc-fat-01-app", "5261"),
    SIT("vncub2242.os.kb.cz", "bssc-sit-01-app", "5144"),
    UAT("vncub2296.os.kb.cz", "bssc-uat-01-app","5261"),
    PERF("vncub2613.os.kb.cz", "bssc-perf-01-app", "6158"),
    EDU("NA", "NA", "NA"),
    REF("NA", "NA", "6158"),
    PROD("vccub2820.os.kb.cz","bssc-prod-01-app", "6513");

    String k8sHost;
    String k8sNamespace;
    int k8sApiPort = 30000;
    String clusterNamingId;

    Environment(String k8sHost, String k8sNamespace, String clusterNamingId) {
        this.k8sNamespace = k8sNamespace;
        this.k8sHost = k8sHost;
        this.clusterNamingId = clusterNamingId;
    }

}
