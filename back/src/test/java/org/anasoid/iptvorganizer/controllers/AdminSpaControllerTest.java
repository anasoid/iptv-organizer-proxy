package org.anasoid.iptvorganizer.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.anasoid.iptvorganizer.SQLiteTestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SQLiteTestProfile.class)
class AdminSpaControllerTest {

  @Test
  void shouldServeAdminIndexForAdminRoot() {
    given()
        .when()
        .get("/admin")
        .then()
        .statusCode(200)
        .contentType(containsString("text/html"))
        .body(containsString("data-test-admin-shell"));
  }

  @Test
  void shouldServeAdminIndexForAdminRootWithTrailingSlash() {
    given()
        .when()
        .get("/admin/")
        .then()
        .statusCode(200)
        .contentType(containsString("text/html"))
        .body(containsString("data-test-admin-shell"));
  }

  @Test
  void shouldServeAdminIndexForAdminDeepLinks() {
    given()
        .when()
        .get("/admin/sources/42")
        .then()
        .statusCode(200)
        .contentType(containsString("text/html"))
        .body(containsString("data-test-admin-shell"));
  }

  @Test
  void shouldNotTreatAssetPathsAsSpaRoutes() {
    given()
        .when()
        .get("/admin/assets/app.js")
        .then()
        .statusCode(404)
        .body(not(containsString("data-test-admin-shell")));
  }
}

