package org.iinegve.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import lombok.SneakyThrows;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import java.util.Map;

/**
 * A lot to read is here: https://www.n-k.de/2019/12/testcontainers-keycloak.html
 * Quarkus, testcontainers, lib versions compatibility matrix:
 * https://awesomeopensource.com/project/dasniko/testcontainers-keycloak
 */
public class KeycloakTestResource implements QuarkusTestResourceLifecycleManager {

  private final String dockerImageName = "jboss/keycloak:12.0.2";
  private final KeycloakContainer keycloakContainer = new KeycloakContainer(dockerImageName);

  public static Keycloak keycloak;

  @Override
  @SneakyThrows
  public Map<String, String> start() {
    keycloakContainer
      .withRealmImportFile("/keycloak.conf.json")
      .start();

    String authServerUrl = keycloakContainer.getAuthServerUrl();
    String usersRealm = "quarkus";

    this.keycloak = KeycloakBuilder.builder()
      .serverUrl(authServerUrl)
      .realm("master")
      .clientId("admin-cli")
      .username("admin")
      .password("admin")
      .build();

    return Map.of(
      "quarkus.oidc.auth-server-url", authServerUrl + "/realms/" + usersRealm,
      "quarkus.oidc.client-id","security",
      "quarkus.oidc.credentials.secret", "4250ebe3-df65-47aa-9960-fbe1373468f0",
      "app.keycloak.server", authServerUrl,
      "app.keycloak.admin.realm", "master",
      "app.keycloak.admin.name", "admin",
      "app.keycloak.admin.password", "admin",
      "app.keycloak.admin.client.id", "admin-cli",
      "app.keycloak.user.realm", usersRealm
    );
  }

  @Override
  public void stop() {
    keycloakContainer.stop();
  }
}
