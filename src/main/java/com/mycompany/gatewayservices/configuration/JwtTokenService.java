package com.mycompany.gatewayservices.configuration;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


@Component
public class JwtTokenService {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);
    private final JwtParser parser;

    // Verifies RS256 tokens with authentication-services' PUBLIC key: the gateway can check tokens but never mint them.
    public JwtTokenService(@Value("${applications.jwtConfig.publicKey}") String publicKey) throws GeneralSecurityException {
        this.parser = Jwts.parserBuilder()
                .setSigningKey(KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))))
                .build();
    }

    /**
     * Verifies signature and expiry in one parse and returns the claims.
     *
     * @throws ResponseStatusException 401 if the token is invalid, expired, or a refresh token
     */
    public Claims parseAccessToken(String token) {
        try {
            Claims claims = parser.parseClaimsJws(token).getBody();
            if ("refresh".equals(claims.get("token_type"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token cannot be used as access token.");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            logger.error("Invalid JWT: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is invalid.");
        }
    }
}
