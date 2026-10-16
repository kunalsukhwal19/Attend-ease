
package com.attendance;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;

public class SetupPassword {

    public static void main(String[] args) throws Exception {

        Console console = System.console();

        if (console == null) {
            System.out.println(
                "Please run this program directly from Terminal."
            );
            return;
        }

        String email = console.readLine("Enter the account email: ");

        if (email == null || email.isBlank()) {
            System.out.println("Email cannot be empty.");
            return;
        }

        char[] password = null;
        char[] confirmation = null;

        try {
            password = console.readPassword(
                "Create password (minimum 12 characters): "
            );

            confirmation = console.readPassword(
                "Confirm password: "
            );

            if (password == null || confirmation == null) {
                System.out.println("Password input was cancelled.");
                return;
            }

            if (password.length < 12) {
                System.out.println(
                    "Password must contain at least 12 characters."
                );
                return;
            }

            if (!MessageDigest.isEqual(
                    new String(password).getBytes(StandardCharsets.UTF_8),
                    new String(confirmation).getBytes(StandardCharsets.UTF_8))) {
                System.out.println("Passwords do not match.");
                return;
            }

            String passwordHash = PasswordUtil.hashPassword(
                new String(password)
            );

            String sql = """
                UPDATE employees
                SET password_hash = ?, account_enabled = TRUE
                WHERE email = ?
                  AND (password_hash IS NULL OR password_hash = '')
                """;

            try (Connection connection = DBConnection.getConnection();
                 PreparedStatement statement =
                     connection.prepareStatement(sql)) {

                statement.setString(1, passwordHash);
                statement.setString(2, email.trim());

                int updated = statement.executeUpdate();

                if (updated == 1) {
                    System.out.println(
                        "Password saved and account activated successfully."
                    );
                } else {
                    System.out.println(
                        "Password was not changed. Check that the email "
                        + "exists and the account does not already have "
                        + "a password."
                    );
                }
            }

        } catch (GeneralSecurityException exception) {
            System.out.println("Could not securely hash the password.");
            throw exception;
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }

            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
