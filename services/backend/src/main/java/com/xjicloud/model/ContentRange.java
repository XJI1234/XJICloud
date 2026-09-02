package com.xjicloud.model;

import com.xjicloud.common.BusinessException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public record ContentRange(long start, long endInclusive, long total) {

    private static final Pattern PATTERN = Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");

    public static ContentRange parse(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessException("缺少上传范围", HttpStatus.BAD_REQUEST);
        }
        Matcher matcher = PATTERN.matcher(header.trim());
        if (!matcher.matches()) {
            throw new BusinessException("上传范围无效", HttpStatus.BAD_REQUEST);
        }
        long start = Long.parseLong(matcher.group(1));
        long endInclusive = Long.parseLong(matcher.group(2));
        long total = Long.parseLong(matcher.group(3));
        if (start < 0 || endInclusive < start || total <= 0 || endInclusive >= total) {
            throw new BusinessException("上传范围无效", HttpStatus.BAD_REQUEST);
        }
        return new ContentRange(start, endInclusive, total);
    }

    public long length() {
        return endInclusive - start + 1;
    }
}
