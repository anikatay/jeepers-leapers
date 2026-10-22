package com.neueda.leap.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** The one rule for turning jwt.shared-secret into key bytes, used to both sign and verify tokens. */
public final class JwtSecret {

    private JwtSecret() {}

    /** A Base64 secret is decoded; any other text (such as the .env.example value) is used as is. */
    public static byte[] keyBytes(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
