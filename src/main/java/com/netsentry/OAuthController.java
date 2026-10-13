package com.netsentry;

import com.netsentry.models.AuthenticatorEndpoints;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

@RestController
public class OAuthController implements AuthenticatorEndpoints {

    private final RestClient restClient;

    public OAuthController(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://www.googleapis.com/calendar/v3")
                .build();
    }

    @Override
    public Map<String, Object> testGoogleConnection(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        JsonNode calendarResponse;
        try {
            calendarResponse = restClient.get()
                    .uri("/calendars/primary/events?maxResults=1")
                    .headers(headers -> headers.setBearerAuth(
                            authorizedClient.getAccessToken().getTokenValue()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 401) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Google rejected the access token. Sign in again.");
            }
            if (error.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Google denied Calendar access. Check API enablement and granted permissions.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Calendar request failed.");
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read Google Calendar's response.");
        }

        if (calendarResponse == null || !calendarResponse.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Calendar returned an invalid response.");
        }

        return Map.of(
                "connected", true,
                "userId", user.getSubject(),
                "calendar", calendarResponse.path("summary").asString(""),
                "eventsReturned", calendarResponse.path("items").size());
    }
}
