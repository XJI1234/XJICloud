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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
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

    /** Provider 创建前完成投影；DataJpaTest 等切片不装配它。 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public CloudResourceConfigRuntime cloudResourceConfigRuntime(
            CloudResourceConfigService configService,
            EciProperties eciProperties,
            OssProperties ossProperties,
            OssStorageService ossStorageService,
            RuntimeCredentialService runtimeCredentialService,
            PlatformTransactionManager transactionManager) {
        CloudResourceConfigRuntime runtime = new CloudResourceConfigRuntime(configService, eciProperties, ossProperties,
                ossStorageService, runtimeCredentialService, new TransactionTemplate(transactionManager));
        runtime.initialize();
        return runtime;
    }

    /**
     * 云资源配置的启动加载和保存刷新：候选 OSS 客户端在事务内先构造，提交成功后再替换
     * 快照属性投影和客户端引用。AK/SK 仍由 {@link RuntimeCredentialService} 持有和热切。
     */
    public static class CloudResourceConfigRuntime {

        private static final Logger log = LoggerFactory.getLogger(CloudResourceConfigRuntime.class);

        private final CloudResourceConfigService configService;
        private final EciProperties eciProperties;
        private final OssProperties ossProperties;
        private final OssStorageService ossStorageService;
        private final RuntimeCredentialService runtimeCredentialService;
        private final TransactionTemplate transactions;

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

        private void initialize() {
            configService.coordinate(() -> {
                runtimeCredentialService.seedIfAbsent();
                eciProperties.coordinateWith(configService);
                ossProperties.coordinateWith(configService);
                CloudResourceConfigSnapshot snapshot = configService.snapshot();
                apply(snapshot, prepareOss(snapshot));
                configService.setRuntimeProjection(this::prepareRuntime);
                log.info("云资源配置已投影到兼容属性");
            });
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
            return configService.coordinate(() -> transactions.execute(status -> action.get()));
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

        private final class PreparedRuntime implements CloudResourceConfigService.PreparedProjection {
            private final CloudResourceConfigSnapshot snapshot;
            private final OssStorageService.PreparedClients oss;

            private PreparedRuntime(CloudResourceConfigSnapshot snapshot, OssStorageService.PreparedClients oss) {
                this.snapshot = snapshot;
                this.oss = oss;
            }

            @Override
            public void apply() {
                CloudResourceConfigRuntime.this.apply(snapshot, oss);
            }

            @Override
            public void close() {
                oss.close();
            }
        }
    }
}
