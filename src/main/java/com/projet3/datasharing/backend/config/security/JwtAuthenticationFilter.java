package com.projet3.datasharing.backend.config.security;

import com.projet3.datasharing.backend.config.logging.LogKeys;
import com.projet3.datasharing.backend.repository.UserAccountRepository;
import com.projet3.datasharing.backend.service.JWTService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticate the queries presenting a JWT Bearer. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JWTService jwtService;
  private final UserAccountRepository userAccountRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");

    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      log.atDebug()
          .addKeyValue(LogKeys.EVENT, "jwt_authentication_filter")
          .addKeyValue(LogKeys.USER_ID, "no_user_required")
          .log("No Bearer token found in the request, proceeding without authentication.");
      filterChain.doFilter(request, response);
      return;
    }

    String subject;
    try {
      subject =
          jwtService.validateAndExtractSubject(authorization.substring(BEARER_PREFIX.length()));
    } catch (IllegalArgumentException exception) {
      SecurityContextHolder.clearContext();
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
      return;
    }

    if (userAccountRepository.findByEmail(subject).isEmpty()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT account no longer exists");
      return;
    }

    UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken.authenticated(subject, null, List.of());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    filterChain.doFilter(request, response);
  }
}
