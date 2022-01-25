package cz.kb.git;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cz.kb")
public class CompareConfiguration {

    private String pwd;
    private String username;

}
