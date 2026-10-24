package com.xjicloud.eci.config;

import com.xjicloud.eci.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置来源优先级、集中默认值、校验和分类保存/恢复默认；数据库使用内存 H2。
 */
@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "spring.datasource.url=jdbc:h2:mem:cloud-resource-config-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class CloudResourceConfigServiceTest {

    @Autowired
    private CloudResourceConfigRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockEnvironment environment;
    private TransactionTemplate transactions;
    private CloudResourceConfigService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        environment = new MockEnvironment();
        transactions = new TransactionTemplate(transactionManager);
        service = new CloudResourceConfigService(repository, environment);
    }

    /** 生产由 @Transactional 代理开启事务，这里手动构造的服务用同样的边界。 */
    private CloudResourceConfigSnapshot transactional(Supplier<CloudResourceConfigSnapshot> action) {
        return transactions.execute(status -> action.get());
    }

    @Test
    void databaseOverrideWinsOverEnvironment() {
        environment.setProperty("ECI_OSS_BUCKET", "env-bucket");

        CloudResourceConfigSnapshot snapshot =
                transactional(() -> service.saveOss(Map.of("ECI_OSS_BUCKET", "database-bucket")));

        assertThat(snapshot.oss().bucket()).isEqualTo("database-bucket");
        assertThat(snapshot.values().get("ECI_OSS_BUCKET").value()).isEqualTo("database-bucket");
        assertThat(snapshot.values().get("ECI_OSS_BUCKET").source()).isEqualTo("database");
    }

    @Test
    void environmentOverrideWinsOverDefault() {
        environment.setProperty("ECI_OSS_BUCKET", "env-bucket");
        environment.setProperty("ECI_CREATE_TIMEOUT_SEC", "600");

        CloudResourceConfigSnapshot snapshot = service.snapshot();

        assertThat(snapshot.oss().bucket()).isEqualTo("env-bucket");
        assertThat(snapshot.values().get("ECI_OSS_BUCKET").source()).isEqualTo("environment");
        assertThat(snapshot.advanced().createTimeoutSec()).isEqualTo(600);
        assertThat(snapshot.values().get("ECI_CREATE_TIMEOUT_SEC").source()).isEqualTo("environment");
    }

    @Test
    void emptyDatabaseValueStaysEmptyAndDoesNotFallBack() {
        environment.setProperty("ECI_OSS_BUCKET", "env-bucket");

        CloudResourceConfigSnapshot snapshot = transactional(() -> service.saveOss(Map.of("ECI_OSS_BUCKET", "")));

        assertThat(snapshot.oss().bucket()).isEmpty();
        assertThat(snapshot.values().get("ECI_OSS_BUCKET").source()).isEqualTo("database");
        assertThat(repository.findById("ECI_OSS_BUCKET")).isPresent();
        assertThat(repository.findById("ECI_OSS_BUCKET").orElseThrow().getConfigValue()).isEmpty();
    }

    @Test
    void defaultsComeFromCentralDefinitions() {
        CloudResourceConfigSnapshot snapshot = service.snapshot();

        assertThat(CloudResourceConfigDefinition.all()).hasSize(15);
        assertThat(CloudResourceConfigDefinition.inCategory(CloudResourceConfigKey.Category.NETWORK)).hasSize(4);
        assertThat(CloudResourceConfigDefinition.inCategory(CloudResourceConfigKey.Category.OSS)).hasSize(6);
        assertThat(CloudResourceConfigDefinition.inCategory(CloudResourceConfigKey.Category.ADVANCED)).hasSize(5);
        assertThat(snapshot.values().keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(CloudResourceConfigKey.values()).map(CloudResourceConfigKey::key).toList());

        assertThat(snapshot.network().regionId()).isEqualTo("cn-hangzhou");
        assertThat(snapshot.network().vpcId()).isEmpty();
        assertThat(snapshot.network().vswitchId()).isEmpty();
        assertThat(snapshot.network().securityGroupId()).isEmpty();
        assertThat(snapshot.oss().enabled()).isTrue();
        assertThat(snapshot.oss().endpoint()).isEqualTo("https://oss-cn-hangzhou-internal.aliyuncs.com");
        assertThat(snapshot.oss().region()).isEqualTo("cn-hangzhou");
        assertThat(snapshot.oss().bucket()).isEmpty();
        assertThat(snapshot.oss().pathStyleAccess()).isFalse();
        assertThat(snapshot.oss().presignExpirationMinutes()).isEqualTo(1440);
        assertThat(snapshot.advanced().defaultImage()).isEmpty();
        assertThat(snapshot.advanced().defaultShmSize()).isEmpty();
        assertThat(snapshot.advanced().createTimeoutSec()).isEqualTo(1800);
        assertThat(snapshot.advanced().failureLogTailLines()).isEqualTo(1000);
        assertThat(snapshot.advanced().failureLogGraceSec()).isEqualTo(90);
        assertThat(snapshot.values().get("ECI_OSS_ENABLED").source()).isEqualTo("default");

        assertThatThrownBy(() -> snapshot.values().put(
                "ECI_VPC_ID", new CloudResourceConfigSnapshot.ResolvedValue("vpc-x", "default")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void savesNetworkCategoryOnly() {
        CloudResourceConfigSnapshot snapshot = transactional(() -> service.saveNetwork(Map.of(
                "ECI_REGION_ID", "cn-shanghai",
                "ECI_VPC_ID", "vpc-1",
                "ECI_VSWITCH_ID", "vsw-1",
                "ECI_SECURITY_GROUP_ID", "sg-1")));

        assertThat(snapshot.network().regionId()).isEqualTo("cn-shanghai");
        assertThat(snapshot.network().vpcId()).isEqualTo("vpc-1");
        assertThat(snapshot.network().vswitchId()).isEqualTo("vsw-1");
        assertThat(snapshot.network().securityGroupId()).isEqualTo("sg-1");
        assertThat(snapshot.values().get("ECI_REGION_ID").source()).isEqualTo("database");
        assertThat(repository.findById("ECI_VSWITCH_ID")).isPresent();
        assertThat(repository.findById("ECI_OSS_BUCKET")).isEmpty();
        assertThat(repository.findById("ECI_CREATE_TIMEOUT_SEC")).isEmpty();
    }

    @Test
    void savesOssCategory() {
        CloudResourceConfigSnapshot snapshot = transactional(() -> service.saveOss(Map.of(
                "ECI_OSS_ENABLED", "false",
                "ECI_OSS_ENDPOINT", "https://oss-cn-shanghai.aliyuncs.com",
                "ECI_OSS_REGION", "cn-shanghai",
                "ECI_OSS_BUCKET", "task-output",
                "ECI_OSS_PATH_STYLE_ACCESS", "true",
                "ECI_OSS_PRESIGN_EXPIRATION_MINUTES", "120")));

        assertThat(snapshot.oss().enabled()).isFalse();
        assertThat(snapshot.oss().endpoint()).isEqualTo("https://oss-cn-shanghai.aliyuncs.com");
        assertThat(snapshot.oss().region()).isEqualTo("cn-shanghai");
        assertThat(snapshot.oss().bucket()).isEqualTo("task-output");
        assertThat(snapshot.oss().pathStyleAccess()).isTrue();
        assertThat(snapshot.oss().presignExpirationMinutes()).isEqualTo(120);
        assertThat(repository.findById("ECI_OSS_BUCKET")).isPresent();
        assertThat(repository.findById("ECI_REGION_ID")).isEmpty();
    }

    @Test
    void savesAdvancedCategory() {
        CloudResourceConfigSnapshot snapshot = transactional(() -> service.saveAdvanced(Map.of(
                "ECI_DEFAULT_IMAGE", "nginx:1.27-alpine",
                "ECI_DEFAULT_SHM_SIZE", "256M",
                "ECI_CREATE_TIMEOUT_SEC", "600",
                "ECI_FAILURE_LOG_TAIL_LINES", "200",
                "ECI_FAILURE_LOG_GRACE_SEC", "0")));

        assertThat(snapshot.advanced().defaultImage()).isEqualTo("nginx:1.27-alpine");
        assertThat(snapshot.advanced().defaultShmSize()).isEqualTo("256Mi");
        assertThat(snapshot.advanced().createTimeoutSec()).isEqualTo(600);
        assertThat(snapshot.advanced().failureLogTailLines()).isEqualTo(200);
        assertThat(snapshot.advanced().failureLogGraceSec()).isZero();
        assertThat(snapshot.values().get("ECI_DEFAULT_SHM_SIZE").value()).isEqualTo("256Mi");
        assertThat(repository.findById("ECI_OSS_BUCKET")).isEmpty();
    }

    @Test
    void booleanAcceptsOnlyTrueOrFalse() {
        assertThatThrownBy(() -> transactional(() -> service.saveOss(Map.of("ECI_OSS_ENABLED", "yes"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启用 OSS");
        assertThatThrownBy(() -> transactional(() -> service.saveOss(Map.of("ECI_OSS_PATH_STYLE_ACCESS", "1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("使用路径风格访问");

        CloudResourceConfigSnapshot snapshot =
                transactional(() -> service.saveOss(Map.of("ECI_OSS_ENABLED", "false")));

        assertThat(snapshot.oss().enabled()).isFalse();
        assertThat(snapshot.values().get("ECI_OSS_ENABLED").value()).isEqualTo("false");
    }

    @Test
    void positiveIntegerRulesApply() {
        assertThatThrownBy(() -> transactional(() -> service.saveOss(
                Map.of("ECI_OSS_PRESIGN_EXPIRATION_MINUTES", "0"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预签名 URL 有效期（分钟）");
        assertThatThrownBy(() -> transactional(() -> service.saveOss(
                Map.of("ECI_OSS_PRESIGN_EXPIRATION_MINUTES", "-5"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> transactional(() -> service.saveAdvanced(
                Map.of("ECI_FAILURE_LOG_TAIL_LINES", "abc"))))
                .isInstanceOf(BusinessException.class);

        CloudResourceConfigSnapshot snapshot = transactional(() -> service.saveOss(
                Map.of("ECI_OSS_PRESIGN_EXPIRATION_MINUTES", "120")));

        assertThat(snapshot.oss().presignExpirationMinutes()).isEqualTo(120);
    }

    @Test
    void graceSecondsAcceptZeroButNotNegative() {
        assertThatThrownBy(() -> transactional(() -> service.saveAdvanced(Map.of("ECI_FAILURE_LOG_GRACE_SEC", "-1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("失败删除宽限时间（秒）");

        CloudResourceConfigSnapshot snapshot =
                transactional(() -> service.saveAdvanced(Map.of("ECI_FAILURE_LOG_GRACE_SEC", "0")));

        assertThat(snapshot.advanced().failureLogGraceSec()).isZero();
    }

    @Test
    void endpointMustBeBasicUrlAndMayBeEmpty() {
        assertThatThrownBy(() -> transactional(() -> service.saveOss(Map.of("ECI_OSS_ENDPOINT", "not-a-url"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OSS 地址");

        CloudResourceConfigSnapshot snapshot = transactional(() -> service.saveOss(
                Map.of("ECI_OSS_ENDPOINT", "https://oss-cn-shanghai.aliyuncs.com")));
        assertThat(snapshot.oss().endpoint()).isEqualTo("https://oss-cn-shanghai.aliyuncs.com");

        CloudResourceConfigSnapshot cleared =
                transactional(() -> service.saveOss(Map.of("ECI_OSS_ENDPOINT", "")));
        assertThat(cleared.oss().endpoint()).isEmpty();
    }

    @Test
    void failedSaveWritesNothing() {
        assertThatThrownBy(() -> transactional(() -> service.saveOss(Map.of(
                "ECI_OSS_BUCKET", "task-output",
                "ECI_OSS_PRESIGN_EXPIRATION_MINUTES", "0"))))
                .isInstanceOf(BusinessException.class);

        assertThat(repository.findById("ECI_OSS_BUCKET")).isEmpty();
        assertThat(repository.findById("ECI_OSS_PRESIGN_EXPIRATION_MINUTES")).isEmpty();
        assertThat(new CloudResourceConfigService(repository, environment).snapshot().oss().bucket()).isEmpty();
    }

    @Test
    void resetDeletesWholeCategoryAndFallsBack() {
        environment.setProperty("ECI_OSS_BUCKET", "env-bucket");
        transactional(() -> service.saveNetwork(Map.of("ECI_REGION_ID", "cn-shanghai", "ECI_VPC_ID", "vpc-1")));
        transactional(() -> service.saveOss(Map.of("ECI_OSS_BUCKET", "database-bucket")));
        transactional(() -> service.saveAdvanced(Map.of("ECI_CREATE_TIMEOUT_SEC", "600")));

        CloudResourceConfigSnapshot snapshot = transactional(service::resetNetwork);

        assertThat(snapshot.network().regionId()).isEqualTo("cn-hangzhou");
        assertThat(snapshot.values().get("ECI_REGION_ID").source()).isEqualTo("default");
        assertThat(snapshot.oss().bucket()).isEqualTo("database-bucket");
        assertThat(snapshot.advanced().createTimeoutSec()).isEqualTo(600);
        assertThat(repository.findById("ECI_REGION_ID")).isEmpty();
        assertThat(repository.findById("ECI_VPC_ID")).isEmpty();
        assertThat(repository.findById("ECI_OSS_BUCKET")).isPresent();
        assertThat(repository.findById("ECI_CREATE_TIMEOUT_SEC")).isPresent();
    }

    @Test
    void resetFallsBackToEnvironmentWhenNoDatabaseRow() {
        environment.setProperty("ECI_OSS_BUCKET", "env-bucket");
        transactional(() -> service.saveOss(Map.of("ECI_OSS_BUCKET", "database-bucket")));

        CloudResourceConfigSnapshot snapshot = transactional(service::resetOss);

        assertThat(snapshot.oss().bucket()).isEqualTo("env-bucket");
        assertThat(snapshot.values().get("ECI_OSS_BUCKET").source()).isEqualTo("environment");
    }

    @Test
    void resetWithoutOverridesKeepsDefaults() {
        CloudResourceConfigSnapshot snapshot = transactional(service::resetAdvanced);

        assertThat(snapshot.advanced().createTimeoutSec()).isEqualTo(1800);
        assertThat(snapshot.advanced().failureLogGraceSec()).isEqualTo(90);
        assertThat(repository.count()).isZero();
    }
}
