package com.xjicloud.eci.task.storage;

import java.util.List;

/**
 * storage 解析结果：输出 key、上传 PUT URL、可选输入文件列表。
 * <p>
 * 一期契约：仅签发输出 PUT（{@code XJI_OUTPUT_UPLOAD_URL}），不签发输出 GET；
 * 容器写完即结束，读回属二期。
 */
public record ResolvedTaskStorage(
        String outputKey,
        String outputUploadUrl,
        List<WorkerImageItem> inputFiles
) {
}
