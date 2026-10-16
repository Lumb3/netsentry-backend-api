package com.netsentry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuthControllerTests {

    private MockRestServiceServer google;
    private OAuthController controller;
    private DefaultOidcUser user;
    private OAuth2AuthorizedClient authorizedClient;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        google = MockRestServiceServer.bindTo(builder).build();
        controller = new OAuthController(builder);

        Instant now = Instant.now();
        var idToken = new OidcIdToken("test-id-token", now, now.plusSeconds(3600),
                Map.of("sub", "test-user"));
        user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        var registration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId("test-client").clientSecret("test-secret").build();
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "test-access-token", now, now.plusSeconds(3600),
                Set.of("https://www.googleapis.com/auth/calendar.events.readonly"));
        authorizedClient = new OAuth2AuthorizedClient(registration, user.getName(), accessToken);
    }

    @AfterEach
    void verifyRequest() {
        google.verify();
    }


    @Test
    void rejectedTokenProducesBadGatewayWithoutLeakingGoogleResponse() {
        expectCalendarRequest().andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .body("private-provider-response test-access-token"));

        assertFailure(HttpStatus.BAD_GATEWAY, "Google rejected the access token. Sign in again.");
    }

    @Test
    void deniedCalendarAccessProducesForbidden() {
        expectCalendarRequest().andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertFailure(HttpStatus.FORBIDDEN,
                "Google denied Calendar access. Check API enablement and granted permissions.");
    }

    @Test
    void googleServerErrorProducesBadGateway() {
        expectCalendarRequest().andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertFailure(HttpStatus.BAD_GATEWAY, "Google Calendar request failed.");
    }

    @Test
    void emptyResponseDoesNotReportSuccessfulConnection() {
        expectCalendarRequest().andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertFailure(HttpStatus.BAD_GATEWAY, "Google Calendar returned an invalid response.");
    }

    private ResponseActions expectCalendarRequest() {
        return google.expect(requestTo("https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"));
    }

    private void assertFailure(HttpStatus status, String reason) {
        assertThatThrownBy(() -> controller.testGoogleConnection(user, authorizedClient))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(status);
                    assertThat(error.getReason()).isEqualTo(reason);
                    assertThat(error.getMessage()).doesNotContain("private-provider-response", "test-access-token");
                });
    }
}
