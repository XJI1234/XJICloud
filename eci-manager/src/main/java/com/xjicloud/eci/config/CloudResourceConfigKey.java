package com.xjicloud.eci.config;

/** 管理端可编辑的非密钥云资源配置：内部键、环境变量名、分类、中文名称、默认值和校验类型。 */
public enum CloudResourceConfigKey {

    REGION_ID("ECI_REGION_ID", "ECI_REGION_ID", Category.NETWORK, "", "地域", "cn-hangzhou", ValueType.TEXT),
    VPC_ID("ECI_VPC_ID", "ECI_VPC_ID", Category.NETWORK, "", "VPC", "", ValueType.TEXT),
    VSWITCH_ID("ECI_VSWITCH_ID", "ECI_VSWITCH_ID", Category.NETWORK, "", "交换机 ID", "", ValueType.TEXT),
    SECURITY_GROUP_ID("ECI_SECURITY_GROUP_ID", "ECI_SECURITY_GROUP_ID", Category.NETWORK, "", "安全组 ID", "", ValueType.TEXT),

    OSS_ENABLED("ECI_OSS_ENABLED", "ECI_OSS_ENABLED", Category.OSS, "", "启用 OSS", "true", ValueType.BOOLEAN),
    OSS_ENDPOINT("ECI_OSS_ENDPOINT", "ECI_OSS_ENDPOINT", Category.OSS, "", "OSS 地址",
            "https://oss-cn-hangzhou-internal.aliyuncs.com", ValueType.URL),
    OSS_REGION("ECI_OSS_REGION", "ECI_OSS_REGION", Category.OSS, "", "OSS 地域", "cn-hangzhou", ValueType.TEXT),
    OSS_BUCKET("ECI_OSS_BUCKET", "ECI_OSS_BUCKET", Category.OSS, "", "OSS 存储桶", "", ValueType.TEXT),
    OSS_PATH_STYLE_ACCESS("ECI_OSS_PATH_STYLE_ACCESS", "ECI_OSS_PATH_STYLE_ACCESS", Category.OSS, "", "使用路径风格访问", "false",
            ValueType.BOOLEAN),
    OSS_PRESIGN_EXPIRATION_MINUTES("ECI_OSS_PRESIGN_EXPIRATION_MINUTES", "ECI_OSS_PRESIGN_EXPIRATION_MINUTES", Category.OSS, "",
            "预签名 URL 有效期（分钟）", "1440", ValueType.POSITIVE_INTEGER),

    DEFAULT_IMAGE("ECI_DEFAULT_IMAGE", "ECI_DEFAULT_IMAGE", Category.ADVANCED, "镜像与共享内存", "默认镜像", "", ValueType.TEXT),
    DEFAULT_SHM_SIZE("ECI_DEFAULT_SHM_SIZE", "ECI_DEFAULT_SHM_SIZE", Category.ADVANCED, "镜像与共享内存", "默认共享内存", "",
            ValueType.SHM_SIZE),
    CREATE_TIMEOUT_SEC("ECI_CREATE_TIMEOUT_SEC", "ECI_CREATE_TIMEOUT_SEC", Category.ADVANCED, "创建超时", "创建超时时间（秒）", "1800",
            ValueType.POSITIVE_INTEGER),
    FAILURE_LOG_TAIL_LINES("ECI_FAILURE_LOG_TAIL_LINES", "ECI_FAILURE_LOG_TAIL_LINES", Category.ADVANCED, "失败取证与清理",
            "失败日志尾部行数", "1000", ValueType.POSITIVE_INTEGER),
    FAILURE_LOG_GRACE_SEC("ECI_FAILURE_LOG_GRACE_SEC", "ECI_FAILURE_LOG_GRACE_SEC", Category.ADVANCED, "失败取证与清理",
            "失败删除宽限时间（秒）", "90", ValueType.NON_NEGATIVE_INTEGER);

    /** 一级分类：页面分区，也是保存和恢复默认的粒度。 */
    public enum Category {
        NETWORK("network", "网络"),
        OSS("oss", "OSS"),
        ADVANCED("advanced", "高级");

        private final String id;
        private final String label;

        Category(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }
    }

    /** 文本转型和校验方式。 */
    public enum ValueType {
        TEXT,
        URL,
        BOOLEAN,
        POSITIVE_INTEGER,
        NON_NEGATIVE_INTEGER,
        SHM_SIZE
    }

    private final String key;
    private final String environmentName;
    private final Category category;
    private final String groupLabel;
    private final String displayName;
    private final String defaultValue;
    private final ValueType valueType;

    CloudResourceConfigKey(
            String key,
            String environmentName,
            Category category,
            String groupLabel,
            String displayName,
            String defaultValue,
            ValueType valueType) {
        this.key = key;
        this.environmentName = environmentName;
        this.category = category;
        this.groupLabel = groupLabel;
        this.displayName = displayName;
        this.defaultValue = defaultValue;
        this.valueType = valueType;
    }

    public String key() {
        return key;
    }

    public String environmentName() {
        return environmentName;
    }

    public Category category() {
        return category;
    }

    public String groupLabel() {
        return groupLabel;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public ValueType valueType() {
        return valueType;
    }
}
