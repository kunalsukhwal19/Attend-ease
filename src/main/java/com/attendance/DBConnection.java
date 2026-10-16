
package com.attendance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL = System.getenv()
            .getOrDefault(
                    "ATTENDANCE_DB_URL",
                    "jdbc:mysql://localhost:3306/attendance_db"
            );

    private static final String USER =
            System.getenv("ATTENDANCE_DB_USER");

    private static final String PASSWORD =
            System.getenv("ATTENDANCE_DB_PASSWORD");

    public static Connection getConnection() throws SQLException {

        if (USER == null || USER.isBlank()
                || PASSWORD == null || PASSWORD.isBlank()) {
            throw new SQLException(
                    "Database credentials are not configured."
            );
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(
                    "MySQL JDBC driver could not load", e
            );
        }

        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
