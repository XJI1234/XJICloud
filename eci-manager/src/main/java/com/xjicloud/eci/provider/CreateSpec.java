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
        List<String> args
) {
}
