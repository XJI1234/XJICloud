package com.xjicloud.job.dto;

public record PresignedUploadItem(
        String archivedName,
        String ossKey,
        String uploadUrl,
        String contentType
) {
}
