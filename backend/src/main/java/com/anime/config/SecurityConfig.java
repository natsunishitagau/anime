package com.anime.config;

import com.anime.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // --- Admin endpoints (most restrictive first) ---
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/stats/cleanup").hasRole("ADMIN")

                // --- Authenticated user data (user-specific) ---
                .requestMatchers("/api/watch-history/**").authenticated()
                .requestMatchers("/api/user/**").authenticated()
                .requestMatchers("/api/user-settings/**").authenticated()

                // --- Authenticated write operations (defense-in-depth) ---
                .requestMatchers(HttpMethod.POST, "/api/danmaku/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/danmaku/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/anime/*/rate").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/anime/*/review/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/anime/*/review/**").authenticated()

                // --- Public endpoints ---
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/anime/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/anime/game-sequence").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/danmaku/**").permitAll()
                .requestMatchers("/api/videos/**").permitAll()
                .requestMatchers("/api/character/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/character/game-sequence").permitAll()
                .requestMatchers("/api/stats/overview").permitAll()
                .requestMatchers("/api/stats/pv").permitAll()
                .requestMatchers("/api/stats/uv").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/assets/**").permitAll()
                .requestMatchers("/hls/**").permitAll()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://127.0.0.1:5173"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}