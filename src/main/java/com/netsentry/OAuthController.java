
package com.netsentry;

import com.netsentry.DTOs.GoogleConnectionResponse;
import com.netsentry.models.AuthenticatorEndpoints;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
public class OAuthController implements AuthenticatorEndpoints {

    private final RestClient restClient;
    private static final Logger logger = LoggerFactory.getLogger(OAuthController.class);
    public OAuthController(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://www.googleapis.com/calendar/v3")
                .build();
    }

    /**
     * OpendID & OAuth 2.0 processing workflow:
     * 1. Spring redirects the browser to Google with the client ID, requested permissions, and callback URL.
     * 2. After the user signs in and grants permission, Google redirects the browser back with a temporary authorization code.
     * 3. Spring exchanges that code at Google’s token endpoint, authenticating your application with its client ID and client secret, over HTTPS.
     * 4. Spring processes the returned tokens: the ID token establishes the user’s identity, and the access token authorizes Calendar requests.
     * @param user is the ID token (user's identity)
     * @param authorizedClient is the access token (calendar request)
     * @return GoogleConnectionResponse DTO
     */
    @Override
    public GoogleConnectionResponse testGoogleConnection(@AuthenticationPrincipal OidcUser user, @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        List<JsonNode> events = new ArrayList<>();
        String calendarOwnerName = user.getFullName();
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
                                authorizedClient.getAccessToken().getTokenValue() // Attaches the access token in HTTP header
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
                logger.warn("Google rejected the access token. Redirecting to the login page...");
                throw new GoogleReconnectRequiredException();
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

    static private class GoogleReconnectRequiredException extends RuntimeException {
    }

    @ExceptionHandler(GoogleReconnectRequiredException.class)
    public ResponseEntity<Void> reconnectGoogle(HttpServletRequest request) {

        URI loginUrl = URI.create(
                request.getContextPath() + "/oauth2/authorization/google"
        );

        return ResponseEntity
                .status(HttpStatus.FOUND) // HTTP 302 redirect
                .location(loginUrl)
                .build();
    }
}
