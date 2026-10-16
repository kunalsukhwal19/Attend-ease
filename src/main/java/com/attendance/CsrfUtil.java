
package com.attendance;

import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class CsrfUtil {

    private static final String SESSION_ATTRIBUTE = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfUtil() {
        // Utility class; do not instantiate.
    }

    public static String getToken(HttpServletRequest request) {
        HttpSession session = request.getSession();

        synchronized (session) {
            String token = (String) session.getAttribute(
                    SESSION_ATTRIBUTE);

            if (token == null) {
                byte[] bytes = new byte[32];
                RANDOM.nextBytes(bytes);

                token = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes);

                session.setAttribute(SESSION_ATTRIBUTE, token);
            }

            return token;
        }
    }

    public static boolean isValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return false;
        }

        String expected = (String) session.getAttribute(
                SESSION_ATTRIBUTE);
        String actual = request.getParameter("csrfToken");

        if (expected == null || actual == null) {
            return false;
        }

        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}
