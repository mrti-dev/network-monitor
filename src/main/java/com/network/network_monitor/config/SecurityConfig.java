package com.network.network_monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Security configuration.
 *
 * <h3>Phân quyền endpoint</h3>
 * <table border="1">
 *   <tr><th>Endpoint</th><th>ADMIN</th><th>OPERATOR</th><th>VIEWER</th></tr>
 *   <tr><td>GET /devices, /alerts (đọc)</td><td>✓</td><td>✓</td><td>✓</td></tr>
 *   <tr><td>GET /devices/metrics/**</td><td>✓</td><td>✓</td><td>✓</td></tr>
 *   <tr><td>GET /devices/new, /devices/edit/**</td><td>✓</td><td>✓</td><td>✗</td></tr>
 *   <tr><td>POST /devices/save</td><td>✓</td><td>✓</td><td>✗</td></tr>
 *   <tr><td>POST /devices/toggle/**</td><td>✓</td><td>✓</td><td>✗</td></tr>
 *   <tr><td>POST /devices/delete/**</td><td>✓</td><td>✗</td><td>✗</td></tr>
 *   <tr><td>POST /api/discovery/scan</td><td>✓</td><td>✓</td><td>✗</td></tr>
 * </table>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AppUserDetailsService userDetailsService;

    public SecurityConfig(AppUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF: bật cho form web và Fetch (CookieCsrfTokenRepository JS-friendly)
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
            )
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // Static resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Login & Register pages
                .requestMatchers("/login", "/login/**", "/register", "/register/**").permitAll()
                // Đọc — tất cả vai trò đã đăng nhập
                .requestMatchers(HttpMethod.GET,
                        "/", "/devices", "/devices/metrics/**",
                        "/alerts", "/alerts/**").hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                // Form create/edit — ADMIN và OPERATOR
                .requestMatchers(HttpMethod.GET,
                        "/devices/new", "/devices/edit/**").hasAnyRole("ADMIN", "OPERATOR")
                // Lưu thiết bị — ADMIN và OPERATOR
                .requestMatchers(HttpMethod.POST, "/devices/save").hasAnyRole("ADMIN", "OPERATOR")
                // Toggle monitoring — ADMIN và OPERATOR
                .requestMatchers(HttpMethod.POST, "/devices/toggle/**").hasAnyRole("ADMIN", "OPERATOR")
                // Xóa — chỉ ADMIN
                .requestMatchers(HttpMethod.POST, "/devices/delete/**").hasRole("ADMIN")
                // Discovery API — ADMIN và OPERATOR
                .requestMatchers(HttpMethod.POST, "/api/discovery/scan").hasAnyRole("ADMIN", "OPERATOR")
                // Tất cả request khác cần đăng nhập
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/devices", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .permitAll()
            )
            // Không expose stack trace trong error response
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            );

        return http.build();
    }
}
