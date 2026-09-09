package com.xjicloud.eci.task.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.BusinessException;
import com.xjicloud.eci.config.OssProperties;
import com.xjicloud.eci.oss.OssStorageService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 校验 {@link TaskStorageRequest}，presign PUT/GET，并构建注入容器的 OSS env。
 * <p>
 * OSS 门控：须 {@code enabled} 且 endpoint/bucket 齐全，并由活客户端（库当前钥或 {@code init()}）签发。
 * mode：{@code test} 生成 {@code eci-test/{taskId}/{fileName}}；{@code keys} 用调用方 {@code outputKey}；
 * {@code dataset} / 其它 → 400。
 * PUT contentType（一期固定，须与上传头一致）：{@code test}→{@code text/plain}；
 * {@code keys}→{@code application/octet-stream}。
 * callerEnv：先写 OSS 键，再 {@code putAll(callerEnv)}，同名键以调用方为准。
 * presign 失败：{@link IllegalStateException} 包装为 {@link BusinessException}（「OSS 未就绪」）。
 * 输出侧仅签发 PUT；输出 GET 属二期。
 */
@Service
public class TaskStorageResolver {

    private final OssProperties ossProperties;
    private final OssStorageService ossStorageService;
    private final ObjectMapper objectMapper;

    public TaskStorageResolver(
            OssProperties ossProperties,
            OssStorageService ossStorageService,
            ObjectMapper objectMapper) {
        this.ossProperties = ossProperties;
        this.ossStorageService = ossStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 storage 并返回应注入容器的完整 env（含可选 caller 覆盖）。
     *
     * @param taskId    已生成的任务 ID（test 模式拼 key、写入 {@code XJI_TASK_ID}）
     * @param storage   非 null 的 storage 块
     * @param callerEnv 请求体 env；同名键覆盖本方法写入的 OSS 键
     */
    public Map<String, String> resolveAndBuildEnv(
            UUID taskId,
            TaskStorageRequest storage,
            Map<String, String> callerEnv) {
        // 门控：未启用或缺少 endpoint/bucket 时直接 400。当前钥在活客户端上，不看 properties AK。
        if (!ossProperties.isEnabled() || !ossProperties.hasTarget()) {
            throw new BusinessException("OSS 未启用或配置不完整，无法处理 storage 请求");
        }

        String mode = storage.getMode() == null ? "" : storage.getMode().trim().toLowerCase(Locale.ROOT);
        String outputKey;
        String contentType;
        switch (mode) {
            case "test" -> {
                String fileName = StringUtils.hasText(storage.getOutputFileName())
                        ? storage.getOutputFileName().trim()
                        : "output.txt";
                outputKey = "eci-test/" + taskId + "/" + fileName;
                contentType = "text/plain";
            }
            case "keys" -> {
                if (!StringUtils.hasText(storage.getOutputKey())) {
                    throw new BusinessException("keys 模式必须提供 outputKey");
                }
                outputKey = storage.getOutputKey().trim();
                contentType = "application/octet-stream";
            }
            case "dataset" -> throw new BusinessException("dataset 模式尚未支持");
            default -> throw new BusinessException("非法的 storage.mode: " + storage.getMode());
        }

        String putUrl;
        List<WorkerImageItem> inputFiles;
        try {
            // contentType 签入 PUT URL；上传须带相同头
            putUrl = ossStorageService.presignPutUrl(outputKey, contentType);
            inputFiles = resolveInputFiles(storage.getInputKeys());
        } catch (IllegalStateException ex) {
            // 未建连 / 配置门控失败 → 业务 400，不暴露 SDK 栈
            throw new BusinessException("OSS 未就绪: " + ex.getMessage());
        }

        ResolvedTaskStorage resolved = new ResolvedTaskStorage(outputKey, putUrl, inputFiles);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("XJI_OSS_ENDPOINT", nullToEmpty(ossProperties.getEndpoint()));
        env.put("XJI_OSS_BUCKET", nullToEmpty(ossProperties.getBucket()));
        env.put("XJI_OSS_REGION", nullToEmpty(ossProperties.getRegion()));
        env.put("XJI_TASK_ID", taskId.toString());
        env.put("XJI_OUTPUT_OSS_KEY", resolved.outputKey());
        env.put("XJI_OUTPUT_UPLOAD_URL", resolved.outputUploadUrl());
        if (!resolved.inputFiles().isEmpty()) {
            try {
                env.put("XJI_INPUT_FILES_JSON", objectMapper.writeValueAsString(resolved.inputFiles()));
            } catch (JsonProcessingException e) {
                throw new BusinessException("序列化 XJI_INPUT_FILES_JSON 失败: " + e.getMessage());
            }
        }

        // 调用方 env 后写入：可覆盖上述 XJI_*（联调覆盖预签名等）
        if (callerEnv != null && !callerEnv.isEmpty()) {
            env.putAll(callerEnv);
        }
        return env;
    }

    private List<WorkerImageItem> resolveInputFiles(List<String> inputKeys) {
        if (inputKeys == null || inputKeys.isEmpty()) {
            return List.of();
        }
        List<WorkerImageItem> items = new ArrayList<>();
        for (String key : inputKeys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String ossKey = key.trim();
            items.add(new WorkerImageItem(
                    fileNameFromKey(ossKey),
                    ossKey,
                    ossStorageService.presignGetUrl(ossKey)));
        }
        return items;
    }

    /**
     * 取最后一个 {@code /} 之后的段；无斜杠或尾随斜杠时回退为完整 key（与主项目路径约定一致，仅识别 {@code /}）。
     */
    static String fileNameFromKey(String ossKey) {
        int slash = ossKey.lastIndexOf('/');
        if (slash < 0 || slash == ossKey.length() - 1) {
            return ossKey;
        }
        return ossKey.substring(slash + 1);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
