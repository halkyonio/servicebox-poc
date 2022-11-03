package io.halkyon;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.IsNot.not;

import javax.ws.rs.core.MediaType;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ClaimsEndpointTest {

    @Test
    public void testAddClaim(){

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .accept("application/json")
                .body("{\"name\": \"Oracle\", \"serviceRequested\": \"oracle-database\"}")
                .when().post("/claims")
                .then()
                .statusCode(201)
                .body(containsString("Oracle"))
                .body("id", notNullValue())
                .extract().body().jsonPath().getString("id");
    }

    @Test
    public void testAddClaimViaHtmxForm(){
        // An htmx request will contain a HX-Request header and Content-Type: application/x-www-form-urlencoded
        given()
                .header("HX-Request", true)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("{\"name\": \"Oracle\", \"serviceRequested\": \"oracle-database\"}")
                .when().post("/claims")
                .then()
                .statusCode(201);

        // TODO check the created date is not null
    }

    @Test
    public void testFindByName(){

        given()
                .when().get("/claims")
                .then()
                .statusCode(200)
                .body(
                        containsString("mysql-demo"),
                        containsString("postgresql-team-dev"),
                        containsString("postgresql-team-test"),
                        containsString("mariadb-demo"),
                        containsString("postgresql-13"));

        given()
                .when().get("/claims/name/mysql-demo")
                .then()
                .statusCode(200)
                .body(containsString("mysql-demo"));
    }

    @Test
    public void testClaimEntity() {
        final String path="/claims";
        //List all
        given()
                .when().get(path)
                .then()
                .statusCode(200)
                .body(
                        containsString("mysql-demo"),
                        containsString("postgresql-team-dev"),
                        containsString("postgresql-team-test"),
                        containsString("mariadb-demo"),
                        containsString("postgresql-13"));

        //Delete the 'mysql-demo':
        given()
                .when().delete(path + "/4")
                .then()
                .statusCode(204);

        //List all, 'mariadb-demo' should be missing now:
        given()
                .when().get(path)
                .then()
                .statusCode(200)
                .body(
                        containsString("mysql-demo"),
                        containsString("postgresql-team-dev"),
                        containsString("postgresql-team-test"),
                        not(containsString("mariadb-demo")),
                        containsString("postgresql-13"));
    }

}
