
package com.attendance;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.sendRedirect(request.getContextPath() + "/login.html");
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null || password == null
                || email.isBlank() || password.isEmpty()) {

            response.sendRedirect(
                    request.getContextPath()
                            + "/login.html?error=invalid"
            );
            return;
        }

        String sql = """
                SELECT id, name, email, password_hash,
                       account_role, account_enabled
                FROM employees
                WHERE email = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, email.trim());

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    invalidLogin(request, response);
                    return;
                }

                int employeeId = result.getInt("id");
                String name = result.getString("name");
                String storedHash = result.getString("password_hash");
                String role = result.getString("account_role");
                boolean enabled = result.getBoolean("account_enabled");

                boolean passwordMatches =
                        storedHash != null
                        && PasswordUtil.verifyPassword(
                                password, storedHash
                        );

                if (!enabled || !passwordMatches) {
                    invalidLogin(request, response);
                    return;
                }

                HttpSession oldSession = request.getSession(false);

                if (oldSession != null) {
                    oldSession.invalidate();
                }

                HttpSession session = request.getSession(true);
                session.setAttribute("employeeId", employeeId);
                session.setAttribute("employeeName", name);
                session.setAttribute("employeeEmail", email.trim());
                session.setAttribute("role", role);

                // Automatically expire inactive sessions after 30 minutes.
                session.setMaxInactiveInterval(30 * 60);

                response.sendRedirect(
                        request.getContextPath() + "/dashboard"
                );
            }

        } catch (Exception e) {
            getServletContext().log("Login failed due to a server error.", e);
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to process login right now."
            );
        }
    }

    private void invalidLogin(HttpServletRequest request,
                              HttpServletResponse response)
            throws IOException {

        response.sendRedirect(
                request.getContextPath() + "/login.html?error=invalid"
        );
    }
}
