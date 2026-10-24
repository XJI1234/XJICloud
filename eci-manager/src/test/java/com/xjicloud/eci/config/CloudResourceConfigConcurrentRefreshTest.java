package com.xjicloud.eci.config;

import com.xjicloud.eci.credential.AliyunResource;
import com.xjicloud.eci.credential.RuntimeCredentialService;
import com.xjicloud.eci.oss.OssStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eci.mock=true",
        "eci.api-key=test-api-key",
        "ECI_OSS_ENABLED=true",
        "ECI_OSS_ENDPOINT=https://oss-cn-hangzhou-internal.aliyuncs.com",
        "ECI_OSS_REGION=cn-hangzhou",
        "ECI_OSS_BUCKET=env-bucket",
        "ECI_OSS_PATH_STYLE_ACCESS=false",
        "ECI_OSS_PRESIGN_EXPIRATION_MINUTES=1440",
        "eci.oss.access-key=LTAIconcurrentold",
        "eci.oss.secret-key=concurrent-old-test-secret",
        "spring.datasource.url=jdbc:h2:mem:eci-config-concurrent-refresh;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CloudResourceConfigConcurrentRefreshTest {

    private static final String CONFIG_PATH = "/api/v1/eci/runtime-config";
    private static final String API_KEY = "test-api-key";
    private static final String OLD_KEY = "LTAIconcurrentold";
    private static final String NEW_KEY = "LTAIconcurrentnew";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CloudResourceConfigService configService;

    @Autowired
    private OssProperties ossProperties;

    @Autowired
    private RuntimeCredentialService credentialService;

    @SpyBean
    private OssStorageService ossStorageService;

    @BeforeEach
    void prepareOldRuntime() throws Exception {
        mockMvc.perform(delete(CONFIG_PATH + "/oss").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk());
        putCredential(OLD_KEY);
        saveOss("before-bucket", 15);
    }

    @ParameterizedTest(name = "restore={0}: prepared old key cannot overwrite credential refresh")
    @ValueSource(booleans = {false, true})
    void configPreparationSerializesCredentialRefresh(boolean restore) throws Exception {
        CountDownLatch candidatePrepared = new CountDownLatch(1);
        CountDownLatch releaseConfig = new CountDownLatch(1);
        List<Running<?>> calls = new ArrayList<>();
        doAnswer(invocation -> {
            // 保留真实构造，暂停在旧钥已被候选捕获、尚未提交的位置。
            Object prepared = invocation.callRealMethod();
            candidatePrepared.countDown();
            awaitRelease(releaseConfig);
            return prepared;
        }).when(ossStorageService).prepare(any(CloudResourceConfigSnapshot.Oss.class), anyString(), anyString());

        try {
            Running<Void> config = start("config-writer", calls, () -> {
                changeConfig(restore);
                return null;
            });
            assertThat(candidatePrepared.await(10, SECONDS)).as("candidate reached real prepare boundary").isTrue();
            Running<Void> credential = start("credential-writer", calls, () -> {
                putCredential(NEW_KEY);
                return null;
            });

            assertBlockedBy(credential, config);
            releaseConfig.countDown();
            config.result();
            credential.result();

            assertPublished(restore, NEW_KEY);
        } finally {
            releaseConfig.countDown();
            joinAll(calls);
        }
    }

    @ParameterizedTest(name = "restore={0}: readers wait for complete committed publication")
    @ValueSource(booleans = {false, true})
    void committedPublicationHidesPartiallyAppliedRuntime(boolean restore) throws Exception {
        CountDownLatch publicationEntered = new CountDownLatch(1);
        CountDownLatch releasePublication = new CountDownLatch(1);
        List<Running<?>> calls = new ArrayList<>();
        doAnswer(invocation -> {
            // 生产顺序已投影属性但尚未替换 OSS 客户端与快照，读者必须等待发布结束。
            publicationEntered.countDown();
            awaitRelease(releasePublication);
            return invocation.callRealMethod();
        }).when(ossStorageService).applyRuntime(any(OssStorageService.PreparedClients.class));

        try {
            Running<Void> config = start("publishing-config", calls, () -> {
                changeConfig(restore);
                return null;
            });
            assertThat(publicationEntered.await(10, SECONDS)).as("afterCommit reached publication boundary").isTrue();

            Running<Void> get = start("config-get-reader", calls, () -> {
                assertGet(restore);
                return null;
            });
            Running<Void> properties = start("properties-reader", calls, () -> {
                assertProperties(restore);
                return null;
            });
            Running<String> presign = start("presign-reader", calls,
                    () -> ossStorageService.presignPutUrl("datasets/concurrent/a.txt", "text/plain"));

            assertBlockedBy(get, config);
            assertBlockedBy(properties, config);
            assertBlockedBy(presign, config);
            releasePublication.countDown();
            config.result();
            get.result();
            properties.result();
            assertPresign(presign.result(), restore, OLD_KEY);
            assertPublished(restore, OLD_KEY);
        } finally {
            releasePublication.countDown();
            joinAll(calls);
        }
    }

    private void changeConfig(boolean restore) throws Exception {
        if (restore) {
            mockMvc.perform(delete(CONFIG_PATH + "/oss").header("X-Api-Key", API_KEY))
                    .andExpect(status().isOk());
        } else {
            saveOss("after-bucket", 45);
        }
    }

    private void saveOss(String bucket, int minutes) throws Exception {
        mockMvc.perform(put(CONFIG_PATH + "/oss").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ECI_OSS_BUCKET\":\"" + bucket
                                + "\",\"ECI_OSS_PRESIGN_EXPIRATION_MINUTES\":\"" + minutes + "\"}"))
                .andExpect(status().isOk());
    }

    private void putCredential(String accessKeyId) throws Exception {
        mockMvc.perform(put("/api/v1/eci/runtime-credentials").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\":\"OSS\",\"accessKeyId\":\"" + accessKeyId
                                + "\",\"accessKeySecret\":\"concurrent-test-secret-" + accessKeyId + "\"}"))
                .andExpect(status().isOk());
    }

    private void assertPublished(boolean restore, String accessKeyId) throws Exception {
        CloudResourceConfigSnapshot snapshot = configService.snapshot();
        assertThat(snapshot.oss().bucket()).isEqualTo(bucket(restore));
        assertThat(snapshot.oss().presignExpirationMinutes()).isEqualTo(minutes(restore));
        assertThat(snapshot.values().get("ECI_OSS_BUCKET").source())
                .isEqualTo(restore ? "environment" : "database");
        assertProperties(restore);
        assertGet(restore);
        assertThat(credentialService.currentPair(AliyunResource.OSS).accessKeyId()).isEqualTo(accessKeyId);
        assertThat(credentialService.currentPair(AliyunResource.OSS).accessKeySecret())
                .isEqualTo("concurrent-test-secret-" + accessKeyId);
        assertPresign(ossStorageService.presignPutUrl("datasets/concurrent/a.txt", "text/plain"), restore, accessKeyId);
    }

    private void assertProperties(boolean restore) {
        assertThat(ossProperties.getBucket()).isEqualTo(bucket(restore));
        assertThat(ossProperties.getPresignExpirationMinutes()).isEqualTo(minutes(restore));
        assertThat(ossProperties.isEnabled()).isTrue();
        assertThat(ossProperties.getEndpoint()).isEqualTo("https://oss-cn-hangzhou-internal.aliyuncs.com");
        assertThat(ossProperties.getRegion()).isEqualTo("cn-hangzhou");
        assertThat(ossProperties.isPathStyleAccess()).isFalse();
    }

    private void assertGet(boolean restore) throws Exception {
        mockMvc.perform(get(CONFIG_PATH).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oss.fields[3].value").value(bucket(restore)))
                .andExpect(jsonPath("$.data.oss.fields[3].source").value(restore ? "environment" : "database"))
                .andExpect(jsonPath("$.data.oss.fields[5].value").value(String.valueOf(minutes(restore))));
    }

    private static void assertPresign(String url, boolean restore, String accessKeyId) {
        URI uri = URI.create(url);
        assertThat(uri.getHost()).isEqualTo(bucket(restore) + ".oss-cn-hangzhou-internal.aliyuncs.com");
        assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
                .contains("X-Amz-Credential=" + accessKeyId + "/")
                .contains("X-Amz-Expires=" + minutes(restore) * 60 + "&");
        assertThat(uri.getPath()).isEqualTo("/datasets/concurrent/a.txt");
    }

    private static String bucket(boolean restore) {
        return restore ? "env-bucket" : "after-bucket";
    }

    private static int minutes(boolean restore) {
        return restore ? 1440 : 45;
    }

    private static void awaitRelease(CountDownLatch release) throws InterruptedException {
        assertThat(release.await(30, SECONDS)).as("test released paused real method").isTrue();
    }

    private static void assertBlockedBy(Running<?> waiting, Running<?> owner) {
        // 观测实际 monitor owner，不以延时或 Future 尚未完成来推断竞争已发生。
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(waiting.thread().getId());
            assertThat(info).as(waiting.thread().getName() + " is alive").isNotNull();
            assertThat(info.getThreadState()).isEqualTo(Thread.State.BLOCKED);
            assertThat(info.getLockOwnerId()).isEqualTo(owner.thread().getId());
            assertThat(waiting.task().isDone()).isFalse();
        });
    }

    private static <T> Running<T> start(String name, List<Running<?>> calls, Callable<T> action) {
        FutureTask<T> task = new FutureTask<>(action);
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        Running<T> running = new Running<>(thread, task);
        calls.add(running);
        thread.start();
        return running;
    }

    private static void joinAll(List<Running<?>> calls) throws InterruptedException {
        for (Running<?> call : calls) {
            call.thread().join(10_000);
            assertThat(call.thread().isAlive()).as(call.thread().getName() + " terminated").isFalse();
        }
    }

    private record Running<T>(Thread thread, FutureTask<T> task) {
        T result() throws Exception {
            return task.get(10, SECONDS);
        }
    }
}
