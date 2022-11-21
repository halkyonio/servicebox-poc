package io.halkyon;

import static io.halkyon.utils.TestUtils.createClaim;
import static io.halkyon.utils.TestUtils.createService;
import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.halkyon.model.Claim;
import io.halkyon.services.ClaimStatus;
import io.halkyon.services.ClaimingServiceJob;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ClaimingServiceJobTest {

    @Inject
    ClaimingServiceJob job;

    @ConfigProperty(name = "servicebox.claiming-service-job.max-attempts")
    int maxAttempts;

    @Inject
    Scheduler scheduler;

    @Test
    public void testJobShouldMarkClaimAsErrorAfterMaxAttemptsExceeded(){
        pauseScheduler();
        Claim postgresClaim = createClaim("Postgresql-ClaimingServiceJobTest", "postgresqlClaimingServiceJobTest-8");
        Claim mySqlClaim = createClaim("MySQL-ClaimingServiceJobTest", "MySQLClaimingServiceJobTest-7.5");
        createService("postgresqlClaimingServiceJobTest", "8", true);
        createService("MySQLClaimingServiceJobTest", "7.5", false);

        // When we run the job once:
        // Then:
        // - the claim "PostgresSQL" should change from "new" to "claimed"
        // - the claim "MySQL" should increase the attempts to 2
        // When we run the job again:
        // Then:
        // - the claim "mysql-demo" should increase the attempts to 3 and status should have changed to "error".
        job.execute();
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/claims/name/" + postgresClaim.name)
                .then()
                .statusCode(200)
                .body("status", is(ClaimStatus.BIND.toString()))
                .body("service.name", containsString("postgresql"));

        for (int attempt = 1; attempt < maxAttempts; attempt++) {
            job.execute();
            given()
                    .contentType(MediaType.APPLICATION_JSON)
                    .get("/claims/name/" + mySqlClaim.name)
                    .then()
                    .statusCode(200)
                    .body("status", is(ClaimStatus.PENDING.toString()))
                    .body("attempts", is(attempt + 1));
        }

        job.execute();
        // Because mysql service is not available:
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/claims/name/" + mySqlClaim.name)
                .then()
                .statusCode(200)
                .body("status", is(ClaimStatus.ERROR.toString()))
                .body("attempts", is(maxAttempts));
    }

    @Test
    public void testShouldClaimServiceWhenNewClaimIsCreated(){
        pauseScheduler();
        given()
                .header("HX-Request", true)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .formParam("name", "Oracle1")
                .formParam("serviceRequested", "oracle-1234")
                .formParam("description", "Description")
                .when().post("/claims")
                .then()
                .statusCode(201);
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .get("/claims/name/Oracle1")
                .then()
                .statusCode(200)
                .body("status", is(ClaimStatus.PENDING.toString()))
                .body("attempts", is(1));
    }

    private void pauseScheduler() {
        scheduler.pause();
        await().atMost(30, SECONDS).until(() -> !scheduler.isRunning());
    }
}
