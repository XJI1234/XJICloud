package com.xjicloud.eci.provider;

import java.util.Map;

public record CreateSpec(
        String name,
        String image,
        double cpu,
        double memory,
        Map<String, String> env
) {
}
