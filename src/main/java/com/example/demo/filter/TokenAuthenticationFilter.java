package com.example.demo.filter;

import com.example.demo.entities.User;
import com.example.demo.services.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService authService;
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    @Autowired
    public TokenAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        logger.info("Запрос на: " + request.getRequestURI());

        if (request.getRequestURI().startsWith("/login") || request.getRequestURI().startsWith("/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractTokenFromRequest(request);
        if (token != null) {
            try {
                User user = authService.authenticate(token);
                if (user != null) {
                    logger.info("Token: " + token + ", user: " + user);
                    Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.info("SecurityContext set for user: " + SecurityContextHolder.getContext().getAuthentication());
                } else {
                    logger.warning("User authentication failed.");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"message\": \"Unauthorized\", \"id\": 401}");
                    return;
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                logger.severe("Authentication error: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"message\": \"Unauthorized\", \"id\": 401}");
                return;
            }
        } else {
            logger.severe("No token found.");
            response.setHeader("Set-Cookie", "JSESSIONID=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"message\": \"Unauthorized\", \"id\": 401}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    public String extractTokenFromRequest(HttpServletRequest request) {
        String token = request.getHeader("auth-token");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
