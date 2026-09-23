package com.xjicloud.eci.provider;

/** 不向调用方泄露云 SDK 请求或凭据的云操作异常。 */
public final class CloudOperationException extends RuntimeException {

    private final CloudOperationKind kind;

    public CloudOperationException(String operation, String cloudErrorCode, CloudOperationKind kind) {
        super(safeMessage(operation, cloudErrorCode));
        this.kind = kind;
    }

    public CloudOperationKind kind() {
        return kind;
    }

    private static String safeMessage(String operation, String cloudErrorCode) {
        String safeOperation = operation == null || operation.isBlank() ? "cloud operation" : operation;
        String safeCode = cloudErrorCode == null || cloudErrorCode.isBlank() ? "unknown" : cloudErrorCode;
        return safeOperation + " failed with cloud error code " + safeCode + "; outcome requires reconciliation";
    }
}
