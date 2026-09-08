package com.xjicloud.eci.provider;

/**
 * 云侧容器能力抽象，便于 Mock / 阿里云 ECI（及日后 CCI）切换。
 * <p>
 * 实现类不应维护本地业务状态机，只负责 OpenAPI 调用。
 */
public interface EciProvider {

    /**
     * 创建云实例。
     *
     * @return 云侧实例 ID（ContainerGroupId）
     */
    String create(CreateSpec spec);

    /** 查询云侧状态；不存在时可返回 status=NotFound。 */
    CloudInstanceView describe(String cloudInstanceId);

    /**
     * 拉取容器日志尾部（删实例前取证用）。
     * <p>
     * 失败时应尽量返回空串或可读错误说明，避免阻断后续删除路径。
     *
     * @param cloudInstanceId 云侧 ContainerGroupId
     * @param tailLines       尾部行数（{@code <=0} 时由实现选用合理默认）
     */
    String fetchContainerLog(String cloudInstanceId, int tailLines);

    /** 删除云实例；已不存在时应幂等成功（不抛错）。 */
    void delete(String cloudInstanceId);
}
