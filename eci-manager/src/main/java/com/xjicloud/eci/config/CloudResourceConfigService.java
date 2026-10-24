package com.xjicloud.eci.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 云资源配置的来源解析、校验、持久化和当前快照入口。 */
@Service
public class CloudResourceConfigService {

    private static final String SOURCE_DATABASE = "database";
    private static final String SOURCE_ENVIRONMENT = "environment";
    private static final String SOURCE_DEFAULT = "default";

    private final CloudResourceConfigRepository repository;
    private final Environment environment;
    private CloudResourceConfigSnapshot current;

    public CloudResourceConfigService(CloudResourceConfigRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    /** 当前三个分类的不可变快照。 */
    public synchronized CloudResourceConfigSnapshot snapshot() {
        if (current == null) {
            current = toSnapshot(parseAll());
        }
        return current;
    }

    /** 保存网络分类的覆盖值并返回新快照。 */
    @Transactional
    public synchronized CloudResourceConfigSnapshot saveNetwork(Map<String, String> updates) {
        return save(CloudResourceConfigKey.Category.NETWORK, updates);
    }

    /** 保存 OSS 分类的覆盖值并返回新快照。 */
    @Transactional
    public synchronized CloudResourceConfigSnapshot saveOss(Map<String, String> updates) {
        return save(CloudResourceConfigKey.Category.OSS, updates);
    }

    /** 保存高级分类的覆盖值并返回新快照。 */
    @Transactional
    public synchronized CloudResourceConfigSnapshot saveAdvanced(Map<String, String> updates) {
        return save(CloudResourceConfigKey.Category.ADVANCED, updates);
    }

    /** 删除网络分类的覆盖值并返回新快照。 */
    @Transactional
    public synchronized CloudResourceConfigSnapshot resetNetwork() {
        return reset(CloudResourceConfigKey.Category.NETWORK);
    }

    /** 删除 OSS 分类的覆盖值并返回新快照。 */
    @Transactional
    public synchronized CloudResourceConfigSnapshot resetOss() {
        return reset(CloudResourceConfigKey.Category.OSS);
    }

    /** 删除高级分类的覆盖值并返回新快照。 */
    @Transactional
    public synchronized CloudResourceConfigSnapshot resetAdvanced() {
        return reset(CloudResourceConfigKey.Category.ADVANCED);
    }

    private CloudResourceConfigSnapshot save(CloudResourceConfigKey.Category category, Map<String, String> updates) {
        Map<CloudResourceConfigKey, Resolved> candidate = parseAll();
        for (CloudResourceConfigDefinition definition : CloudResourceConfigDefinition.inCategory(category)) {
            if (updates.containsKey(definition.key())) {
                // 先把整个分类校验完，再写库，保存失败不留半套覆盖值。
                candidate.put(definition.id(), new Resolved(
                        CloudResourceConfigRules.parseAndValidate(definition, updates.get(definition.key())),
                        SOURCE_DATABASE));
            }
        }

        Instant now = Instant.now();
        for (CloudResourceConfigDefinition definition : CloudResourceConfigDefinition.inCategory(category)) {
            if (!updates.containsKey(definition.key())) {
                continue;
            }
            String value = candidate.get(definition.id()).parsed().text();
            CloudResourceConfigEntity entity = repository.findById(definition.key())
                    .orElseGet(() -> new CloudResourceConfigEntity(definition.key(), value, now));
            entity.setConfigValue(value);
            entity.setUpdatedAt(now);
            repository.save(entity);
        }

        return publishAfterCommit(toSnapshot(candidate));
    }

    private CloudResourceConfigSnapshot reset(CloudResourceConfigKey.Category category) {
        List<String> keys = CloudResourceConfigDefinition.inCategory(category).stream()
                .map(CloudResourceConfigDefinition::key)
                .toList();
        repository.deleteByConfigKeyIn(keys);
        return publishAfterCommit(toSnapshot(parseAll()));
    }

    /** 快照只在事务提交后替换；提交失败时内存快照保持与数据库一致。 */
    private CloudResourceConfigSnapshot publishAfterCommit(CloudResourceConfigSnapshot next) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(next);
                }
            });
        } else {
            publish(next);
        }
        return next;
    }

    /** 与 snapshot() 共用同一把锁发布，保证其他线程可见且不产生数据竞争。 */
    private synchronized void publish(CloudResourceConfigSnapshot next) {
        current = next;
    }

    /** 数据库覆盖值 > 环境变量值 > 集中定义默认值。 */
    private Map<CloudResourceConfigKey, Resolved> parseAll() {
        Map<String, CloudResourceConfigEntity> persisted = new LinkedHashMap<>();
        repository.findAll().forEach(entity -> persisted.put(entity.getConfigKey(), entity));

        Map<CloudResourceConfigKey, Resolved> resolved = new EnumMap<>(CloudResourceConfigKey.class);
        for (CloudResourceConfigDefinition definition : CloudResourceConfigDefinition.all()) {
            CloudResourceConfigEntity entity = persisted.get(definition.key());
            String value;
            String source;
            if (entity != null) {
                value = entity.getConfigValue();
                source = SOURCE_DATABASE;
            } else {
                String environmentValue = environment.getProperty(definition.environmentName());
                if (environmentValue != null) {
                    value = environmentValue;
                    source = SOURCE_ENVIRONMENT;
                } else {
                    value = definition.defaultValue();
                    source = SOURCE_DEFAULT;
                }
            }
            resolved.put(definition.id(), new Resolved(CloudResourceConfigRules.parseAndValidate(definition, value), source));
        }
        return resolved;
    }

    private CloudResourceConfigSnapshot toSnapshot(Map<CloudResourceConfigKey, Resolved> resolved) {
        Map<String, CloudResourceConfigSnapshot.ResolvedValue> values = new LinkedHashMap<>();
        for (CloudResourceConfigDefinition definition : CloudResourceConfigDefinition.all()) {
            Resolved item = resolved.get(definition.id());
            values.put(definition.key(), new CloudResourceConfigSnapshot.ResolvedValue(item.parsed().text(), item.source()));
        }

        return new CloudResourceConfigSnapshot(
                new CloudResourceConfigSnapshot.Network(
                        text(resolved, CloudResourceConfigKey.REGION_ID),
                        text(resolved, CloudResourceConfigKey.VPC_ID),
                        text(resolved, CloudResourceConfigKey.VSWITCH_ID),
                        text(resolved, CloudResourceConfigKey.SECURITY_GROUP_ID)),
                new CloudResourceConfigSnapshot.Oss(
                        resolved.get(CloudResourceConfigKey.OSS_ENABLED).parsed().booleanValue(),
                        text(resolved, CloudResourceConfigKey.OSS_ENDPOINT),
                        text(resolved, CloudResourceConfigKey.OSS_REGION),
                        text(resolved, CloudResourceConfigKey.OSS_BUCKET),
                        resolved.get(CloudResourceConfigKey.OSS_PATH_STYLE_ACCESS).parsed().booleanValue(),
                        resolved.get(CloudResourceConfigKey.OSS_PRESIGN_EXPIRATION_MINUTES).parsed().intValue()),
                new CloudResourceConfigSnapshot.Advanced(
                        text(resolved, CloudResourceConfigKey.DEFAULT_IMAGE),
                        text(resolved, CloudResourceConfigKey.DEFAULT_SHM_SIZE),
                        resolved.get(CloudResourceConfigKey.CREATE_TIMEOUT_SEC).parsed().intValue(),
                        resolved.get(CloudResourceConfigKey.FAILURE_LOG_TAIL_LINES).parsed().intValue(),
                        resolved.get(CloudResourceConfigKey.FAILURE_LOG_GRACE_SEC).parsed().intValue()),
                Map.copyOf(values));
    }

    private static String text(Map<CloudResourceConfigKey, Resolved> resolved, CloudResourceConfigKey key) {
        return resolved.get(key).parsed().text();
    }

    /** 单字段的解析结果和来源。 */
    private record Resolved(CloudResourceConfigRules.ParsedValue parsed, String source) {
    }
}
