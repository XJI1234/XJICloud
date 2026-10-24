package com.xjicloud.eci.provider;

import java.util.List;
import java.util.Map;

/** 传给 {@link EciProvider#create} 的创建规格（已与配置默认值合并）。 */
public record CreateSpec(
        String name,
        String image,
        double cpu,
        double memory,
        Map<String, String> env,
        /** 可选；ECI 指定 ECS 规格，如 GPU：{@code ecs.gn7i-c32g1.8xlarge}。 */
        String instanceType,
        /** 可选；容器占用的 GPU 个数（指定 GPU 规格时通常必填）。 */
        Integer gpu,
        /** 重启策略：Never / OnFailure / Always；由调用方与配置默认值合并后传入。 */
        String restartPolicy,
        /** 可选；覆盖镜像 ENTRYPOINT（ECI Container.Commands）。 */
        List<String> command,
        /** 可选；覆盖镜像 CMD（ECI Container.Args）。 */
        List<String> args,
        /** 可选；镜像缓存 ID，透传 ECI ImageSnapshotId。 */
        String imageSnapshotId,
        /** 可选；规范化后的 shm SizeLimit（如 {@code 8Gi}）；空则不挂 EmptyDir。 */
        String shmSize,
        /** 可选；提交任务的网络目标快照。非空时优先于当前 eci.region-id。 */
        String regionId,
        /** 可选；仅本地快照。ECI CreateContainerGroup 无 VPC 字段，云侧由交换机推导。 */
        String vpcId,
        /** 可选；提交任务的网络目标快照。非空时优先于当前 eci.vswitch-id。 */
        String vswitchId,
        /** 可选；提交任务的网络目标快照。非空时优先于当前 eci.security-group-id。 */
        String securityGroupId
) {

    /** 兼容旧调用方：不带网络目标时使用当前 EciProperties。 */
    public CreateSpec(
            String name,
            String image,
            double cpu,
            double memory,
            Map<String, String> env,
            String instanceType,
            Integer gpu,
            String restartPolicy,
            List<String> command,
            List<String> args,
            String imageSnapshotId,
            String shmSize) {
        this(name, image, cpu, memory, env, instanceType, gpu, restartPolicy, command, args,
                imageSnapshotId, shmSize, null, null, null, null);
    }
}
