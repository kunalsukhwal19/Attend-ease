
package com.attendance;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("employeeId") instanceof Integer)) {
            response.sendRedirect(request.getContextPath() + "/login.html");
            return;
        }

        Object roleAttribute = session.getAttribute("role");

        if (!(roleAttribute instanceof String)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Invalid account role.");
            return;
        }

        String role = (String) roleAttribute;

        if (!"ADMIN".equals(role) && !"EMPLOYEE".equals(role)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Invalid account role.");
            return;
        }

        int employeeId = (Integer) session.getAttribute("employeeId");

        String employeeName = "User";
        Object nameAttribute = session.getAttribute("employeeName");

        if (nameAttribute instanceof String) {
            employeeName = (String) nameAttribute;
        }

        boolean isAdmin = "ADMIN".equals(role);
        String contextPath = request.getContextPath();

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        String csrfToken = CsrfUtil.getToken(request);

        String formattedDate = today.format(
                DateTimeFormatter.ofPattern(
                        "EEEE, dd MMMM yyyy", Locale.ENGLISH));

        response.setHeader(
                "Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setContentType("text/html;charset=UTF-8");

        int totalEmployees = 0;
        int presentToday = 0;
        int absentToday = 0;
        int pendingLeaves = 0;

        int myPresentDays = 0;
        int myAbsentDays = 0;
        int myLeaveDays = 0;
        int myPendingLeaves = 0;

        // Six months: current month and previous five months.
        int[] monthlyPresent = new int[6];
        int[] monthlyAbsent = new int[6];
        int[] monthlyLeave = new int[6];
        String[] monthLabels = new String[6];

        for (int i = 0; i < 6; i++) {
            YearMonth month = currentMonth.minusMonths(5 - i);
            monthLabels[i] = month.format(
                    DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH));
        }

        try (Connection con = DBConnection.getConnection()) {

            if (isAdmin) {

                String employeeSql =
                        "SELECT COUNT(*) FROM employees";

                try (PreparedStatement ps =
                             con.prepareStatement(employeeSql);
                     ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {
                        totalEmployees = rs.getInt(1);
                    }
                }

                String presentSql = """
                        SELECT COUNT(*)
                        FROM attendance
                        WHERE attendance_date = ?
                          AND status = 'PRESENT'
                        """;

                try (PreparedStatement ps =
                             con.prepareStatement(presentSql)) {

                    ps.setDate(1, java.sql.Date.valueOf(today));

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            presentToday = rs.getInt(1);
                        }
                    }
                }

                String absentSql = """
                        SELECT COUNT(*)
                        FROM attendance
                        WHERE attendance_date = ?
                          AND status = 'ABSENT'
                        """;

                try (PreparedStatement ps =
                             con.prepareStatement(absentSql)) {

                    ps.setDate(1, java.sql.Date.valueOf(today));

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            absentToday = rs.getInt(1);
                        }
                    }
                }

                String pendingSql = """
                        SELECT COUNT(*)
                        FROM leave_requests
                        WHERE status = 'PENDING'
                        """;

                try (PreparedStatement ps =
                             con.prepareStatement(pendingSql);
                     ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {
                        pendingLeaves = rs.getInt(1);
                    }
                }

                // Load monthly attendance counts for administrators.
                LocalDate startDate = currentMonth.minusMonths(5).atDay(1);
                LocalDate endDate = currentMonth.plusMonths(1).atDay(1);

                String monthlySql = """
                        SELECT
                            MONTH(attendance_date) AS month_num,
                            YEAR(attendance_date) AS year_num,
                            status,
                            COUNT(*) AS total
                        FROM attendance
                        WHERE attendance_date >= ?
                          AND attendance_date < ?
                        GROUP BY YEAR(attendance_date),
                                 MONTH(attendance_date),
                                 status
                        ORDER BY YEAR(attendance_date),
                                 MONTH(attendance_date)
                        """;

                try (PreparedStatement ps =
                             con.prepareStatement(monthlySql)) {

                    ps.setDate(1, java.sql.Date.valueOf(startDate));
                    ps.setDate(2, java.sql.Date.valueOf(endDate));

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int year = rs.getInt("year_num");
                            int month = rs.getInt("month_num");
                            String status = rs.getString("status");
                            int total = rs.getInt("total");

                            int index =
                                    (year - startDate.getYear()) * 12
                                    + month - startDate.getMonthValue();

                            if (index >= 0 && index < 6) {
                                switch (status) {
                                    case "PRESENT" ->
                                            monthlyPresent[index] = total;
                                    case "ABSENT" ->
                                            monthlyAbsent[index] = total;
                                    case "LEAVE" ->
                                            monthlyLeave[index] = total;
                                    default -> {
                                        // Ignore unexpected statuses.
                                    }
                                }
                            }
                        }
                    }
                }

            } else {

                String personalSql = """
                        SELECT
                            COUNT(CASE WHEN a.status = 'PRESENT'
                                THEN 1 END) AS present_days,
                            COUNT(CASE WHEN a.status = 'ABSENT'
                                THEN 1 END) AS absent_days,
                            COUNT(CASE WHEN a.status = 'LEAVE'
                                THEN 1 END) AS leave_days
                        FROM attendance a
                        WHERE a.employee_id = ?
                        """;

                try (PreparedStatement ps =
                             con.prepareStatement(personalSql)) {

                    ps.setInt(1, employeeId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            myPresentDays = rs.getInt("present_days");
                            myAbsentDays = rs.getInt("absent_days");
                            myLeaveDays = rs.getInt("leave_days");
                        }
                    }
                }

                String personalLeaveSql = """
                        SELECT COUNT(*)
                        FROM leave_requests
                        WHERE employee_id = ?
                          AND status = 'PENDING'
                        """;

                try (PreparedStatement ps =
                             con.prepareStatement(personalLeaveSql)) {

                    ps.setInt(1, employeeId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            myPendingLeaves = rs.getInt(1);
                        }
                    }
                }
            }

        } catch (SQLException e) {
            getServletContext().log("Unable to load dashboard.", e);
            throw new ServletException("Unable to load dashboard.", e);
        }

        try (PrintWriter out = response.getWriter()) {

            out.println("<!DOCTYPE html>");
            out.println("<html lang='en'><head>");
            out.println("<meta charset='UTF-8'>");
            out.println("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
            out.println("<title>Dashboard | AttendEase</title>");
            out.println("<link rel='stylesheet' href='"
                    + contextPath + "/css/style.css'>");

            // Chart.js library.
            if (isAdmin) {
                out.println("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>");
            }

            out.println("</head><body>");

            // Navigation.
            out.println("<header class='navbar'>");
            out.println("<h2>AttendEase</h2>");
            out.println("<div class='dashboard-nav-right'>");
            out.println("<span>" + escapeHtml(employeeName) + "</span>");
            out.println("<form class='logout-form' action='"
                    + contextPath + "/logout' method='post'>");
            out.println("<input type='hidden' name='csrfToken' value='"
                    + escapeHtml(csrfToken) + "'>");
            out.println("<button class='logout-button' type='submit'>Log Out</button>");
            out.println("</form>");
            out.println("</div>");
            out.println("</header>");

            out.println("<main class='container dashboard-container'>");

            // Welcome section.
            out.println("<section class='dashboard-hero'>");
            out.println("<div>");
            out.println("<p class='eyebrow'>"
                    + (isAdmin ? "ADMINISTRATOR PORTAL" : "EMPLOYEE PORTAL")
                    + "</p>");
            out.println("<h1>Welcome back, "
                    + escapeHtml(employeeName) + "!</h1>");
            out.println("<p class='dashboard-subtitle'>"
                    + (isAdmin
                    ? "Here's an overview of your organization's attendance and leave activity."
                    : "Here's a summary of your attendance and leave requests.")
                    + "</p>");
            out.println("</div>");
            out.println("<div class='dashboard-date'>");
            out.println("<span class='date-label'>TODAY</span>");
            out.println("<strong>" + formattedDate + "</strong>");
            out.println("</div>");
            out.println("</section>");

            // Statistics.
            out.println("<section class='dashboard-section'>");
            out.println("<div class='section-heading'><div>");
            out.println("<h2>"
                    + (isAdmin ? "Organization Overview" : "My Attendance Overview")
                    + "</h2>");
            out.println("<p>"
                    + (isAdmin
                    ? "A quick look at today's attendance and pending requests."
                    : "Your recorded attendance and current leave requests.")
                    + "</p>");
            out.println("</div></div>");
            out.println("<div class='dashboard-stats'>");

            if (isAdmin) {
                printCard(out, "Total Employees", totalEmployees,
                        "People registered", "blue");
                printCard(out, "Present Today", presentToday,
                        "Marked present today", "green");
                printCard(out, "Absent Today", absentToday,
                        "Marked absent today", "red");
                printCard(out, "Pending Leaves", pendingLeaves,
                        "Requests awaiting review", "amber");
            } else {
                printCard(out, "Present Days", myPresentDays,
                        "Recorded present days", "green");
                printCard(out, "Absent Days", myAbsentDays,
                        "Recorded absent days", "red");
                printCard(out, "Leave Days", myLeaveDays,
                        "Attendance marked as leave", "blue");
                printCard(out, "Pending Leaves", myPendingLeaves,
                        "Requests awaiting review", "amber");
            }

            out.println("</div></section>");

            // Monthly chart is administrator-only.
            if (isAdmin) {
                out.println("<section class='dashboard-section chart-section'>");
                out.println("<div class='section-heading'><div>");
                out.println("<h2>Monthly Attendance</h2>");
                out.println("<p>Attendance records across the last six months.</p>");
                out.println("</div></div>");
                out.println("<div class='attendance-chart-card'>");
                out.println("<div class='attendance-chart-container'>");
                out.println("<canvas id='attendanceChart' "
                        + "aria-label='Monthly present, absent, and leave counts' "
                        + "role='img'></canvas>");
                out.println("</div></div>");
                out.println("</section>");

                // Values are integers and labels are fixed English month names.
                out.println("<script>");
                out.println("const monthLabels = "
                        + toJsStringArray(monthLabels) + ";");
                out.println("const presentData = "
                        + toJsIntArray(monthlyPresent) + ";");
                out.println("const absentData = "
                        + toJsIntArray(monthlyAbsent) + ";");
                out.println("const leaveData = "
                        + toJsIntArray(monthlyLeave) + ";");

                out.println("""
                        const chartCanvas = document.getElementById('attendanceChart');
                        if (chartCanvas && typeof Chart !== 'undefined') {
                            new Chart(chartCanvas, {
                                type: 'bar',
                                data: {
                                    labels: monthLabels,
                                    datasets: [
                                        {
                                            label: 'Present',
                                            data: presentData,
                                            backgroundColor: '#16a34a',
                                            borderRadius: 5
                                        },
                                        {
                                            label: 'Absent',
                                            data: absentData,
                                            backgroundColor: '#dc2626',
                                            borderRadius: 5
                                        },
                                        {
                                            label: 'Leave',
                                            data: leaveData,
                                            backgroundColor: '#2563eb',
                                            borderRadius: 5
                                        }
                                    ]
                                },
                                options: {
                                    responsive: true,
                                    maintainAspectRatio: false,
                                    interaction: {
                                        intersect: false,
                                        mode: 'index'
                                    },
                                    plugins: {
                                        legend: {
                                            position: 'bottom'
                                        }
                                    },
                                    scales: {
                                        y: {
                                            beginAtZero: true,
                                            ticks: {
                                                precision: 0
                                            }
                                        }
                                    }
                                }
                            });
                        }
                        """);
                out.println("</script>");
            }

            // Quick actions.
            out.println("<section class='dashboard-section actions-section'>");
            out.println("<div class='section-heading'><div>");
            out.println("<h2>Quick Actions</h2>");
            out.println("<p>Go directly to the tasks you need.</p>");
            out.println("</div></div>");
            out.println("<div class='dashboard-actions'>");

            if (isAdmin) {
                printAction(out, contextPath, "/employees",
                        "Manage Employees",
                        "Add employees and manage employee records.", "EMPLOYEES");
                printAction(out, contextPath, "/attendance",
                        "Manage Attendance",
                        "Record and update daily attendance.", "ATTENDANCE");
                printAction(out, contextPath, "/leave",
                        "Leave Management",
                        "Review requests and manage leave applications.", "LEAVE");
                printAction(out, contextPath, "/monthly-report",
                        "Monthly Report",
                        "Review the monthly attendance report.", "REPORTS");
            } else {
                printAction(out, contextPath, "/leave",
                        "Leave Management",
                        "Apply for leave and check the status of your requests.",
                        "MY LEAVE");
            }

            out.println("</div></section>");

            out.println("<footer class='dashboard-footer'>");
            out.println("<p>AttendEase &mdash; Attendance &amp; Leave Management</p>");
            out.println("</footer>");
            out.println("</main></body></html>");
        }
    }

    private void printCard(PrintWriter out,
                           String title,
                           int value,
                           String description,
                           String color) {

        out.println("<article class='stat-card stat-" + color + "'>");
        out.println("<div class='stat-card-top'>");
        out.println("<h3>" + escapeHtml(title) + "</h3>");
        out.println("<span class='stat-indicator' aria-hidden='true'></span>");
        out.println("</div>");
        out.println("<p class='stat-value'>" + value + "</p>");
        out.println("<p class='stat-description'>"
                + escapeHtml(description) + "</p>");
        out.println("</article>");
    }

    private void printAction(PrintWriter out,
                             String contextPath,
                             String path,
                             String title,
                             String description,
                             String category) {

        out.println("<a class='dashboard-action' href='"
                + contextPath + path + "'>");
        out.println("<span class='action-category'>"
                + escapeHtml(category) + "</span>");
        out.println("<h3>" + escapeHtml(title) + "</h3>");
        out.println("<p>" + escapeHtml(description) + "</p>");
        out.println("<span class='action-link'>Open section &rarr;</span>");
        out.println("</a>");
    }

    private String toJsIntArray(int[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                result.append(",");
            }
            result.append(values[i]);
        }
        return result.append("]").toString();
    }

    private String toJsStringArray(String[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                result.append(",");
            }
            // Labels are generated internally from a fixed date format.
            result.append("'").append(values[i]).append("'");
        }
        return result.append("]").toString();
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
