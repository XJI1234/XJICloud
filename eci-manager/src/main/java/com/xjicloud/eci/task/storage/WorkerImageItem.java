package com.xjicloud.eci.task.storage;

/**
 * 容器侧输入文件项，序列化为 {@code XJI_INPUT_FILES_JSON}。
 * <p>
 * 字段名与主项目 WorkerImageItem 对齐：{@code fileName}（自 key 末段）、{@code ossKey}、
 * {@code downloadUrl}（预签名 GET）。
 */
public record WorkerImageItem(String fileName, String ossKey, String downloadUrl) {
}
