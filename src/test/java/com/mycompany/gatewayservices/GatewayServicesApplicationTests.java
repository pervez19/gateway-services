package com.mycompany.gatewayservices;

import com.mycompany.gatewayservices.configuration.RedisHashComponent;
import com.mycompany.gatewayservices.constant.AppConstant;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gateway routing through service discovery without real Eureka/Redis/Config Server:
 * a SimpleDiscoveryClient stands in for Eureka (same DiscoveryClient contract that lb:// uses).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.cloud.loadbalancer.cache.enabled=false",
        "spring.cloud.gateway.httpclient.connect-timeout=1000",
        "applications.jwtConfig.prefix=Bearer",
        // same CORS settings as config-server's gateway-services.yml
        "spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-origin-patterns=http://localhost:[*],http://127.0.0.1:[*]",
        "spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-methods=GET,POST,PUT,PATCH,DELETE,OPTIONS",
        "spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-headers=*",
        "spring.cloud.gateway.globalcors.cors-configurations.[/**].allow-credentials=true",
        "spring.cloud.gateway.globalcors.cors-configurations.[/**].max-age=3600"
})
class GatewayServicesApplicationTests {

    // Throwaway RSA key pair per test run; never put a real key in tests.
    static final KeyPair KEYS = Keys.keyPairFor(SignatureAlgorithm.RS256);
    static final String API_KEY = "343C-ED0B-4137-B27E";

    static final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    static final Map<String, String> lastUsernameHeader = new ConcurrentHashMap<>();
    static final HttpServer auth1 = backend("auth-1");
    static final HttpServer auth2 = backend("auth-2");
    static final int deadPort = freePort();

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry r) {
        String p = "spring.cloud.discovery.client.simple.instances.";
        r.add(p + "authentication-services[0].uri", () -> "http://localhost:" + auth1.getAddress().getPort());
        r.add(p + "authentication-services[1].uri", () -> "http://localhost:" + auth2.getAddress().getPort());
        r.add(p + "authentication-services[2].uri", () -> "http://localhost:" + deadPort); // crashed instance
        r.add("applications.jwtConfig.publicKey", () -> Encoders.BASE64.encode(KEYS.getPublic().getEncoded()));
        r.add(p + "product-services[0].uri", () -> "http://localhost:" + deadPort);         // only instance is down
    }

    @MockBean
    RedisHashComponent redis;

    @MockBean
    StringRedisTemplate stringRedis;

    @SuppressWarnings("unchecked")
    final ValueOperations<String, String> revocations = mock(ValueOperations.class);

    @Autowired
    WebTestClient client;

    @BeforeEach
    void setUp() {
        hits.clear();
        lastUsernameHeader.clear();
        when(redis.hashValues(any())).thenReturn(List.of());
        when(stringRedis.opsForValue()).thenReturn(revocations);
        when(revocations.multiGet(anyList())).thenReturn(Arrays.asList(null, null));
        when(redis.hashGet(eq(AppConstant.RECORD_KEY), eq(API_KEY)))
                .thenReturn(Map.of("key", API_KEY, "services", List.of(AppConstant.AUTHENTICATION_SERVICE_KEY)));
    }

    @AfterAll
    static void stop() {
        auth1.stop(0);
        auth2.stop(0);
    }

    @Test
    void loadBalancesAcrossInstancesAndFailsOverFromDeadOne() {
        for (int i = 0; i < 9; i++) {
            client.get().uri("/api/v1/auth/signin").header(AppConstant.GATEWAY_KEY, API_KEY)
                    .exchange().expectStatus().isOk();
        }
        assertThat(hits.get("auth-1").get()).isGreaterThan(0);
        assertThat(hits.get("auth-2").get()).isGreaterThan(0);
        assertThat(hits.get("auth-1").get() + hits.get("auth-2").get()).isEqualTo(9);
    }

    @Test
    void securedRouteForwardsIdentityFromJwt() {
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice"))
                .exchange().expectStatus().isOk();
        assertThat(lastUsernameHeader.get("value")).isEqualTo("alice");
    }

    @Test
    void securedRouteWithoutJwtIsRejected() {
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void browserPreflightForSignupIsAllowed() {
        client.options().uri("/api/v1/auth/signup")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "gatewaykey,content-type,authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000");
        assertThat(hits).isEmpty(); // answered by the gateway, never proxied
    }

    @Test
    void crossOriginCallGetsExactlyOneAllowOriginHeader() {
        client.get().uri("/api/v1/auth/signin").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header("Origin", "http://localhost:3000")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values("Access-Control-Allow-Origin",
                        v -> assertThat(v).containsExactly("http://localhost:3000"));
    }

    @Test
    void unknownOriginIsRefused() {
        client.options().uri("/api/v1/auth/signup")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void refreshTokenEndpointIsReachableWithoutAccessToken() {
        // Not 401 = no JWT required. (May be 503: round-robin can pick the fixture's dead instance and POST isn't retried.)
        client.post().uri("/api/v1/auth/refresh-token").header(AppConstant.GATEWAY_KEY, API_KEY)
                .exchange().expectStatus().value(status -> assertThat(status).isIn(200, 503));
    }

    @Test
    void logoutRequiresAccessToken() {
        client.post().uri("/api/v1/auth/logout").header(AppConstant.GATEWAY_KEY, API_KEY)
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void openPathEmbeddedInSecuredPathStillRequiresJwt() {
        client.get().uri("/api/v1/users/api/v1/auth/signin").header(AppConstant.GATEWAY_KEY, API_KEY)
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void tokenSignedWithOtherKeyIsRejected() {
        String forged = Jwts.builder().setSubject("mallory")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512)).compact();
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder().setSubject("alice")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(KEYS.getPrivate(), SignatureAlgorithm.RS256).compact();
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void permissionsEndpointIsRoutedToAuthService() {
        client.get().uri("/api/v1/permissions/1").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void spoofedIdentityHeaderIsStripped() {
        client.get().uri("/api/v1/auth/signin").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header("username", "admin")
                .exchange().expectStatus().isOk();
        assertThat(lastUsernameHeader.get("value")).isEqualTo("<none>");
    }

    @Test
    void invalidApiKeyNeverReachesBackend() {
        client.get().uri("/api/v1/auth/signin").header(AppConstant.GATEWAY_KEY, "wrong")
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void unavailableServiceReturns503() {
        when(redis.hashGet(eq(AppConstant.RECORD_KEY), eq("FA48-EF0C-427E-8CCF")))
                .thenReturn(Map.of("key", "FA48-EF0C-427E-8CCF", "services", List.of(AppConstant.PRODUCT_SERVICE_KEY)));
        client.get().uri("/api/v1/products").header(AppConstant.GATEWAY_KEY, "FA48-EF0C-427E-8CCF")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice"))
                .exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void loggedOutTokenIsRejected() {
        when(revocations.multiGet(anyList())).thenReturn(Arrays.asList("1", null));
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice"))
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void tokenOfDeletedUserIsRejected() {
        String revokedBefore = String.valueOf(System.currentTimeMillis() / 1000 + 1);
        when(revocations.multiGet(anyList())).thenReturn(Arrays.asList(null, revokedBefore));
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("alice"))
                .exchange().expectStatus().isUnauthorized();
        assertThat(hits).isEmpty();
    }

    @Test
    void oldSharedSecretTokenIsRejected() {
        String hmac = Jwts.builder().setSubject("alice")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512)).compact();
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hmac)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void refreshTokenIsRejectedAsAccessToken() {
        String refresh = Jwts.builder().setSubject("alice").claim("token_type", "refresh")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(KEYS.getPrivate(), SignatureAlgorithm.RS256).compact();
        client.get().uri("/api/v1/users/me").header(AppConstant.GATEWAY_KEY, API_KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh)
                .exchange().expectStatus().isUnauthorized();
    }

    private static String token(String user) {
        return Jwts.builder().setId(UUID.randomUUID().toString()).setSubject(user).claim("authorities", "USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(KEYS.getPrivate(), SignatureAlgorithm.RS256).compact();
    }

    private static HttpServer backend(String name) {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/", ex -> {
                hits.computeIfAbsent(name, k -> new AtomicInteger()).incrementAndGet();
                lastUsernameHeader.put("value", Optional.ofNullable(ex.getRequestHeaders().getFirst("username")).orElse("<none>"));
                if (ex.getRequestHeaders().containsKey("Origin")) { // like authentication-services' own CorsConfig
                    ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                }
                byte[] body = name.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
