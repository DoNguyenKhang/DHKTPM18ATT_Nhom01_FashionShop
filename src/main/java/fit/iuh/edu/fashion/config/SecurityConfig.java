package fit.iuh.edu.fashion.config;

import fit.iuh.edu.fashion.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Public API endpoints - MUST BE FIRST
                        .requestMatchers("/api/auth/**").permitAll()

                        // AI Chatbot endpoints - Public access
                        .requestMatchers("/ai-chatbot").permitAll()
                        .requestMatchers("/api/ai/**").permitAll()

                        // Payment endpoints - MUST BE PUBLIC for VNPay callbacks
                        .requestMatchers("/api/payment/vnpay/callback").permitAll()
                        .requestMatchers("/api/payment/vnpay/ipn").permitAll()
                        .requestMatchers("/payment/**").permitAll()

                        // Public web pages
                        .requestMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", "/index", "/products/**", "/dashboard").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/image_product/**", "/static/**").permitAll()

                        // Public product viewing
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/product-variants/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/brands/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/product-images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/colors/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sizes/**").permitAll()

                        // Admin web pages - Allow access to HTML pages (JavaScript will check auth)
                        .requestMatchers("/admin/**").permitAll()

                        // Customer endpoints - use hasAnyAuthority with ROLE_ prefix
                        .requestMatchers("/cart", "/orders","/profile").permitAll()
                        .requestMatchers("/api/cart/**").hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers("/api/orders/my/**").hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/cancel").hasAnyAuthority("ROLE_CUSTOMER", "ROLE_ADMIN")

                        // Staff Product endpoints
                        .requestMatchers(HttpMethod.POST, "/api/products/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.POST, "/api/product-variants/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.PUT, "/api/product-variants/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.DELETE, "/api/product-variants/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.POST, "/api/product-images/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.DELETE, "/api/product-images/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.POST, "/api/brands/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.PUT, "/api/brands/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.DELETE, "/api/brands/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.POST, "/api/categories/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.POST, "/api/colors/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.PUT, "/api/colors/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.DELETE, "/api/colors/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.POST, "/api/sizes/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.PUT, "/api/sizes/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")
                        .requestMatchers(HttpMethod.DELETE, "/api/sizes/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_PRODUCT")

                        // Staff Sales endpoints
                        .requestMatchers("/api/orders/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_STAFF_SALES")

                        // Admin only
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/users/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/roles/**").hasAuthority("ROLE_ADMIN")

                        // All authenticated users
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Use setAllowedOriginPatterns to support various public tunnels
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "https://localhost:*",
            "https://*.ngrok-free.app",
            "https://*.ngrok-free.dev",
            "https://*.ngrok.io",
            "https://*.ngrok.app",
            // LocalTunnel
            "https://*.loca.lt",
            // Serveo
            "https://*.serveo.net",
            // Cloudflare Tunnel default
            "https://*.trycloudflare.com"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
