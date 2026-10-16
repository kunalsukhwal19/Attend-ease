
package com.attendance;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/employees")
public class EmployeeServlet extends HttpServlet {

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        return session != null
                && session.getAttribute("employeeId") instanceof Integer
                && "ADMIN".equals(session.getAttribute("role"));
    }

    private void denyAccess(HttpServletRequest request,
                            HttpServletResponse response,
                            String message) throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null
                || session.getAttribute("employeeId") == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.html");
        } else {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN, message);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws IOException {

        if (!isAdmin(request)) {
            denyAccess(request, response,
                    "Only administrators can access employee management.");
            return;
        }

        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        String contextPath = request.getContextPath();
        String csrfToken = CsrfUtil.getToken(request);

        String createdId = request.getParameter("createdId");
        String updatedId = request.getParameter("updatedId");

        String search = request.getParameter("search");
        String departmentFilter = request.getParameter("department");

        search = search == null ? "" : search.trim();
        departmentFilter = departmentFilter == null
                ? "" : departmentFilter.trim();

        if (search.length() > 150) {
            search = search.substring(0, 150);
        }

        if (departmentFilter.length() > 100) {
            departmentFilter = "";
        }

        String employeeSql = """
                SELECT id, name, email, department, job_role
                FROM employees
                WHERE (
                    ? = ''
                    OR name LIKE ?
                    OR email LIKE ?
                    OR CAST(id AS CHAR) = ?
                )
                AND (? = '' OR department = ?)
                ORDER BY id DESC
                """;

        String departmentSql = """
                SELECT DISTINCT department
                FROM employees
                WHERE department IS NOT NULL
                  AND TRIM(department) <> ''
                ORDER BY department
                """;

        try (Connection connection = DBConnection.getConnection();
             PrintWriter out = response.getWriter()) {

            List<String> departments = new ArrayList<>();

            try (PreparedStatement departmentStatement =
                         connection.prepareStatement(departmentSql);
                 ResultSet departmentResults =
                         departmentStatement.executeQuery()) {

                while (departmentResults.next()) {
                    departments.add(
                            departmentResults.getString("department"));
                }
            }

            out.println("<!DOCTYPE html>");
            out.println("<html lang='en'><head>");
            out.println("<meta charset='UTF-8'>");
            out.println("<meta name='viewport' "
                    + "content='width=device-width, initial-scale=1.0'>");
            out.println("<title>Employees | AttendEase</title>");
            out.println("<link rel='stylesheet' href='"
                    + contextPath + "/css/style.css'>");
            out.println("</head><body>");

            out.println("<main class='container'>");
            out.println("<h1>Employee Management</h1>");
            out.println("<p>Manage your organization's employees.</p>");

            out.println("<p><a href='" + contextPath
                    + "/dashboard'>Back to Dashboard</a></p>");

            if (createdId != null && createdId.matches("\\d+")) {
                out.println("<p role='status'>Employee created successfully. "
                        + "Employee ID: <strong>"
                        + escapeHtml(createdId)
                        + "</strong>. Share the login email and initial "
                        + "password securely with the employee.</p>");
            }

            if (updatedId != null && updatedId.matches("\\d+")) {
                out.println("<p role='status'>Employee details updated "
                        + "successfully. Employee ID: <strong>"
                        + escapeHtml(updatedId)
                        + "</strong>.</p>");
            }

            // Logout form
            out.println("<form action='" + contextPath
                    + "/logout' method='post'>");

            out.println("<input type='hidden' name='csrfToken' value='"
                    + escapeHtml(csrfToken) + "'>");

            out.println("<button type='submit'>Log Out</button>");
            out.println("</form>");

            // Add employee form
            out.println("<h2>Add Employee</h2>");

            out.println("<form action='" + contextPath
                    + "/employees' method='post'>");

            out.println("<input type='hidden' name='csrfToken' value='"
                    + escapeHtml(csrfToken) + "'>");

            out.println("<label for='name'>Name</label>");
            out.println("<input id='name' type='text' name='name' "
                    + "maxlength='100' autocomplete='name' required>");

            out.println("<label for='email'>Email (login ID)</label>");
            out.println("<input id='email' type='email' name='email' "
                    + "maxlength='150' autocomplete='email' required>");

            out.println("<label for='password'>Initial Password</label>");
            out.println("<input id='password' type='password' name='password' "
                    + "minlength='12' maxlength='128' "
                    + "autocomplete='new-password' required>");

            out.println("<small>Use at least 12 characters. "
                    + "Do not reuse a personal password.</small>");

            out.println("<label for='department'>Department</label>");
            out.println("<input id='department' type='text' "
                    + "name='department' maxlength='100' required>");

            out.println("<label for='jobRole'>Job Role</label>");
            out.println("<input id='jobRole' type='text' name='jobRole' "
                    + "maxlength='100' required>");

            out.println("<button type='submit'>Create Employee</button>");
            out.println("</form>");

            // Search and department filters
            out.println("<h2>Employee List</h2>");

            out.println("<form action='" + contextPath
                    + "/employees' method='get'>");

            out.println("<label for='search'>Search employees</label>");

            out.println("<input id='search' type='search' name='search' "
                    + "maxlength='150' "
                    + "placeholder='Search by ID, name or email' "
                    + "value='" + escapeHtml(search) + "'>");

            out.println("<label for='departmentFilter'>Department</label>");
            out.println("<select id='departmentFilter' name='department'>");
            out.println("<option value=''>All Departments</option>");

            for (String department : departments) {
                out.println("<option value='" + escapeHtml(department) + "'"
                        + (department.equals(departmentFilter)
                        ? " selected" : "")
                        + ">" + escapeHtml(department) + "</option>");
            }

            out.println("</select>");
            out.println("<button type='submit'>Filter</button>");

            out.println("<a href='" + contextPath
                    + "/employees'>Clear Filters</a>");

            out.println("</form>");

            // Employee table
            try (PreparedStatement statement =
                         connection.prepareStatement(employeeSql)) {

                statement.setString(1, search);
                statement.setString(2, "%" + search + "%");
                statement.setString(3, "%" + search + "%");
                statement.setString(4, search);
                statement.setString(5, departmentFilter);
                statement.setString(6, departmentFilter);

                try (ResultSet result = statement.executeQuery()) {

                    out.println("<table><thead><tr>");
                    out.println("<th>ID</th>");
                    out.println("<th>Name</th>");
                    out.println("<th>Email</th>");
                    out.println("<th>Department</th>");
                    out.println("<th>Job Role</th>");
                    out.println("<th>Action</th>");
                    out.println("</tr></thead><tbody>");

                    boolean found = false;

                    while (result.next()) {
                        found = true;

                        int employeeId = result.getInt("id");

                        out.println("<tr>");

                        out.println("<td>" + employeeId + "</td>");

                        out.println("<td>"
                                + escapeHtml(result.getString("name"))
                                + "</td>");

                        out.println("<td>"
                                + escapeHtml(result.getString("email"))
                                + "</td>");

                        out.println("<td>"
                                + escapeHtml(result.getString("department"))
                                + "</td>");

                        out.println("<td>"
                                + escapeHtml(result.getString("job_role"))
                                + "</td>");

                        out.println("<td><a href='" + contextPath
                                + "/edit-employee?id=" + employeeId
                                + "'>Edit</a></td>");

                        out.println("</tr>");
                    }

                    if (!found) {
                        out.println("<tr><td colspan='6'>"
                                + "No matching employees found."
                                + "</td></tr>");
                    }

                    out.println("</tbody></table>");
                }
            }

            out.println("</main></body></html>");

        } catch (SQLException e) {
            getServletContext().log("Unable to load employees.", e);

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load employees.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws IOException {

        if (!isAdmin(request)) {
            denyAccess(request, response,
                    "Only administrators can add employees.");
            return;
        }

        if (!CsrfUtil.isValid(request)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Invalid or missing CSRF token. Refresh and try again.");
            return;
        }

        request.setCharacterEncoding("UTF-8");

        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String department = request.getParameter("department");
        String jobRole = request.getParameter("jobRole");

        if (name == null || email == null || password == null
                || department == null || jobRole == null
                || name.isBlank() || email.isBlank()
                || password.isEmpty()
                || department.isBlank() || jobRole.isBlank()) {

            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "All fields are required.");
            return;
        }

        name = name.trim();
        email = email.trim();
        department = department.trim();
        jobRole = jobRole.trim();

        if (name.length() > 100 || email.length() > 150
                || password.length() < 12 || password.length() > 128
                || department.length() > 100 || jobRole.length() > 100) {

            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Check field lengths. Password must be 12–128 characters.");
            return;
        }

        String passwordHash;

        try {
            passwordHash = PasswordUtil.hashPassword(password);
        } catch (GeneralSecurityException e) {
            getServletContext().log(
                    "Unable to hash the new employee password.", e);

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to create the employee account.");
            return;
        }

        String sql = """
                INSERT INTO employees
                    (name, email, department, job_role,
                     password_hash, account_role, account_enabled)
                VALUES (?, ?, ?, ?, ?, 'EMPLOYEE', TRUE)
                """;

        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, department);
            statement.setString(4, jobRole);
            statement.setString(5, passwordHash);

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    int employeeId = keys.getInt(1);

                    response.sendRedirect(
                            request.getContextPath()
                                    + "/employees?createdId=" + employeeId);
                    return;
                }
            }

            response.sendRedirect(
                    request.getContextPath() + "/employees");

        } catch (SQLException e) {
            getServletContext().log("Unable to add employee.", e);

            if ("23000".equals(e.getSQLState())) {
                response.sendError(
                        HttpServletResponse.SC_CONFLICT,
                        "This email may already belong to an employee.");
            } else {
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could not save employee. Check the server logs.");
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
