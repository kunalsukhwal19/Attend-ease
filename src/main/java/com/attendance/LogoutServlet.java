
package com.attendance;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        // Require a valid login session.
        if (session == null
                || session.getAttribute("employeeId") == null) {
            response.sendRedirect(
                    request.getContextPath() + "/login.html");
            return;
        }

        // Reject logout requests without a valid CSRF token.
        if (!CsrfUtil.isValid(request)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Invalid or missing CSRF token. Refresh the page and try again."
            );
            return;
        }

        // Invalidate the session after successful validation.
        session.invalidate();

        response.sendRedirect(
                request.getContextPath() + "/login.html?logout=success");
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
            throws ServletException, IOException {

        response.sendError(
                HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Use POST to log out."
        );
    }
}
