
package com.attendance;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/edit-employee")
public class EditEmployeeServlet extends HttpServlet {

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null
                && session.getAttribute("employeeId") instanceof Integer
                && "ADMIN".equals(session.getAttribute("role"));
    }

    private void denyAccess(HttpServletRequest request,
                            HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null
                || session.getAttribute("employeeId") == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.html");
        } else {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Only administrators can edit employees.");
        }
    }

    private Integer parseEmployeeId(String value) {
        if (value == null || !value.matches("[1-9]\\d{0,9}")) {
            return null;
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        if (!isAdmin(request)) {
            denyAccess(request, response);
            return;
        }

        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        Integer employeeId =
                parseEmployeeId(request.getParameter("id"));

        if (employeeId == null) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid employee ID.");
            return;
        }

        String sql = """
                SELECT id, name, email, department, job_role
                FROM employees
                WHERE id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, employeeId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    response.sendError(
                            HttpServletResponse.SC_NOT_FOUND,
                            "Employee not found.");
                    return;
                }

                String name = result.getString("name");
                String email = result.getString("email");
                String department = result.getString("department");
                String jobRole = result.getString("job_role");

                renderForm(
                        request, response, employeeId,
                        name, email, department, jobRole, null);
            }

        } catch (SQLException e) {
            getServletContext().log(
                    "Unable to load employee for editing.", e);

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load employee details.");
        }
    }

    private void renderForm(HttpServletRequest request,
                            HttpServletResponse response,
                            int employeeId,
                            String name,
                            String email,
                            String department,
                            String jobRole,
                            String errorMessage)
            throws IOException {

        String contextPath = request.getContextPath();
        String csrfToken = CsrfUtil.getToken(request);

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang='en'><head>");
            out.println("<meta charset='UTF-8'>");
            out.println("<meta name='viewport' "
                    + "content='width=device-width, initial-scale=1.0'>");
            out.println("<title>Edit Employee | AttendEase</title>");
            out.println("<link rel='stylesheet' href='"
                    + contextPath + "/css/style.css'>");
            out.println("</head><body>");

            out.println("<main class='container'>");
            out.println("<h1>Edit Employee</h1>");
            out.println("<p>Update the employee's details below.</p>");

            if (errorMessage != null) {
                out.println("<p role='alert'>"
                        + escapeHtml(errorMessage) + "</p>");
            }

            out.println("<form action='" + contextPath
                    + "/edit-employee' method='post'>");

            out.println("<input type='hidden' name='csrfToken' value='"
                    + escapeHtml(csrfToken) + "'>");

            out.println("<input type='hidden' name='id' value='"
                    + employeeId + "'>");

            out.println("<label for='name'>Name</label>");
            out.println("<input id='name' type='text' name='name' "
                    + "maxlength='100' required value='"
                    + escapeHtml(name) + "'>");

            out.println("<label for='email'>Email (login ID)</label>");
            out.println("<input id='email' type='email' name='email' "
                    + "maxlength='150' required value='"
                    + escapeHtml(email) + "'>");

            out.println("<label for='department'>Department</label>");
            out.println("<input id='department' type='text' "
                    + "name='department' maxlength='100' required value='"
                    + escapeHtml(department) + "'>");

            out.println("<label for='jobRole'>Job Role</label>");
            out.println("<input id='jobRole' type='text' "
                    + "name='jobRole' maxlength='100' required value='"
                    + escapeHtml(jobRole) + "'>");

            out.println("<button type='submit'>Save Changes</button>");
            out.println("</form>");

            out.println("<p><a href='" + contextPath
                    + "/employees'>Cancel and return to employees</a></p>");

            out.println("</main></body></html>");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException {

        if (!isAdmin(request)) {
            denyAccess(request, response);
            return;
        }

        request.setCharacterEncoding("UTF-8");

        if (!CsrfUtil.isValid(request)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Invalid or missing CSRF token. Refresh and try again.");
            return;
        }

        Integer employeeId =
                parseEmployeeId(request.getParameter("id"));

        if (employeeId == null) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid employee ID.");
            return;
        }

        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String department = request.getParameter("department");
        String jobRole = request.getParameter("jobRole");

        if (name == null || email == null
                || department == null || jobRole == null) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "All fields are required.");
            return;
        }

        name = name.trim();
        email = email.trim();
        department = department.trim();
        jobRole = jobRole.trim();

        if (name.isEmpty() || email.isEmpty()
                || department.isEmpty() || jobRole.isEmpty()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "All fields are required.");
            return;
        }

        if (name.length() > 100 || email.length() > 150
                || department.length() > 100 || jobRole.length() > 100) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "One or more fields exceed the allowed length.");
            return;
        }

        String sql = """
                UPDATE employees
                SET name = ?,
                    email = ?,
                    department = ?,
                    job_role = ?
                WHERE id = ?
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, department);
            statement.setString(4, jobRole);
            statement.setInt(5, employeeId);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated == 0) {
                response.sendError(
                        HttpServletResponse.SC_NOT_FOUND,
                        "Employee not found.");
                return;
            }

            response.sendRedirect(
                    request.getContextPath()
                            + "/employees?updatedId=" + employeeId);

        } catch (SQLException e) {
            getServletContext().log(
                    "Unable to update employee details.", e);

            if ("23000".equals(e.getSQLState())) {
                response.sendError(
                        HttpServletResponse.SC_CONFLICT,
                        "That email address may already belong to another employee.");
            } else {
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could not update employee. Check the server logs.");
            }
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
