package com.projet3.datasharing.backend.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SpringSecurityConfiguration {

  private final CustomUserDetailService customUserDetailService;

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  @Bean
  DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(customUserDetailService);

    authProvider.setPasswordEncoder(passwordEncoder());

    return authProvider;
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
    return authConfig.getAuthenticationManager();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /*
   * @Bean
   * SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
   * return http
   * .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/register",
   * "/api/auth/login"))
   * .formLogin(AbstractHttpConfigurer::disable)
   * .httpBasic(AbstractHttpConfigurer::disable)
   * .authorizeHttpRequests(authorize -> authorize
   * .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
   * .anyRequest().authenticated())
   * .exceptionHandling(exceptionHandling -> exceptionHandling
   * .authenticationEntryPoint(unauthorizedEntryPoint()))
   * .addFilterBefore(jwtAuthenticationFilter,
   * UsernamePasswordAuthenticationFilter.class)
   * .build();
   * }
   */

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/register", "/api/auth/login", "/api/files",
            "/api/auth/files/**", "/api/files/download"))
        .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationProvider(authenticationProvider())
        .authorizeHttpRequests(authorize -> authorize
            // No auth needed on :
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers("/api/auth/register", "/api/auth/login", "/api/files", "/api/files/download").permitAll()
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
            // Others protected routes will be added here.
            .requestMatchers("/api/auth/files/**").authenticated()
            .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(
            (request, response, exception) -> {
              response.sendError(HttpServletResponse.SC_UNAUTHORIZED, exception.getMessage());
            }));
    return http.build();
  }

  @Bean
  AuthenticationEntryPoint unauthorizedEntryPoint() {
    return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
  }
}
