package com.shortbreakshub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;
import com.shortbreakshub.security.JwtAuthFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private final JwtAuthFilter JwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.JwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/itineraries/**",
                                "/api/regions/**",
                                "/api/auth/**",
                                "/api/community-itineraries/**",
                                "/api/health"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/request-password-reset",
                                "/api/auth/reset-password",
                                "/api/auth/me/photo",
                                "/api/auth/me/renew-token",
                                "/api/community-itineraries/upload-itinerary-cover-photo",
                                "/api/community-itineraries/publish-itinerary",
                                "/api/itineraries/*/favorite",
                                "/api/itineraries/*/comments",
                                "/api/community-itineraries/*/favorite",
                                "/api/community-itineraries/*/comments",
                                "/api/community-itineraries/*/question-threads",
                                "/api/community-itineraries/*/question-threads/*/messages",
                                "/api/community-itineraries/draft/save-draft",
                                "/api/community-itineraries/draft/upload-draft-cover-photo",
                                "/api/community-itineraries/draft/update-draft-cover-photo",
                                "api/contact"

                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/auth/me/photo",
                                "/api/auth/me",
                                "/api/community-itineraries/draft/**"

                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/itineraries/*/favorite",
                                "/api/itineraries/*/comments",
                                "/api/community-itineraries/*/favorite",
                                "/api/community-itineraries/*/comments",
                                "/api/community-itineraries/draft/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                ).addFilterBefore(JwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
