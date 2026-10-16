
package com.attendance;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalDate;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/leave")
public class LeaveServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (!isLoggedIn(session)) {
            redirectToLogin(request, response);
            return;
        }

        String role = (String) session.getAttribute("role");
        int loggedInId = (Integer) session.getAttribute("employeeId");
        boolean isAdmin = "ADMIN".equals(role);

        String csrfToken = CsrfUtil.getToken(request);
        String contextPath = request.getContextPath();

        response.setHeader("Cache-Control",
                "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setContentType("text/html;charset=UTF-8");

        try (Connection con = DBConnection.getConnection();
             PrintWriter out = response.getWriter()) {

            out.println("<!DOCTYPE html>");
            out.println("<html lang='en'><head>");
            out.println("<meta charset='UTF-8'>");
            out.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
            out.println("<title>Leave Management | AttendEase</title>");
            out.println("<link rel='stylesheet' href='"
                    + contextPath + "/css/style.css'>");
            out.println("</head><body>");
            out.println("<main class='container'>");

            out.println("<h1>Leave Management</h1>");
            out.println("<p class='description'>Apply for leave and manage requests.</p>");

            out.println("<p><a href='" + contextPath
                    + "/dashboard'>Back to Dashboard</a></p>");

            // Logout form with CSRF protection.
            out.println("<form action='" + contextPath
                    + "/logout' method='post'>");
            out.println("<input type='hidden' name='csrfToken' value='"
                    + escapeHtml(csrfToken) + "'>");
            out.println("<button type='submit'>Log Out</button>");
            out.println("</form>");

            // Leave application form.
            out.println("<h2>Apply for Leave</h2>");
            out.println("<form action='" + contextPath
                    + "/leave' method='post'>");

            out.println("<input type='hidden' name='csrfToken' value='"
                    + escapeHtml(csrfToken) + "'>");

            // Administrators can submit requests for active employees.
            if (isAdmin) {
                out.println("<label for='employeeId'>Employee</label>");
                out.println("<select id='employeeId' name='employeeId' required>");
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
            }

            out.println("<label for='startDate'>Start date</label>");
            out.println("<input type='date' id='startDate' name='startDate' "
                    + "min='" + LocalDate.now() + "' required>");

            out.println("<label for='endDate'>End date</label>");
            out.println("<input type='date' id='endDate' name='endDate' "
                    + "min='" + LocalDate.now() + "' required>");

            out.println("<label for='reason'>Reason</label>");
            out.println("<textarea id='reason' name='reason' "
                    + "rows='4' maxlength='500' required></textarea>");

            out.println("<button type='submit'>Submit Leave Request</button>");
            out.println("</form>");

            // Leave request history.
            out.println("<hr><h2>Leave Requests</h2>");
            out.println("<table><thead><tr>");
            out.println("<th>ID</th><th>Employee</th>");
            out.println("<th>Start Date</th><th>End Date</th>");
            out.println("<th>Reason</th><th>Status</th>");

            if (isAdmin) {
                out.println("<th>Action</th>");
            }

            out.println("</tr></thead><tbody>");

            String sql = """
                    SELECT l.id, e.name, l.employee_id,
                           l.start_date, l.end_date, l.reason, l.status
                    FROM leave_requests l
                    JOIN employees e ON l.employee_id = e.id
                    WHERE (? = 'ADMIN' OR l.employee_id = ?)
                    ORDER BY l.created_at DESC
                    """;

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, role);
                ps.setInt(2, loggedInId);

                try (ResultSet rs = ps.executeQuery()) {
                    boolean found = false;

                    while (rs.next()) {
                        found = true;

                        int leaveId = rs.getInt("id");
                        String status = rs.getString("status");

                        out.println("<tr>");
                        out.println("<td>" + leaveId + "</td>");
                        out.println("<td>"
                                + escapeHtml(rs.getString("name")) + "</td>");
                        out.println("<td>"
                                + rs.getDate("start_date") + "</td>");
                        out.println("<td>"
                                + rs.getDate("end_date") + "</td>");
                        out.println("<td>"
                                + escapeHtml(rs.getString("reason")) + "</td>");

                        // Colored status badge.
                        String badgeClass;

                        switch (status) {
                            case "APPROVED":
                                badgeClass = "badge-success";
                                break;
                            case "REJECTED":
                                badgeClass = "badge-danger";
                                break;
                            case "PENDING":
                            default:
                                badgeClass = "badge-warning";
                                break;
                        }

                        out.println("<td><span class='" + badgeClass + "'>"
                                + escapeHtml(status) + "</span></td>");

                        // Only administrators can process pending requests.
                        if (isAdmin) {
                            out.println("<td>");

                            if ("PENDING".equals(status)) {
                                // Approve form.
                                out.println("<form action='" + contextPath
                                        + "/leave' method='post' "
                                        + "style='display:inline'>");

                                out.println("<input type='hidden' name='csrfToken' value='"
                                        + escapeHtml(csrfToken) + "'>");

                                out.println("<input type='hidden' name='leaveId' "
                                        + "value='" + leaveId + "'>");

                                out.println("<input type='hidden' name='action' "
                                        + "value='APPROVED'>");

                                out.println("<button type='submit'>Approve</button>");
                                out.println("</form>");

                                // Reject form.
                                out.println("<form action='" + contextPath
                                        + "/leave' method='post' "
                                        + "style='display:inline'>");

                                out.println("<input type='hidden' name='csrfToken' value='"
                                        + escapeHtml(csrfToken) + "'>");

                                out.println("<input type='hidden' name='leaveId' "
                                        + "value='" + leaveId + "'>");

                                out.println("<input type='hidden' name='action' "
                                        + "value='REJECTED'>");

                                out.println("<button type='submit'>Reject</button>");
                                out.println("</form>");
                            } else {
                                out.println("Already processed");
                            }

                            out.println("</td>");
                        }

                        out.println("</tr>");
                    }

                    if (!found) {
                        int columns = isAdmin ? 7 : 6;

                        out.println("<tr><td colspan='" + columns + "'>"
                                + "No leave requests submitted yet."
                                + "</td></tr>");
                    }
                }
            }

            out.println("</tbody></table>");
            out.println("</main></body></html>");

        } catch (SQLException e) {
            getServletContext().log(
                    "Unable to load leave requests.", e);

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to load leave requests.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (!isLoggedIn(session)) {
            redirectToLogin(request, response);
            return;
        }

        // Validate CSRF before any database modification.
        if (!CsrfUtil.isValid(request)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Invalid or missing CSRF token. Refresh the page and try again.");
            return;
        }

        String role = (String) session.getAttribute("role");
        String action = request.getParameter("action");

        // Only administrators can approve or reject requests.
        if (action != null) {

            if (!"ADMIN".equals(role)) {
                response.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Only administrators can process leave requests.");
                return;
            }

            if (!"APPROVED".equals(action)
                    && !"REJECTED".equals(action)) {
                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid leave action.");
                return;
            }

            updateLeaveStatus(request, response, action);
            return;
        }

        Integer employeeId;

        if ("ADMIN".equals(role)) {
            try {
                employeeId = Integer.parseInt(
                        request.getParameter("employeeId"));

                if (employeeId <= 0) {
                    response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "Select a valid employee.");
                    return;
                }

            } catch (NumberFormatException | NullPointerException e) {
                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Select a valid employee.");
                return;
            }

        } else if ("EMPLOYEE".equals(role)) {
            // Employees must use their own session employee ID.
            employeeId = (Integer) session.getAttribute("employeeId");

        } else {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Your account role is not permitted to apply for leave.");
            return;
        }

        String startDateText = request.getParameter("startDate");
        String endDateText = request.getParameter("endDate");
        String reason = request.getParameter("reason");

        if (startDateText == null || endDateText == null) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Start and end dates are required.");
            return;
        }

        if (reason == null || reason.isBlank()
                || reason.trim().length() > 500) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a reason of up to 500 characters.");
            return;
        }

        try {
            LocalDate startDate = LocalDate.parse(startDateText);
            LocalDate endDate = LocalDate.parse(endDateText);

            if (startDate.isBefore(LocalDate.now())
                    || endDate.isBefore(startDate)) {
                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Choose valid dates. The end date must not precede the start date.");
                return;
            }

            String sql = """
                    INSERT INTO leave_requests
                    (employee_id, start_date, end_date, reason)
                    SELECT id, ?, ?, ?
                    FROM employees
                    WHERE id = ? AND account_enabled = TRUE
                    """;

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setDate(1, java.sql.Date.valueOf(startDate));
                ps.setDate(2, java.sql.Date.valueOf(endDate));
                ps.setString(3, reason.trim());
                ps.setInt(4, employeeId);

                if (ps.executeUpdate() == 0) {
                    response.sendError(
                            HttpServletResponse.SC_BAD_REQUEST,
                            "Employee not found or account is disabled.");
                    return;
                }
            }

            response.sendRedirect(
                    request.getContextPath() + "/leave");

        } catch (DateTimeException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid date.");

        } catch (SQLException e) {
            getServletContext().log(
                    "Unable to submit leave request.", e);

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to submit leave request.");
        }
    }

    private void updateLeaveStatus(HttpServletRequest request,
                                   HttpServletResponse response,
                                   String action)
            throws ServletException, IOException {

        String leaveIdText = request.getParameter("leaveId");

        try {
            int leaveId = Integer.parseInt(leaveIdText);

            if (leaveId <= 0) {
                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid leave request ID.");
                return;
            }

            String sql = """
                    UPDATE leave_requests
                    SET status = ?
                    WHERE id = ? AND status = 'PENDING'
                    """;

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, action);
                ps.setInt(2, leaveId);

                if (ps.executeUpdate() == 0) {
                    response.sendError(
                            HttpServletResponse.SC_CONFLICT,
                            "Request not found or already processed.");
                    return;
                }
            }

            response.sendRedirect(
                    request.getContextPath() + "/leave");

        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid leave request ID.");

        } catch (SQLException e) {
            getServletContext().log(
                    "Unable to update leave status.", e);

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to update leave status.");
        }
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null
                && session.getAttribute("employeeId") instanceof Integer
                && session.getAttribute("role") instanceof String;
    }

    private void redirectToLogin(HttpServletRequest request,
                                 HttpServletResponse response)
            throws IOException {

        response.sendRedirect(
                request.getContextPath() + "/login.html");
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
