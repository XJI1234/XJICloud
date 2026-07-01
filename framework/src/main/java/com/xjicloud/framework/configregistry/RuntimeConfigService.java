package com.xjicloud.framework.configregistry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.framework.common.BusinessException;
import com.xjicloud.framework.config.FrameworkProperties;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.AliyunConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.BackendConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.CorsConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.DatabaseConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.JwtConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.OssConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.RedisConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.StorageConfig;
import com.xjicloud.framework.configregistry.RuntimeConfigDocument.WorkerConfig;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeConfigService {

    private static final String SINGLETON_ID = "default";

    private final RuntimeConfigStateRepository repository;
    private final SecretCrypto crypto;
    private final ObjectMapper objectMapper;
    private final FrameworkProperties properties;

    public RuntimeConfigService(
            RuntimeConfigStateRepository repository,
            SecretCrypto crypto,
            ObjectMapper objectMapper,
            FrameworkProperties properties
    ) {
        this.repository = repository;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    @Transactional
    public void initDefaults() {
        if (repository.findById(SINGLETON_ID).isEmpty()) {
            RuntimeConfigState state = new RuntimeConfigState();
            state.setId(SINGLETON_ID);
            state.setRevision(1);
            state.setPayloadJson(serialize(defaultConfig()));
            state.setUpdatedAt(Instant.now());
            repository.save(state);
        }
    }

    public RuntimeConfigSnapshot getSnapshot() {
        RuntimeConfigState state = repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new BusinessException("配置未初始化"));
        RuntimeConfigDocument doc = deserialize(state.getPayloadJson());
        return new RuntimeConfigSnapshot(state.getRevision(), state.getUpdatedAt(), doc);
    }

    public RuntimeConfigSnapshot getSnapshotForRevision(int revision) {
        RuntimeConfigSnapshot snap = getSnapshot();
        if (snap.revision() != revision) {
            return null;
        }
        return snap;
    }

    @Transactional
    public RuntimeConfigSnapshot update(RuntimeConfigDocument incoming, boolean mergeSecrets) {
        RuntimeConfigState state = repository.findById(SINGLETON_ID).orElseThrow();
        RuntimeConfigDocument current = deserialize(state.getPayloadJson());
        RuntimeConfigDocument merged = merge(current, incoming, mergeSecrets);
        state.setRevision(state.getRevision() + 1);
        state.setPayloadJson(serialize(merged));
        state.setUpdatedAt(Instant.now());
        repository.save(state);
        return new RuntimeConfigSnapshot(state.getRevision(), state.getUpdatedAt(), merged);
    }

    public RuntimeConfigDocument getPublicDocument() {
        RuntimeConfigDocument doc = getSnapshot().config();
        return doc.withSecretsMasked();
    }

    public RuntimeConfigDocument getFullDocumentForBackend() {
        return decryptDocument(getSnapshot().config());
    }

    public RuntimeConfigDocument decryptDocument(RuntimeConfigDocument doc) {
        DatabaseConfig db = doc.database();
        if (db != null) {
            db = new DatabaseConfig(db.url(), db.username(), crypto.decrypt(db.password()));
        }
        RedisConfig redis = doc.redis();
        if (redis != null) {
            redis = new RedisConfig(redis.host(), redis.port(), crypto.decrypt(redis.password()), redis.queueKey());
        }
        OssConfig oss = doc.oss();
        if (oss != null) {
            oss = new OssConfig(
                    oss.endpoint(), oss.region(), oss.bucket(), oss.accessKey(),
                    crypto.decrypt(oss.secretKey()), oss.pathStyleAccess(), oss.presignExpirationMinutes()
            );
        }
        JwtConfig jwt = doc.jwt();
        if (jwt != null) {
            jwt = new JwtConfig(crypto.decrypt(jwt.secret()), jwt.expirationMs());
        }
        WorkerConfig worker = doc.worker();
        if (worker != null) {
            worker = new WorkerConfig(
                    crypto.decrypt(worker.sharedSecret()), worker.heartbeatTimeoutSec(), worker.pollTimeoutSec()
            );
        }
        return new RuntimeConfigDocument(db, redis, oss, doc.storage(), jwt, doc.cors(), worker, doc.backend(), doc.aliyun());
    }

    public RuntimeConfigDocument getSubsetForRole(String role) {
        RuntimeConfigDocument full = getSnapshot().config();
        return switch (role == null ? "" : role.toUpperCase()) {
            case "FRONTEND" -> new RuntimeConfigDocument(
                    null, null, null, null, null, full.cors(), null, full.backend(), null
            );
            case "BACKEND" -> full;
            default -> new RuntimeConfigDocument(
                    null, null, null, full.storage(), null, full.cors(), null, full.backend(), null
            );
        };
    }

    private RuntimeConfigDocument merge(RuntimeConfigDocument current, RuntimeConfigDocument incoming, boolean mergeSecrets) {
        DatabaseConfig db = mergeDb(current.database(), incoming.database(), mergeSecrets);
        RedisConfig redis = mergeRedis(current.redis(), incoming.redis(), mergeSecrets);
        OssConfig oss = mergeOss(current.oss(), incoming.oss(), mergeSecrets);
        return new RuntimeConfigDocument(
                db,
                redis,
                oss,
                incoming.storage() != null ? incoming.storage() : current.storage(),
                incoming.jwt() != null ? incoming.jwt() : current.jwt(),
                incoming.cors() != null ? incoming.cors() : current.cors(),
                incoming.worker() != null ? incoming.worker() : current.worker(),
                incoming.backend() != null ? incoming.backend() : current.backend(),
                incoming.aliyun() != null ? incoming.aliyun() : current.aliyun()
        );
    }

    private DatabaseConfig mergeDb(DatabaseConfig cur, DatabaseConfig inc, boolean mergeSecrets) {
        if (inc == null) return cur;
        String pwd = inc.password();
        if (mergeSecrets && (pwd == null || pwd.isBlank())) {
            pwd = cur != null ? crypto.decrypt(cur.password()) : "";
        } else if (pwd != null && !pwd.isBlank() && !pwd.startsWith("***")) {
            pwd = crypto.encrypt(pwd);
        } else if (cur != null) {
            pwd = cur.password();
        }
        return new DatabaseConfig(
                inc.url() != null ? inc.url() : cur != null ? cur.url() : "",
                inc.username() != null ? inc.username() : cur != null ? cur.username() : "",
                pwd
        );
    }

    private RedisConfig mergeRedis(RedisConfig cur, RedisConfig inc, boolean mergeSecrets) {
        if (inc == null) return cur;
        String pwd = inc.password();
        if (mergeSecrets && (pwd == null || pwd.isBlank())) {
            pwd = cur != null ? cur.password() : "";
        } else if (pwd != null && !pwd.isBlank() && !pwd.startsWith("***")) {
            pwd = crypto.encrypt(pwd);
        } else if (cur != null) {
            pwd = cur.password();
        }
        return new RedisConfig(
                inc.host() != null ? inc.host() : cur != null ? cur.host() : "127.0.0.1",
                inc.port() != null ? inc.port() : cur != null ? cur.port() : 6379,
                pwd,
                inc.queueKey() != null ? inc.queueKey() : cur != null ? cur.queueKey() : "xjicloud:jobs"
        );
    }

    private OssConfig mergeOss(OssConfig cur, OssConfig inc, boolean mergeSecrets) {
        if (inc == null) return cur;
        String sk = inc.secretKey();
        if (mergeSecrets && (sk == null || sk.isBlank())) {
            sk = cur != null ? cur.secretKey() : "";
        } else if (sk != null && !sk.isBlank() && !sk.startsWith("***")) {
            sk = crypto.encrypt(sk);
        } else if (cur != null) {
            sk = cur.secretKey();
        }
        return new OssConfig(
                inc.endpoint() != null ? inc.endpoint() : cur != null ? cur.endpoint() : "",
                inc.region() != null ? inc.region() : cur != null ? cur.region() : "us-east-1",
                inc.bucket() != null ? inc.bucket() : cur != null ? cur.bucket() : "xjicloud",
                inc.accessKey() != null ? inc.accessKey() : cur != null ? cur.accessKey() : "",
                sk,
                inc.pathStyleAccess() != null ? inc.pathStyleAccess() : cur != null && cur.pathStyleAccess(),
                inc.presignExpirationMinutes() != null ? inc.presignExpirationMinutes() : 120
        );
    }

    private RuntimeConfigDocument defaultConfig() {
        return new RuntimeConfigDocument(
                new DatabaseConfig("jdbc:postgresql://127.0.0.1:5432/xjicloud", "xjicloud", crypto.encrypt("change-me")),
                new RedisConfig("127.0.0.1", 6379, crypto.encrypt(""), "xjicloud:jobs"),
                new OssConfig("http://127.0.0.1:9000", "us-east-1", "xjicloud", "minioadmin", crypto.encrypt("minioadmin"), true, 120),
                new StorageConfig("/data/xjicloud"),
                new JwtConfig("replace-with-a-long-random-secret-at-least-32-characters", 86400000L),
                new CorsConfig("http://127.0.0.1:5174"),
                new WorkerConfig("change-me-worker-shared-secret", 60, 25),
                new BackendConfig(properties.backendUrl()),
                new AliyunConfig(
                        properties.aliyun() != null ? properties.aliyun().accessKeyId() : null,
                        properties.aliyun() != null ? properties.aliyun().accessKeySecret() : null,
                        properties.aliyun() != null ? properties.aliyun().regionId() : "cn-hangzhou",
                        properties.aliyun() != null ? properties.aliyun().vpcId() : null,
                        properties.aliyun() != null ? properties.aliyun().vSwitchId() : null,
                        properties.aliyun() != null ? properties.aliyun().securityGroupId() : null,
                        properties.aliyun() != null ? properties.aliyun().containerImage() : null
                )
        );
    }

    private String serialize(RuntimeConfigDocument doc) {
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            throw new BusinessException("配置序列化失败");
        }
    }

    private RuntimeConfigDocument deserialize(String json) {
        try {
            return objectMapper.readValue(json, RuntimeConfigDocument.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("配置解析失败");
        }
    }

    public record RuntimeConfigSnapshot(int revision, Instant updatedAt, RuntimeConfigDocument config) {}
}

@Entity
@Table(name = "runtime_config_state")
class RuntimeConfigState {
    @Id
    private String id;
    private int revision;
    @jakarta.persistence.Column(length = 16384)
    private String payloadJson;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

@Repository
interface RuntimeConfigStateRepository extends JpaRepository<RuntimeConfigState, String> {}
