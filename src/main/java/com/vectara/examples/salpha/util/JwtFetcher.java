package com.vectara.examples.salpha.util;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.vectara.StatusProtos.StatusCode;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A helper that retrieves a JSON Web Token from a authorization code grant. Most of the details for how to format the
 * HTTP request can be found at:
 * <p>
 * https://aws.amazon.com/blogs/mobile/understanding-amazon-cognito-user-pool-oauth-2-0-grants/
 */
public class JwtFetcher {
  private static final Logger LOG =
      Logger.getLogger(JwtFetcher.class.getName());
  private URI tokenEndpoint;
  private URI redirectUri;
  private String clientId;
  @Nullable
  private String clientSecret;
  private HttpClient httpClient;

  public JwtFetcher(URI authDomain, URI redirectUri, String clientId) {
    init(authDomain, redirectUri, clientId, null);
  }

  public JwtFetcher(URI authDomain, URI redirectUri, String clientId, String clientSecret) {
    init(authDomain, redirectUri, clientId, clientSecret);
  }

  /**
   * Construct a JWT fetcher for machine-to-machine authentication
   * (also known as "client credentials").
   */
  public JwtFetcher(URI authDomain, String clientId, String clientSecret) {
    init(authDomain, null, clientId, clientSecret);
  }

  private final void init(URI authDomain, @Nullable URI redirectUri, String clientId, @Nullable String clientSecret) {
    String strAuthDomain = authDomain.toASCIIString();
    if (strAuthDomain.endsWith("/oauth2/token")) {
      tokenEndpoint = asUrl(strAuthDomain);
    } else {
      if (!strAuthDomain.endsWith("/")) {
        strAuthDomain += "/";
      }
      tokenEndpoint = asUrl(strAuthDomain + "oauth2/token");
    }
    this.redirectUri = redirectUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    httpClient = HttpClient.newBuilder()
        .version(Version.HTTP_2)
        .followRedirects(Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("endpoint", tokenEndpoint)
        .add("redirect-uri", redirectUri)
        .add("client-id", clientId)
        .toString();
  }

  private URI asUrl(String url) {
    return URI.create(url);
  }

  /**
   * Use an access code to get id, access, and refresh tokens.
   */
  public StatusOr<TokenResponse> fetchAuthCode(String code) {
    return fetch("authorization_code", Collections.singletonMap("code", code));
  }

  /**
   * Use a refresh token to get id and access tokens.
   */
  public StatusOr<TokenResponse> fetchRefreshToken(String token) {
    return fetch(
        "refresh_token", Collections.singletonMap("refresh_token", token));
  }

  /**
   * Use a refresh token to get id and access tokens.
   */
  public StatusOr<TokenResponse> fetchClientCredentials() {
    return fetch("client_credentials", Maps.newHashMap());
  }

  private StatusOr<TokenResponse> fetch(
      String grantType, Map<String, String> values) {
    String x = Joiner.on("&").withKeyValueSeparator("=").join(values);
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(tokenEndpoint)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(BodyPublishers.ofString(
            String.format(
                "grant_type=%s&client_id=%s&redirect_uri=%s&%s",
                grantType, clientId, redirectUri, x
            )));
    if (clientSecret != null) {
      builder.header(
          "Authorization", "Basic " + BaseEncoding.base64().encode((clientId + ":" + clientSecret).getBytes()));
    }
    HttpRequest request = builder.build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, BodyHandlers.ofString());
      @SuppressWarnings("unchecked")
      Map<String, Object> map = new Gson().fromJson(response.body(), Map.class);
      TokenResponse tok = new TokenResponse(map);
      return tok.hasError()
          ? new StatusOr<>(StatusCode.FAILURE, tok.getError())
          : new StatusOr<>(tok);
    } catch (IOException | InterruptedException e) {
      return new StatusOr<>(e);
    }
  }
}
