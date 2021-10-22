package cz.kb.git;

import lombok.Data;

import java.util.List;

@Data
public class EnvironmentDeployment {

    private Environment environment;
    private List<Deployment> deployedServices;

    public EnvironmentDeployment(Environment environment) {
        this.environment = environment;
    }
}
