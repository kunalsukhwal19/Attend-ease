
package com.attendance;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebFilter(urlPatterns = {"/*"})
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest =
                (HttpServletRequest) request;

        HttpServletResponse httpResponse =
                (HttpServletResponse) response;

        String path = httpRequest.getServletPath();

        // Apply security headers to public and protected responses.
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("Referrer-Policy", "no-referrer");
        httpResponse.setHeader(
                "Cache-Control", "no-store, no-cache, must-revalidate");
        httpResponse.setHeader("Pragma", "no-cache");

        // Allow login resources without an authenticated session.
        boolean publicResource =
                "/login.html".equals(path)
                || "/login".equals(path)
                || path.startsWith("/css/")
                || path.startsWith("/images/")
                || "/favicon.ico".equals(path);

        if (publicResource) {
            chain.doFilter(request, response);
            return;
        }

        // All other routes require an authenticated session.
        HttpSession session = httpRequest.getSession(false);

        boolean loggedIn = session != null
                && session.getAttribute("employeeId") instanceof Integer;

        if (!loggedIn) {
            httpResponse.sendRedirect(
                    httpRequest.getContextPath() + "/login.html");
            return;
        }

        chain.doFilter(request, response);
    }
}
