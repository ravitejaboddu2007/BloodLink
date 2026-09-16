package com.bloodlink.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${bloodlink.cors.allowed-origins:http://localhost:3000,http://localhost:5500,http://localhost:8080,http://127.0.0.1:5500,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins = "http://localhost:3000,http://localhost:5500,http://localhost:8080,http://127.0.0.1:5500,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173";

    @Autowired
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, String allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Unauthorized: Authentication token is missing, invalid, or expired\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Forbidden: You do not have permission to access this resource\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Allow all CORS preflight OPTIONS requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 2. Public Authentication & Health
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health").permitAll()

                // 3. Public Aggregates & Public Search queries
                .requestMatchers(HttpMethod.GET, "/api/users/stats").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/*/exists").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/donors/count/available").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/donors/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/requests/count").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bloodbanks/inventory/updates-count").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bloodbanks/*/inventory/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/bloodbanks/*/inventory").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/reviews/user/**").permitAll()

                // 4. Donor-Only Endpoints
                .requestMatchers(HttpMethod.GET, "/api/donors/*/history").hasRole("DONOR")
                .requestMatchers(HttpMethod.POST, "/api/donors/*/history").hasRole("DONOR")
                .requestMatchers(HttpMethod.PUT, "/api/donors/*/availability").hasRole("DONOR")
                .requestMatchers(HttpMethod.PUT, "/api/donors/*/location").hasRole("DONOR")
                .requestMatchers(HttpMethod.GET, "/api/donors/*/alerts").hasRole("DONOR")
                .requestMatchers(HttpMethod.PUT, "/api/donors/*/alerts/**").hasRole("DONOR")

                // 5. Hospital-Only Endpoints
                .requestMatchers(HttpMethod.GET, "/api/hospitals/*/requests").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.POST, "/api/requests").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.GET, "/api/requests/hospital/**").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.POST, "/api/requests/*/bloodbanks").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.POST, "/api/requests/*/match-bloodbanks").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.POST, "/api/requests/*/match-donors").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.POST, "/api/requests/*/increase-radius").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.POST, "/api/requests/*/alerts").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.POST, "/api/requests/*/progress-wave").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.PUT, "/api/requests/*/fulfill").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.PUT, "/api/requests/*/donors/*/confirm").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.PUT, "/api/requests/*/bloodbanks/*/confirm").hasRole("HOSPITAL")
                .requestMatchers(HttpMethod.PUT, "/api/requests/*/bloodbanks/*/reject").hasRole("HOSPITAL")

                // 6. Blood Bank-Only Endpoints
                .requestMatchers(HttpMethod.GET, "/api/bloodbanks/*/alerts").hasRole("BLOODBANK")
                .requestMatchers(HttpMethod.PUT, "/api/bloodbanks/*/alerts/**").hasRole("BLOODBANK")
                .requestMatchers(HttpMethod.PUT, "/api/bloodbanks/*/inventory").hasRole("BLOODBANK")

                // 7. Shared Authenticated Endpoints
                .requestMatchers(HttpMethod.GET, "/api/users/*").hasAnyRole("DONOR", "HOSPITAL", "BLOODBANK")
                .requestMatchers(HttpMethod.PUT, "/api/users/*").hasAnyRole("DONOR", "HOSPITAL", "BLOODBANK")
                .requestMatchers(HttpMethod.GET, "/api/requests/*").hasAnyRole("DONOR", "HOSPITAL", "BLOODBANK")
                .requestMatchers(HttpMethod.POST, "/api/reviews").hasAnyRole("DONOR", "HOSPITAL", "BLOODBANK")
                .requestMatchers(HttpMethod.DELETE, "/api/reviews/**").hasAnyRole("DONOR", "HOSPITAL", "BLOODBANK")

                // 8. Catch-all for any other protected endpoints
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
