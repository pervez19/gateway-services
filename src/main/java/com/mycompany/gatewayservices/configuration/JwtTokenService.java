package com.mycompany.gatewayservices.configuration;



import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;


@Component
public class JwtTokenService {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenService.class);
    @Value("${applications.jwtConfig.secret}")
    private String secret;
    private Key key;
    @PostConstruct
    private  void init(){
        byte[] keyByte= Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyByte);
    }

    public String extractUsernameFromJWT(String token) {
        return extractClaimFromToken(token,Claims::getSubject);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(key).parseClaimsJws(authToken);
            return true;
        } catch (final SignatureException ex) {
            JwtTokenService.logger.error("Invalid JWT signature");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        } catch (final MalformedJwtException ex) {
            JwtTokenService.logger.error("Invalid JWT token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        } catch (final ExpiredJwtException ex) {
            JwtTokenService.logger.error("Expired JWT token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        } catch (final UnsupportedJwtException ex) {
            JwtTokenService.logger.error("Unsupported JWT token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        } catch (final IllegalArgumentException ex) {
            JwtTokenService.logger.error("JWT claims string is empty.");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
        }
    }

    public <T> T extractClaimFromToken(String authToken, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaimsFromToken(authToken);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaimsFromToken(String authToken) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(authToken)
                .getBody();
    }
    public Boolean isTokenExpired(String authToken) {
        return extractExpirationFromToken(authToken).before(new Date());
    }

    public Date extractExpirationFromToken(String authToken) {
        return extractClaimFromToken(authToken, Claims::getExpiration);
    }
}