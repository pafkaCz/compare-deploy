package cz.kb.git;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Deployment {
    private String service;
    private String version;
}
