
package com.attendance;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/db-test")
public class DBTestServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        try (Connection connection = DBConnection.getConnection()) {
            response.getWriter().println(
                    "SUCCESS: Database connection is working."
            );
        } catch (SQLException e) {
            // Keep technical details out of the HTTP response.
            getServletContext().log(
                    "Database connectivity test failed.", e
            );

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );
            response.getWriter().println(
                    "Database connection is currently unavailable."
            );
        }
    }
}
