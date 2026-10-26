package com.xjicloud.auth.fusion;

/** Provider boundary: clients supply opaque tokens, never a trusted phone number. */
public interface FusionAuthProvider {
    String issueAuthToken();
    String verifyPhone(String verifyToken);
}
