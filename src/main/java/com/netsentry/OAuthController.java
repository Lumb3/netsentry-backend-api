
package com.netsentry;

import com.netsentry.DTOs.GoogleConnectionResponse;
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

import java.util.ArrayList;
import java.util.List;

@RestController
public class OAuthController implements AuthenticatorEndpoints {

    private final RestClient restClient;

    public OAuthController(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://www.googleapis.com/calendar/v3")
                .build();
    }

    @Override
    public GoogleConnectionResponse testGoogleConnection(@AuthenticationPrincipal OidcUser user, @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {

        List<JsonNode> events = new ArrayList<>();
        String calendarOwnerName = "";
        String pageToken = null;

        try {
            do {
                // Capture the token for this request
                final String currentPageToken = pageToken;

                JsonNode response = restClient.get().uri(
                          uriBuilder -> {uriBuilder
                                    .path("/calendars/primary/events")
                                    .queryParam("maxResults", 2500);

                            // Request another page if one exists
                            if (currentPageToken != null) {
                                uriBuilder.queryParam(
                                        "pageToken", currentPageToken
                                );
                            }

                            return uriBuilder.build();
                        })
                        .headers(headers -> headers.setBearerAuth(
                                authorizedClient.getAccessToken().getTokenValue()
                        ))
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null ||
                        !response.isObject() ||
                        !response.path("items").isArray()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Google Calendar returned an invalid response."
                    );
                }

                calendarOwnerName = response.path("summary")
                        .asString(calendarOwnerName);

                // Collect all events from this page
                for (JsonNode event : response.path("items")) {
                    events.add(event);
                }

                // Check whether Google has another page
                String nextPageToken = response
                        .path("nextPageToken")
                        .asString("");

                pageToken = nextPageToken.isBlank()
                        ? null
                        : nextPageToken;

            } while (pageToken != null);

        } catch (RestClientResponseException error) {

            if (error.getStatusCode().value() == 401) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Google rejected the access token. Sign in again."
                );
            }

            if (error.getStatusCode().value() == 403) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Google denied Calendar access."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Google Calendar request failed."
            );

        } catch (RestClientException error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not read Google Calendar's response."
            );
        }
        return new GoogleConnectionResponse(true, calendarOwnerName);
    }

    @Override
    public String message() {
        return "success";
    }
}
