package com.tradetracker.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
public class InfrastructureConfig {

    // ── AWS S3 / MinIO ────────────────────────────────────────────────────────

    @Bean
    public S3Client s3Client(
            @Value("${aws.s3.endpoint}")   String endpoint,
            @Value("${aws.s3.region}")     String region,
            @Value("${aws.s3.access-key}") String accessKey,
            @Value("${aws.s3.secret-key}") String secretKey) {

        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)          // Required for MinIO — uses path-style URLs
            .build();
    }

    // ── RestClient (shared builder for all HTTP clients) ───────────────────────

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    // ── Jackson ───────────────────────────────────────────────────────────────

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Redis cache manager with per-cache TTLs ────────────────────────────────

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory factory,
            ObjectMapper objectMapper) {

        var serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        var valueSerializer = RedisSerializationContext.SerializationPair.fromSerializer(serializer);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(valueSerializer)
            .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(Map.of(
                "portfolios",   defaults.entryTtl(Duration.ofMinutes(5)),
                "holdings",     defaults.entryTtl(Duration.ofMinutes(5)),
                "prices",       defaults.entryTtl(Duration.ofMinutes(10)),
                "performance",  defaults.entryTtl(Duration.ofHours(6)),
                "cgt-summary",  defaults.entryTtl(Duration.ofHours(12))
            ))
            .build();
    }

    // ── CORS ──────────────────────────────────────────────────────────────────

    @Bean
    public CorsFilter corsFilter(
            @Value("${FRONTEND_URL:http://localhost:3000}") String frontendUrl) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    // ── OpenAPI / Swagger ─────────────────────────────────────────────────────

    @Bean
    public OpenAPI openApi(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {

        String issuerUri = jwkSetUri.replace("/protocol/openid-connect/certs", "");
        String authUrl = issuerUri + "/protocol/openid-connect/auth";
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";

        return new OpenAPI()
            .info(new Info()
                .title("TradeTracker API")
                .description("Open-source portfolio tracker — Sharesight alternative")
                .version("0.1.0"))
            .components(new Components()
                .addSecuritySchemes("keycloak", new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                            .authorizationUrl(authUrl)
                            .tokenUrl(tokenUrl)))))
            .addSecurityItem(new SecurityRequirement().addList("keycloak"));
    }
}
