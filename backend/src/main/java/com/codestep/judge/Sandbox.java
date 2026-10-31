package com.codestep.judge;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface Sandbox {

    SandboxOutput execute(Map<String, String> sources, String mainClass, List<String> inputs)
            throws IOException, InterruptedException, SandboxTimeoutException;
}
