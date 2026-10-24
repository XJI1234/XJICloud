package com.xjicloud.eci;

import com.xjicloud.eci.config.CloudResourceConfigService;
import com.xjicloud.eci.config.CloudResourceConfigSnapshot;
import com.xjicloud.eci.config.EciProperties;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.credential.AccessKeyPair;
import com.xjicloud.eci.credential.AliyunResource;
import com.xjicloud.eci.credential.RuntimeCredentialService;
import com.xjicloud.eci.oss.OssStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * ECI 容器管理 Demo 启动入口。
 * <p>
 * 排除默认 {@link UserDetailsServiceAutoConfiguration}，本服务仅使用 Api-Key，不使用表单/内存用户。
 * {@link EnableConfigurationProperties} 同时注册 {@link EciProperties} 与 {@link OssProperties}（{@code eci.oss.*}）。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({EciProperties.class, OssProperties.class})
public class EciManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EciManagerApplication.class, args);
    }

    /** 启动加载与保存刷新共用的运行时投影入口；DataJpaTest 等切片不装配它。 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public CloudResourceConfigRuntime cloudResourceConfigRuntime(
            CloudResourceConfigService configService,
            EciProperties eciProperties,
            OssProperties ossProperties,
            OssStorageService ossStorageService,
            RuntimeCredentialService runtimeCredentialService,
            PlatformTransactionManager transactionManager) {
        return new CloudResourceConfigRuntime(configService, eciProperties, ossProperties,
                ossStorageService, runtimeCredentialService, new TransactionTemplate(transactionManager));
    }

    /**
     * 云资源配置的启动加载和保存刷新：候选 OSS 客户端在事务内先构造，提交成功后再替换
     * 快照属性投影和客户端引用。AK/SK 仍由 {@link RuntimeCredentialService} 持有和热切。
     */
    public static class CloudResourceConfigRuntime implements SmartInitializingSingleton {

        private static final Logger log = LoggerFactory.getLogger(CloudResourceConfigRuntime.class);
        private static final String SOURCE_DEFAULT = "default";

        private final CloudResourceConfigService configService;
        private final EciProperties eciProperties;
        private final OssProperties ossProperties;
        private final OssStorageService ossStorageService;
        private final RuntimeCredentialService runtimeCredentialService;
        private final TransactionTemplate transactions;
        private final ReentrantLock lock = new ReentrantLock();

        CloudResourceConfigRuntime(
                CloudResourceConfigService configService,
                EciProperties eciProperties,
                OssProperties ossProperties,
                OssStorageService ossStorageService,
                RuntimeCredentialService runtimeCredentialService,
                TransactionTemplate transactions) {
            this.configService = configService;
            this.eciProperties = eciProperties;
            this.ossProperties = ossProperties;
            this.ossStorageService = ossStorageService;
            this.runtimeCredentialService = runtimeCredentialService;
            this.transactions = transactions;
        }

        @Override
        public void afterSingletonsInstantiated() {
            lock.lock();
            try {
                CloudResourceConfigSnapshot snapshot = configService.snapshot();
                // 没有数据库或环境覆盖时保持既有进程/yml 绑定，避免未迁移消费者在本次改动中行为突变。
                if (!hasConfiguredValue(snapshot)) {
                    log.info("云资源配置未发现数据库或环境覆盖，兼容属性保持进程配置");
                    return;
                }
                apply(snapshot, prepareOss(snapshot));
                log.info("云资源配置已投影到兼容属性");
            } finally {
                lock.unlock();
            }
        }

        public CloudResourceConfigSnapshot snapshot() {
            return configService.snapshot();
        }

        public CloudResourceConfigSnapshot saveNetwork(Map<String, String> updates) {
            return save(() -> configService.saveNetwork(updates));
        }

        public CloudResourceConfigSnapshot saveOss(Map<String, String> updates) {
            return save(() -> configService.saveOss(updates));
        }

        public CloudResourceConfigSnapshot saveAdvanced(Map<String, String> updates) {
            return save(() -> configService.saveAdvanced(updates));
        }

        public CloudResourceConfigSnapshot resetNetwork() {
            return save(configService::resetNetwork);
        }

        public CloudResourceConfigSnapshot resetOss() {
            return save(configService::resetOss);
        }

        public CloudResourceConfigSnapshot resetAdvanced() {
            return save(configService::resetAdvanced);
        }

        /**
         * 在同一把锁内提交配置事务并替换运行时对象；候选 OSS 客户端构造失败时事务回滚，
         * 当前快照、属性投影和客户端引用都保持不变。
         */
        private CloudResourceConfigSnapshot save(Supplier<CloudResourceConfigSnapshot> action) {
            lock.lock();
            try {
                PreparedRuntime[] candidate = new PreparedRuntime[1];
                PreparedRuntime next;
                try {
                    next = transactions.execute(status -> {
                        CloudResourceConfigSnapshot saved = action.get();
                        PreparedRuntime prepared = prepareRuntime(saved);
                        candidate[0] = prepared;
                        return prepared;
                    });
                } catch (RuntimeException | Error ex) {
                    if (candidate[0] != null) {
                        candidate[0].oss().close();
                    }
                    throw ex;
                }
                apply(next.snapshot(), next.oss());
                return next.snapshot();
            } finally {
                lock.unlock();
            }
        }

        private PreparedRuntime prepareRuntime(CloudResourceConfigSnapshot snapshot) {
            return new PreparedRuntime(snapshot, prepareOss(snapshot));
        }

        private OssStorageService.PreparedClients prepareOss(CloudResourceConfigSnapshot snapshot) {
            AccessKeyPair pair = runtimeCredentialService.currentPair(AliyunResource.OSS);
            return ossStorageService.prepare(snapshot.oss(), pair.accessKeyId(), pair.accessKeySecret());
        }

        private void apply(CloudResourceConfigSnapshot snapshot, OssStorageService.PreparedClients ossClients) {
            eciProperties.applyCloudConfig(snapshot);
            ossProperties.applyCloudConfig(snapshot);
            ossStorageService.applyRuntime(ossClients);
        }

        private static boolean hasConfiguredValue(CloudResourceConfigSnapshot snapshot) {
            return snapshot.values().values().stream()
                    .anyMatch(value -> !SOURCE_DEFAULT.equals(value.source()));
        }
    }

    private record PreparedRuntime(
            CloudResourceConfigSnapshot snapshot,
            OssStorageService.PreparedClients oss) {
    }
}
