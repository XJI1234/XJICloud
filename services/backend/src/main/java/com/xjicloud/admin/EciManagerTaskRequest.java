package com.xjicloud.admin;

import java.util.List;
import java.util.Map;

public record EciManagerTaskRequest(
        String name,
        String image,
        List<String> command,
        List<String> args,
        Double cpu,
        Double memory,
        Integer gpu,
        String instanceType,
        String imageSnapshotId,
        String shmSize,
        String restartPolicy,
        Integer maxRuntimeSec,
        Map<String, String> env,
        String clientRequestId,
        Storage storage
) {
    public record Storage(String mode, List<String> inputKeys, String outputKey) {
    }
}
