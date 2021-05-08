package org.iinegve.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.keycloak.authorization.client.util.HttpResponseException;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;

@Slf4j
@ApplicationScoped
public class KeycloakRepository {

  private final ObjectMapper json;
  private final Keycloak keycloak;
  private final AuthzClient keycloakAuth;

  private final OkHttpClient http;
  private final String keycloakUri;
  private final String keycloakRealm;
  private final String keycloakClientId;
  private final String keycloakSecret;

  public KeycloakRepository(
    ObjectMapper json,

    @ConfigProperty(name = "app.keycloak.server") String server,
    @ConfigProperty(name = "app.keycloak.admin.realm") String adminRealm,
    @ConfigProperty(name = "app.keycloak.admin.name") String adminUsername,
    @ConfigProperty(name = "app.keycloak.admin.password") String adminPassword,
    @ConfigProperty(name = "app.keycloak.admin.client.id") String adminClientId,

    @ConfigProperty(name = "app.keycloak.user.realm") String userRealm,
    @ConfigProperty(name = "app.keycloak.user.client.id") String userClientId,
    @ConfigProperty(name = "app.keycloak.user.credentials.secret") String userSecret
  ) {
    this.json = json;
    this.http = new OkHttpClient();

    this.keycloakUri = server;
    this.keycloakRealm = userRealm;
    this.keycloakClientId = userClientId;
    this.keycloakSecret = userSecret;

    this.keycloak = KeycloakBuilder.builder()
      .serverUrl(server)
      .realm(adminRealm)
      .clientId(adminClientId)
      .username(adminUsername)
      .password(adminPassword)
      .build();

    Configuration config = new Configuration();
    config.setRealm(userRealm);
    config.setAuthServerUrl(server);
    config.setResource(userClientId);
    config.setCredentials(Map.of("secret", userSecret));
    this.keycloakAuth = AuthzClient.create(config);
  }

  public void createUser(String username, String password) {
    CredentialRepresentation credential = new CredentialRepresentation();
    credential.setType(CredentialRepresentation.PASSWORD);
    credential.setValue(password);
    credential.setTemporary(false);

    UserRepresentation user = new UserRepresentation();
    user.setUsername(username);
    user.setCredentials(List.of(credential));
    user.setEnabled(true);

    try (Response response = keycloak.realm(keycloakRealm).users().create(user)) {
      if (response.getStatus() != 201) {
        String responseMessage = response.readEntity(String.class);
        log.error("Cannot create user in keycloak: status '{}', message '{}'",
          response.getStatus(), responseMessage);
        throw new RuntimeException("Cannot create user");
      }
    }
  }

  public AuthorizationResponse signIn(String username, String password) {
    try {
      AuthorizationResource authResource = keycloakAuth.authorization(username, password);
      return authResource.authorize();
    } catch (Exception ex) {
      log.error("Unable to authorize:", ex);
      if (ex.getCause() instanceof HttpResponseException) {
        if (((HttpResponseException) ex.getCause()).getStatusCode() == 401) {
          throw new RuntimeException("Unauthorized", ex);
        }
      }
      throw new RuntimeException(ex);
    }
  }

  public void signOut(String refreshToken) {
    String uri = keycloakUri + "/realms/" + keycloakRealm + "/protocol/openid-connect/logout";
    Request request = new Request.Builder()
      .url(uri)
      .post(new FormBody.Builder()
        .add("client_id", keycloakClientId)
        .add("client_secret", keycloakSecret)
        .add("refresh_token", refreshToken)
        .build()
      )
      .build();

    try (okhttp3.Response rsp = call(request)) {
      int code = rsp.code();
      if (code != SC_NO_CONTENT) {
        throw new RuntimeException("Unable to logout");
      }
    }
  }

  // Response must be closed afterwards
  @SneakyThrows
  private okhttp3.Response call(Request request) {
    return http.newCall(request).execute();
  }

  public void setNewPassword(String keycloakUsername, String newPassword) {
    UserRepresentation user = findKeycloakUser(keycloakUsername);
    doSetNewPassword(user.getId(), newPassword);
  }

  private UserRepresentation findKeycloakUser(String username) {
    List<UserRepresentation> users = keycloak.realm(keycloakRealm).users().search(username, true);
    if (users.isEmpty()) {
      throw new RuntimeException("User not found");
    }
    return users.get(0);
  }

  private void doSetNewPassword(String keycloakUserId, String newPassword) {
    AccessTokenResponse adminAccessToken = keycloak.tokenManager().getAccessToken();
    String uri = String.format("%s/admin/realms/%s/users/%s/reset-password",
      keycloakUri, keycloakRealm, keycloakUserId
    );
    RequestBody requestBody = RequestBody.create(
      MediaType.parse("application/json; charset=utf-8"),
      toJson(Map.of("temporary", false, "value", newPassword, "type", "password"))
    );
    Request request = new Request.Builder()
      .url(uri)
      .put(requestBody)
      .header(AUTHORIZATION, "Bearer " + adminAccessToken.getToken())
      .build();

    try (okhttp3.Response rsp = call(request)) {
      int code = rsp.code();
      if (code != SC_NO_CONTENT) {
        throw new RuntimeException("Unable to set new password");
      }
    }
  }

  @SneakyThrows
  private String toJson(Object val) {
    return json.writeValueAsString(val);
  }
}
