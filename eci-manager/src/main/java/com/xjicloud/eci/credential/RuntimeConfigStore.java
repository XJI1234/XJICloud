package com.xjicloud.eci.credential;

import java.util.Optional;

/**
 * 运行时配置 KV（当前云钥、HTTP Api-Key 等）。测试用内存实现，生产用 JPA。
 */
public interface RuntimeConfigStore {

    boolean exists(String key);

    Optional<String> get(String key);

    void put(String key, String value);

    void delete(String key);
}
