package com.example.apartment.config;

import com.example.apartment.common.ApiError;
import com.example.apartment.modules.identity.AccountMapper;
import com.example.apartment.modules.identity.AccountPrincipal;
import com.example.apartment.modules.identity.IdentityService;
import com.example.apartment.modules.identity.SessionAccountFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean UserDetailsService userDetailsService(AccountMapper accounts) {
        return username -> {
            var account = accounts.findByUsername(username);
            if (account == null) throw new UsernameNotFoundException("账号或密码错误");
            return new AccountPrincipal(account);
        };
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json, IdentityService identity) throws Exception {
        return http
            .addFilterAfter(new SessionAccountFilter(identity, json), SecurityContextHolderFilter.class)
            .csrf(Customizer.withDefaults())
            .requestCache(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/identity/workspaces/*").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/password").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/identity/accounts").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/identity/accounts").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/identity/accounts/*/role", "/api/identity/accounts/*/enabled",
                        "/api/identity/accounts/*/reset-password").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/housing/buildings", "/api/housing/rooms", "/api/housing/beds").hasAnyRole("SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/housing/buildings", "/api/housing/buildings/*", "/api/housing/buildings/*/enabled",
                        "/api/housing/rooms", "/api/housing/rooms/*", "/api/housing/rooms/*/enabled",
                        "/api/housing/beds", "/api/housing/beds/*", "/api/housing/beds/*/enabled").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/housing/students").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/housing/students", "/api/housing/students/*", "/api/housing/students/*/enabled").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/housing/my-residency").hasRole("STUDENT")
                .requestMatchers(HttpMethod.GET, "/api/housing/residencies", "/api/housing/vacant-beds").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/housing/residencies", "/api/housing/residencies/*/transfer", "/api/housing/residencies/*/checkout").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/repairs/maintainers").hasAnyRole("SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/repairs/*/assign", "/api/repairs/*/reassign").hasAnyRole("SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/repairs/*/start", "/api/repairs/*/complete").hasRole("MAINTAINER")
                .requestMatchers(HttpMethod.GET, "/api/repairs", "/api/repairs/*", "/api/repairs/*/images/*").hasAnyRole("STUDENT", "SYSTEM_ADMIN", "DORM_MANAGER", "MAINTAINER")
                .requestMatchers(HttpMethod.POST, "/api/repairs").hasRole("STUDENT")
                .requestMatchers(HttpMethod.POST, "/api/repairs/*/cancel").hasAnyRole("STUDENT", "SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/hygiene/schemes", "/api/hygiene/schemes/*").hasAnyRole("SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/hygiene/schemes", "/api/hygiene/schemes/*", "/api/hygiene/schemes/*/copy", "/api/hygiene/schemes/*/activate").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/hygiene/batches", "/api/hygiene/batches/current", "/api/hygiene/batches/*", "/api/hygiene/batches/*/rooms", "/api/hygiene/batches/*/rooms/*").hasAnyRole("SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/hygiene/batches/current/prepare").hasAnyRole("SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/hygiene/batches/*/state").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/hygiene/batches/*/rooms/*/add", "/api/hygiene/batches/*/rooms/*/remove").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/hygiene/batches/*/eligible-rooms").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/hygiene/batches/*/changes").hasAnyRole("SYSTEM_ADMIN", "DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/hygiene/batches/*/rooms/*/score").hasRole("DORM_MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/hygiene/drafts", "/api/hygiene/batches/*/rooms/*/draft").hasRole("DORM_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/hygiene/batches/*/rooms/*/draft").hasRole("DORM_MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/hygiene/student/results").hasRole("STUDENT")
                .anyRequest().denyAll())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"登录成功\"}");
                })
                .failureHandler((request, response, exception) -> {
                    if (exception instanceof AuthenticationServiceException) {
                        ApiError.write(json, response, 500, "SERVER_ERROR", "服务暂时不可用，请稍后重试");
                    } else {
                        ApiError.write(json, response, 401, "INVALID_CREDENTIALS", "账号或密码错误，或账号已停用");
                    }
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .deleteCookies("APARTMENTSESSION")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    ApiError.write(json, response, 401, "UNAUTHENTICATED", "请先登录"))
                .accessDeniedHandler((request, response, exception) ->
                    ApiError.write(json, response, 403, "FORBIDDEN", "没有操作权限或安全校验已失效，请刷新后重试")))
            .build();
    }
}
