
package com.attendance;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/monthly-report")
public class MonthlyReportServlet extends HttpServlet {

    private static final String REPORT_SQL = """
            SELECT
                e.id,
                e.name,
                COUNT(CASE WHEN a.status = 'PRESENT' THEN 1 END)
                    AS present_days,
                COUNT(CASE WHEN a.status = 'ABSENT' THEN 1 END)
                    AS absent_days,
                COUNT(CASE WHEN a.status = 'LEAVE' THEN 1 END)
                    AS leave_days
            FROM employees e
            LEFT JOIN attendance a
                ON e.id = a.employee_id
                AND a.attendance_date >= ?
                AND a.attendance_date < ?
            GROUP BY e.id, e.name
            ORDER BY e.name
            """;

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        // Require an authenticated administrator.
        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute("employeeId")
                        instanceof Integer)) {

            response.sendRedirect(
                    request.getContextPath() + "/login.html");
            return;
        }

        Object roleAttribute = session.getAttribute("role");

        if (!(roleAttribute instanceof String)
                || !"ADMIN".equals(roleAttribute)) {

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "You do not have permission to access this page.");
            return;
        }

        // Reports contain employee information; do not cache them.
        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Read and validate the selected month.
        YearMonth selectedMonth;

        try {
            String monthParameter =
                    request.getParameter("month");

            if (monthParameter == null
                    || monthParameter.isBlank()) {

                selectedMonth = YearMonth.now();

            } else {
                selectedMonth = YearMonth.parse(monthParameter);
            }

        } catch (DateTimeParseException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid month. Use the month selector.");
            return;
        }

        // Route CSV requests to the export method.
        String format = request.getParameter("format");

        if ("csv".equalsIgnoreCase(format)) {
            exportCsv(selectedMonth, response);
            return;
        }

        response.setContentType("text/html;charset=UTF-8");

        LocalDate startDate = selectedMonth.atDay(1);
        LocalDate nextMonth = selectedMonth.plusMonths(1).atDay(1);

        try (Connection con = DBConnection.getConnection();
             PrintWriter out = response.getWriter()) {

            String contextPath = request.getContextPath();

            out.println("<!DOCTYPE html><html lang='en'><head>");
            out.println("<meta charset='UTF-8'>");
            out.println("<meta name='viewport' "
                    + "content='width=device-width, initial-scale=1.0'>");
            out.println("<title>Monthly Attendance Report | AttendEase</title>");

            out.println("<link rel='stylesheet' href='"
                    + contextPath + "/css/style.css'>");

            out.println("</head><body>");
            out.println("<main class='container'>");

            out.println("<h1>Monthly Attendance Report</h1>");
            out.println("<p>Review attendance totals for each employee.</p>");

            out.println("<p><a href='"
                    + contextPath
                    + "/index.html'>Back to Home</a> | "
                    + "<a href='"
                    + contextPath
                    + "/dashboard'>Dashboard</a></p>");

            // Month selector.
            out.println("<form action='"
                    + contextPath
                    + "/monthly-report' method='get'>");

            out.println("<label for='month'>Select month</label>");

            out.println("<input type='month' id='month' "
                    + "name='month' value='"
                    + selectedMonth + "' required>");

            out.println("<button type='submit'>"
                    + "Generate Report</button>");

            out.println("</form>");

            // CSV export button for the selected month.
            out.println("<form action='"
                    + contextPath
                    + "/monthly-report' method='get' "
                    + "style='margin-top: 16px;'>");

            out.println("<input type='hidden' name='month' value='"
                    + selectedMonth + "'>");

            out.println("<input type='hidden' name='format' value='csv'>");

            out.println("<button type='submit'>"
                    + "Export CSV</button>");

            out.println("</form>");

            String monthName = selectedMonth.getMonth()
                    .toString().charAt(0)
                    + selectedMonth.getMonth().toString()
                            .substring(1).toLowerCase(Locale.ROOT);

            out.println("<h2>Report for "
                    + monthName + " "
                    + selectedMonth.getYear()
                    + "</h2>");

            out.println("<table><thead><tr>");
            out.println("<th>Employee ID</th>");
            out.println("<th>Employee</th>");
            out.println("<th>Present Days</th>");
            out.println("<th>Absent Days</th>");
            out.println("<th>Leave Days</th>");
            out.println("<th>Attendance %</th>");
            out.println("</tr></thead><tbody>");

            try (PreparedStatement ps =
                         con.prepareStatement(REPORT_SQL)) {

                ps.setDate(1, java.sql.Date.valueOf(startDate));
                ps.setDate(2, java.sql.Date.valueOf(nextMonth));

                try (ResultSet rs = ps.executeQuery()) {

                    boolean found = false;

                    while (rs.next()) {
                        found = true;

                        int employeeId = rs.getInt("id");
                        String employeeName = rs.getString("name");

                        int presentDays = rs.getInt("present_days");
                        int absentDays = rs.getInt("absent_days");
                        int leaveDays = rs.getInt("leave_days");

                        int recordedDays = presentDays + absentDays;

                        double attendancePercentage =
                                recordedDays == 0
                                        ? 0.0
                                        : presentDays * 100.0
                                                / recordedDays;

                        out.println("<tr>");
                        out.println("<td>" + employeeId + "</td>");

                        out.println("<td>"
                                + escapeHtml(employeeName)
                                + "</td>");

                        out.println("<td>" + presentDays + "</td>");
                        out.println("<td>" + absentDays + "</td>");
                        out.println("<td>" + leaveDays + "</td>");

                        out.printf(
                                Locale.ROOT,
                                "<td>%.1f%%</td>%n",
                                attendancePercentage);

                        out.println("</tr>");
                    }

                    if (!found) {
                        out.println(
                                "<tr><td colspan='6'>"
                                + "No employees found. Add employees first."
                                + "</td></tr>");
                    }
                }
            }

            out.println("</tbody></table>");

            out.println("<p>Attendance percentage = "
                    + "Present days / (Present days + "
                    + "Absent days) × 100.</p>");

            out.println("<p>Approved leave and unmarked days "
                    + "are not included in the percentage.</p>");

            out.println("</main></body></html>");

        } catch (SQLException e) {

            getServletContext().log(
                    "Unable to generate monthly attendance report.",
                    e);

            throw new ServletException(
                    "Unable to generate monthly attendance report.",
                    e);
        }
    }

    private void exportCsv(YearMonth selectedMonth,
                           HttpServletResponse response)
            throws ServletException, IOException {

        LocalDate startDate = selectedMonth.atDay(1);
        LocalDate nextMonth = selectedMonth.plusMonths(1).atDay(1);

        String filename = "attendance-report-" + selectedMonth + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + filename + "\"");

        try (Connection con = DBConnection.getConnection();
             PrintWriter out = response.getWriter()) {

            // UTF-8 BOM helps spreadsheet applications recognize UTF-8.
            out.write('\uFEFF');

            out.println(
                    "Employee ID,Employee Name,Present Days,"
                    + "Absent Days,Leave Days,Attendance Percentage");

            try (PreparedStatement ps =
                         con.prepareStatement(REPORT_SQL)) {

                ps.setDate(1, java.sql.Date.valueOf(startDate));
                ps.setDate(2, java.sql.Date.valueOf(nextMonth));

                try (ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        int employeeId = rs.getInt("id");
                        String employeeName = rs.getString("name");

                        int presentDays = rs.getInt("present_days");
                        int absentDays = rs.getInt("absent_days");
                        int leaveDays = rs.getInt("leave_days");

                        int recordedDays = presentDays + absentDays;

                        double attendancePercentage =
                                recordedDays == 0
                                        ? 0.0
                                        : presentDays * 100.0
                                                / recordedDays;

                        out.print(employeeId);
                        out.print(',');
                        out.print(csvEscape(employeeName));
                        out.print(',');
                        out.print(presentDays);
                        out.print(',');
                        out.print(absentDays);
                        out.print(',');
                        out.print(leaveDays);
                        out.print(',');
                        out.printf(
                                Locale.ROOT,
                                "%.1f%%%n",
                                attendancePercentage);
                    }
                }
            }

        } catch (SQLException e) {
            getServletContext().log(
                    "Unable to export monthly attendance report.", e);

            if (!response.isCommitted()) {
                throw new ServletException(
                        "Unable to export monthly attendance report.", e);
            }
        }
    }

    // Escape CSV fields containing commas, quotes, or newlines.
    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }

        // Reduce the risk of spreadsheet formula injection.
        String safeValue = value;
        String trimmed = safeValue.stripLeading();

        if (!trimmed.isEmpty()
                && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            safeValue = "'" + safeValue;
        }

        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    // Escape employee names before inserting them into HTML.
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
