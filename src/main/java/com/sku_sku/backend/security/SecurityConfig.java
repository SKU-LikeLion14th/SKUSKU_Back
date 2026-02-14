package com.sku_sku.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final CustomAuthorizationRequestResolver customAuthorizationRequestResolver;
    private final RedisOAuth2AuthorizationRequestRepository redisOAuth2AuthorizationRequestRepository; // (준하) Redis 기반
                                                                                                       // 인증 요청 저장소 추가

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults()) // cors 등록
                .httpBasic(httpBasic -> httpBasic.disable()) // 이게 올바른 문법
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestResolver(customAuthorizationRequestResolver)
                                .authorizationRequestRepository(redisOAuth2AuthorizationRequestRepository) // (준하) 세션 대신
                                                                                                           // Redis에
                                                                                                           // OAuth2 인증
                                                                                                           // 요청 저장
                        )
                        .successHandler(oAuth2SuccessHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api/auth/**").permitAll()
                        // ADMIN_LION
                        .requestMatchers("/admin/assignment/**",
                                "/admin/schedule/**",
                                "/admin/lecture/**",
                                "/admin/project/**",
                                "/admin/reviewQuiz/**")
                        .hasRole("ADMIN_LION")
                        // ALL
                        .requestMatchers("/project/all").permitAll()
                        .requestMatchers("/oauth2/redirect").permitAll()
                        .anyRequest().authenticated()
                // .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "https://sku-sku.com", "http://localhost:3000",
                "https://legacy.sku-sku.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
