package com.xjicloud.eci.lifecycle;

import com.xjicloud.eci.instance.CompensationOutcome;
import com.xjicloud.eci.instance.EciInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 启动补偿：进程崩溃可能留下 CREATING/STOPPING；启动时扫一遍并对齐或续跑异步任务。
 * <p>
 * Demo 假定单实例部署；多节点 HA 并发补偿需分布式锁，本阶段不做。
 * 本 Runner 失败只记日志，不阻断应用启动。
 */
@Component
public class StartupCompensationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCompensationRunner.class);

    private final EciInstanceService instanceService;

    public StartupCompensationRunner(EciInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<UUID> ids = instanceService.listIdsForStartupCompensation();
            int resumedCreate = 0;
            int resumedDelete = 0;
            int failedLocal = 0;
            int terminatedLocal = 0;
            int refreshedOnly = 0;
            int skipped = 0;

            for (UUID id : ids) {
                try {
                    CompensationOutcome outcome = instanceService.compensateOne(id);
                    switch (outcome) {
                        case RESUMED_CREATE -> resumedCreate++;
                        case RESUMED_DELETE -> resumedDelete++;
                        case FAILED_LOCAL -> failedLocal++;
                        case TERMINATED_LOCAL -> terminatedLocal++;
                        case REFRESHED_ONLY -> refreshedOnly++;
                        case SKIPPED -> skipped++;
                    }
                } catch (Exception e) {
                    log.warn("startup compensation failed for instance id={}", id, e);
                }
            }

            log.info(
                    "startup compensation done scanned={} resumedCreate={} resumedDelete={} "
                            + "failedLocal={} terminatedLocal={} refreshedOnly={} skipped={}",
                    ids.size(),
                    resumedCreate,
                    resumedDelete,
                    failedLocal,
                    terminatedLocal,
                    refreshedOnly,
                    skipped);
        } catch (Exception e) {
            log.error("startup compensation aborted", e);
        }
    }
}
