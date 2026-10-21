package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.dto.InstanceResponse;
import com.xjicloud.eci.task.EciTaskEntity;

final class TaskSuccessEvidence {

    private TaskSuccessEvidence() {
    }

    static boolean isSatisfied(EciTaskEntity task, InstanceResponse instance, boolean persistedEvidence) {
        if ("Always".equalsIgnoreCase(task.getRestartPolicy())) {
            return false;
        }
        return persistedEvidence || "Succeeded".equalsIgnoreCase(instance.cloudStatus());
    }
}
