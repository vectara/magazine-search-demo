package com.vectara.examples.salpha.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.time.Duration;
import java.util.Map;

/**
 * A response from the Cognito token endpoint.
 */
public class TokenResponse {
  private DecodedJWT idToken, accessToken;
  private String refreshToken;
  private Duration expiresIn;
  private String tokenType;
  private String error;

  public TokenResponse(Map<String, Object> json) {
    if (json.containsKey("error")) {
      setError(String.valueOf(json.get("error")));
    }
    if (json.containsKey("token_type")) {
      setTokenType(String.valueOf(json.get("token_type")));
    }
    Object objExpires = json.get("expires_in");
    if (objExpires != null && objExpires instanceof Number) {
      setExpiresIn(((Number) objExpires).longValue());
    }
    if (json.containsKey("id_token")) {
      setIdToken(JWT.decode(String.valueOf(json.get("id_token"))));
    }
    if (json.containsKey("refresh_token")) {
      setRefreshToken(String.valueOf(json.get("refresh_token")));
    }
    if (json.containsKey("access_token")) {
      setAccessToken(JWT.decode(String.valueOf(json.get("access_token"))));
    }
  }

  public String getError() {
    return error;
  }

  public boolean hasError() {
    return error != null;
  }

  public TokenResponse setError(String error) {
    this.error = error;
    return this;
  }

  public TokenResponse setTokenType(String type) {
    this.tokenType = type;
    return this;
  }

  public TokenResponse setExpiresIn(long seconds) {
    expiresIn = Duration.ofSeconds(seconds);
    return this;
  }

  public TokenResponse setIdToken(DecodedJWT token) {
    this.idToken = token;
    return this;
  }

  public TokenResponse setAccessToken(DecodedJWT token) {
    this.accessToken = token;
    return this;
  }

  public TokenResponse setRefreshToken(String token) {
    this.refreshToken = token;
    return this;
  }

  public boolean hasIdToken() {
    return idToken != null;
  }

  public boolean hasAccessToken() {
    return accessToken != null;
  }

  public boolean hasRefreshToken() {
    return refreshToken != null;
  }

  public DecodedJWT getIdToken() {
    return idToken;
  }

  public DecodedJWT getAccessToken() {
    return accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public Duration getExpiresIn() {
    return expiresIn;
  }

  public String getTokenType() {
    return tokenType;
  }
}
