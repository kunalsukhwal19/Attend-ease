
package com.attendance;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/attendance")
public class AttendanceServlet extends HttpServlet {

    private boolean isLoggedIn(HttpSession session) {
        return session != null
                && session.getAttribute("employeeId") instanceof Integer;
    }

    private boolean isAdmin(HttpSession session) {
        return isLoggedIn(session)
                && "ADMIN".equals(session.getAttribute("role"));
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Cache-Control",
                "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        HttpSession session = request.getSession(false);

        if (!isLoggedIn(session)) {
            response.sendRedirect(
                    request.getContextPath() + "/login.html");
            return;
        }

        // Attendance history is available to admins only.
        if (!isAdmin(session)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Only administrators can access attendance management.");
            return;
        }

        response.setContentType("text/html;charset=UTF-8");

        String filterDate = request.getParameter("date");

        if (filterDate != null && !filterDate.isBlank()) {
            try {
                LocalDate.parse(filterDate);
            } catch (java.time.DateTimeException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid date. Please select a valid date.");
                return;
            }
        }

        String csrfToken = CsrfUtil.getToken(request);

        try (Connection con = DBConnection.getConnection();
             PrintWriter out = response.getWriter()) {

            String contextPath = request.getContextPath();

            out.println("<!DOCTYPE html><html lang='en'><head>");
            out.println("<meta charset='UTF-8'>");
            out.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
            out.println("<title>Attendance | AttendEase</title>");
            out.println("<link rel='stylesheet' href='"
                    + contextPath + "/css/style.css'>");
            out.println("</head><body><main class='container'>");

            out.println("<h1>Attendance Management</h1>");
            out.println("<p><a href='" + contextPath
                    + "/dashboard'>Back to Dashboard</a></p>");

            out.println("<h2>Mark Attendance</h2>");
            out.println("<form action='" + contextPath
                    + "/attendance' method='post'>");

            out.println("<input type='hidden' name='csrfToken' value='"
                    + escapeHtml(csrfToken) + "'>");

            out.println("<label>Employee</label>");
            out.println("<select name='employeeId' required>");
            out.println("<option value=''>Select employee</option>");

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT id, name FROM employees "
                    + "WHERE account_enabled = TRUE ORDER BY name");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    out.println("<option value='" + rs.getInt("id") + "'>"
                            + escapeHtml(rs.getString("name"))
                            + "</option>");
                }
            }

            out.println("</select>");

            out.println("<label>Date</label>");
            out.println("<input type='date' name='attendanceDate' value='"
                    + LocalDate.now()
                    + "' required>");

            out.println("<label>Status</label>");
            out.println("<select name='status' required>");
            out.println("<option value='PRESENT'>Present</option>");
            out.println("<option value='ABSENT'>Absent</option>");
            out.println("</select>");

            out.println("<button type='submit'>Save Attendance</button>");
            out.println("</form>");

            out.println("<hr>");
            out.println("<h2>Attendance History</h2>");

            out.println("<form action='" + contextPath
                    + "/attendance' method='get'>");
            out.println("<label>Filter by date</label>");
            out.println("<input type='date' name='date' value='"
                    + (filterDate == null ? "" : escapeHtml(filterDate))
                    + "'>");
            out.println("<button type='submit'>Filter</button>");
            out.println(" <a href='" + contextPath
                    + "/attendance'>Clear Filter</a>");
            out.println("</form>");

            String sql = """
                    SELECT a.id, e.name, a.attendance_date, a.status
                    FROM attendance a
                    JOIN employees e ON a.employee_id = e.id
                    """;

            if (filterDate != null && !filterDate.isBlank()) {
                sql += " WHERE a.attendance_date = ?";
            }

            sql += " ORDER BY a.attendance_date DESC, e.name ASC";

            out.println("<table><thead><tr>");
            out.println("<th>ID</th><th>Employee</th>");
            out.println("<th>Date</th><th>Status</th>");
            out.println("</tr></thead><tbody>");

            try (PreparedStatement ps = con.prepareStatement(sql)) {

                if (filterDate != null && !filterDate.isBlank()) {
                    ps.setDate(1, java.sql.Date.valueOf(
                            LocalDate.parse(filterDate)));
                }

                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = false;

                    while (rs.next()) {
                        found = true;

                        out.println("<tr>");
                        out.println("<td>" + rs.getInt("id") + "</td>");
                        out.println("<td>"
                                + escapeHtml(rs.getString("name"))
                                + "</td>");
                        out.println("<td>"
                                + rs.getDate("attendance_date")
                                + "</td>");
                        out.println("<td>"
                                + escapeHtml(rs.getString("status"))
                                + "</td>");
                        out.println("</tr>");
                    }

                    if (!found) {
                        out.println("<tr><td colspan='4'>"
                                + "No attendance records found."
                                + "</td></tr>");
                    }
                }
            }

            out.println("</tbody></table>");
            out.println("</main></body></html>");

        } catch (SQLException e) {
            throw new ServletException(
                    "Unable to load attendance history.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        response.setHeader("Cache-Control",
                "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        HttpSession session = request.getSession(false);

        if (!isLoggedIn(session)) {
            response.sendRedirect(
                    request.getContextPath() + "/login.html");
            return;
        }

        // Enforce authorization on the server, not just in the UI.
        if (!isAdmin(session)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Only administrators can mark attendance.");
            return;
        }

        if (!CsrfUtil.isValid(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Invalid or missing CSRF token. Reload the page and try again.");
            return;
        }

        String employeeIdText = request.getParameter("employeeId");
        String dateText = request.getParameter("attendanceDate");
        String status = request.getParameter("status");

        if (employeeIdText == null || dateText == null
                || status == null || employeeIdText.isBlank()
                || dateText.isBlank() || status.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Required attendance fields are missing.");
            return;
        }

        try {
            int employeeId = Integer.parseInt(employeeIdText);
            LocalDate date = LocalDate.parse(dateText);

            if (employeeId <= 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid employee ID.");
                return;
            }

            if (!"PRESENT".equals(status)
                    && !"ABSENT".equals(status)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid attendance status.");
                return;
            }

            String sql = """
                    INSERT INTO attendance
                        (employee_id, attendance_date, status)
                    SELECT id, ?, ?
                    FROM employees
                    WHERE id = ? AND account_enabled = TRUE
                    ON DUPLICATE KEY UPDATE status = ?
                    """;

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setDate(1, java.sql.Date.valueOf(date));
                ps.setString(2, status);
                ps.setInt(3, employeeId);
                ps.setString(4, status);

                int affected = ps.executeUpdate();

                if (affected == 0) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Employee not found or account is disabled.");
                    return;
                }
            }

            response.sendRedirect(
                    request.getContextPath()
                            + "/attendance?success=1");

        } catch (NumberFormatException
                 | java.time.DateTimeException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid employee ID or date.");
        } catch (SQLException e) {
            throw new ServletException(
                    "Unable to save attendance.", e);
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
