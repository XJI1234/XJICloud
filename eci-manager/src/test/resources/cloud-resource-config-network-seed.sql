-- 启动顺序回归夹具：Spring 刷新前在同一个 H2 内存库写入网络覆盖。
-- 表结构与 Hibernate 为 eci_cloud_config 生成的 DDL 一致（H2 PostgreSQL 模式）。
CREATE TABLE IF NOT EXISTS eci_cloud_config (
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(2048) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (config_key)
);

INSERT INTO eci_cloud_config (config_key, config_value, updated_at)
VALUES ('ECI_REGION_ID', 'cn-shanghai', CURRENT_TIMESTAMP);

INSERT INTO eci_cloud_config (config_key, config_value, updated_at)
VALUES ('ECI_VPC_ID', 'vpc-startup-override', CURRENT_TIMESTAMP);

INSERT INTO eci_cloud_config (config_key, config_value, updated_at)
VALUES ('ECI_VSWITCH_ID', 'vsw-startup-override', CURRENT_TIMESTAMP);

INSERT INTO eci_cloud_config (config_key, config_value, updated_at)
VALUES ('ECI_SECURITY_GROUP_ID', 'sg-startup-override', CURRENT_TIMESTAMP);
