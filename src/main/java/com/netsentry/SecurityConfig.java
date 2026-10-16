package com.netsentry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {
        var uploadRequest = PathPatternRequestMatcher.pathPattern(
                HttpMethod.POST, "/api/v1/upload-calendar");

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll()
                        .requestMatchers(uploadRequest).permitAll()
                        .anyRequest().authenticated()
                )
                // The public upload API accepts multipart requests without a session CSRF token.
                .csrf(csrf -> csrf.ignoringRequestMatchers(uploadRequest))
                .oauth2Login(oauth -> oauth.defaultSuccessUrl("/api/v1/successful-message", true))
                .oauth2Client(Customizer.withDefaults());

        return http.build();
    }
}
