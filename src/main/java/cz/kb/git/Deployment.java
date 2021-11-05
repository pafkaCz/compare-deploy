package cz.kb.git;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Deployment {
    private String serviceName;
    private String version;
}
