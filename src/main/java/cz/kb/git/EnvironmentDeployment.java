package cz.kb.git;

import lombok.Data;

import java.util.List;

@Data
public class EnvironmentDeployment {

    private Environment environment;
    private List<Deployment> deployments;

    public EnvironmentDeployment(Environment environment) {
        this.environment = environment;
    }

    public String getDeployedServiceVersion(String serviceName) {
        return deployments.stream().filter(e -> e.getServiceName().equals(serviceName)).findFirst().orElse(new Deployment()).getVersion();
    }
}
