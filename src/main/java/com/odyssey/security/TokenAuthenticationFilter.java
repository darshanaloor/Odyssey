package com.odyssey.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final TokenAuthService tokenAuthService;

    public static final String TOKEN_ATTRIBUTE = "ODYSSEY_AUTH_TOKEN";
    public static final String HEADER_AUTH_TOKEN = "X-Auth-Token";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && !token.isBlank()) {
            Authentication auth = tokenAuthService.getAuthentication(token);
            if (auth != null) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);

                request.setAttribute(TOKEN_ATTRIBUTE, token);
                response.setHeader(HEADER_AUTH_TOKEN, token);
            }
        }

        filterChain.doFilter(request, response);
    }

    public static String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(HEADER_AUTH_TOKEN);
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }

        return null;
    }
}
