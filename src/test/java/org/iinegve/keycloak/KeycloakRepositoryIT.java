package org.iinegve.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Ints;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import lombok.SneakyThrows;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.AuthorizationResponse;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.io.BaseEncoding.base64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@QuarkusTest
@QuarkusTestResource(KeycloakTestResource.class)
class KeycloakRepositoryIT {

  @Inject
  ObjectMapper json;

  @Inject
  KeycloakRepository keycloakRepo;


  @ConfigProperty(name = "app.keycloak.server")
  String keycloakServer;

  @ConfigProperty(name = "app.keycloak.user.realm")
  String keycloakUserRealm;

  @ConfigProperty(name = "app.keycloak.admin.client.id")
  String adminClient;


  @Test
  void createUser__successfully_creates_new_user() {
    keycloakRepo.createUser("test-username", "test-password");

    List<UserRepresentation> users = KeycloakTestResource.keycloak.realm(keycloakUserRealm).users().search("test-username", true);
    UserRepresentation kkUser = users.get(0);
    assertThat(kkUser.getUsername()).isEqualTo("test-username");
    assertThat(kkUser.isEnabled()).isTrue();
  }

  @Test
  void signIn__successfully_authenticates__and_create_session__when_credentials_are_correct() {
    List<Map<String, String>> sessionsBeforeSignIn = keycloakFor("alice", "alice").getClientSessionStats();
    AuthorizationResponse auth = keycloakRepo.signIn("alice", "alice");
    List<Map<String, String>> sessionsAfterSignIn = keycloakFor("alice", "alice").getClientSessionStats();

    // Check sessions before and after.
    int activeSessionsBeforeSignIn = findActiveSessionsForClient(sessionsBeforeSignIn, "security");
    int activeSessionsAfterSignIn = findActiveSessionsForClient(sessionsAfterSignIn, "security");
    assertThat(activeSessionsAfterSignIn).isEqualTo(activeSessionsBeforeSignIn + 1);

    // Check JWT access token
    assertThat(auth.getToken()).isNotEmpty();
    Map<String, Object> accessTokenBody = toMap(base64().decode(auth.getToken().split("\\.")[1]));
    assertThat(accessTokenBody).containsEntry("email_verified", true);
    assertThat(accessTokenBody).containsEntry("preferred_username", "alice");

    // Check JWT refresh token
    assertThat(auth.getRefreshToken()).isNotEmpty();
    Map<String, Object> refreshTokenBody = toMap(base64().decode(auth.getRefreshToken().split("\\.")[1]));
    assertThat(refreshTokenBody).containsEntry("typ", "Refresh");
  }

  @Test
  void signIn__throws__when_credentials_are_wrong() {
    Throwable ce = catchThrowable(() -> keycloakRepo.signIn("wrong", "wrong"));
    assertThat(ce).hasMessageContaining("Unauthorized");
  }

  @Test
  @SneakyThrows
  void signOut__should_close_session() {
    AuthorizationResponse auth = keycloakRepo.signIn("alice", "alice");
    List<Map<String, String>> sessionsAfterSignIn = keycloakFor("alice", "alice").getClientSessionStats();
    int activeSessionsAfterSignIn = findActiveSessionsForClient(sessionsAfterSignIn, "security");

    keycloakRepo.signOut(auth.getRefreshToken());

    List<Map<String, String>> sessionsAfterSignOut = keycloakFor("alice", "alice").getClientSessionStats();
    int activeSessionsAfterSignOut = findActiveSessionsForClient(sessionsAfterSignOut, "security");
    assertThat(activeSessionsAfterSignOut).isEqualTo(activeSessionsAfterSignIn - 1);
  }

  /*
   * Client session stats is a List of Maps with these kind of values
   * //  "offline" -> "0"
   * //  "clientId" -> "security"
   * //  "active" -> "2"
   * //  "id" -> "04a640e5-6f85-4a20-8ecc-9a5cd6d2aee7"
   *
   * 'active' is number of active sessions for particular clientId.
   * If there are no values for clientId, then it means there are no active sessions
   */
  private int findActiveSessionsForClient(List<Map<String, String>> sessionsStats, String clientId) {
    Map<String, String> stats = sessionsStats.stream()
      .filter(stat -> stat.get("clientId").equals(clientId))
      .findFirst()
      .orElse(new HashMap<>());

    return stats.get("active") == null ? 0 : Ints.tryParse(stats.get("active"));
  }

  @Test
  void setNewPasswords__updates_password() {
    AuthorizationResponse oldPassAuth = keycloakRepo.signIn("bob", "bob");
    keycloakRepo.setNewPassword("bob", "newPassword");

    Throwable unauthorized = catchThrowable(() -> keycloakRepo.signIn("bob", "bob"));
    AuthorizationResponse newPassAuth = keycloakRepo.signIn("bob", "newPassword");

    assertThat(unauthorized).hasMessageContaining("Unauthorized");
    assertThat(oldPassAuth.getToken()).isNotEqualTo(newPassAuth.getToken());
  }

  private RealmResource keycloakFor(String username, String password) {
    return KeycloakBuilder.builder()
      .serverUrl(keycloakServer)
      .realm(keycloakUserRealm)
      .clientId(adminClient)
      .username(username)
      .password(password)
      .build()
      .realm(keycloakUserRealm);
  }

  @SneakyThrows
  private Map<String, Object> toMap(byte[] jsonValue) {
    return json.readValue(jsonValue, new TypeReference<>() {});
  }
}