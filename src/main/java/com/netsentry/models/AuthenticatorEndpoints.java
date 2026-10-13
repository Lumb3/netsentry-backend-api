package com.netsentry.models;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@RequestMapping("/api/v1")
public interface AuthenticatorEndpoints {
    @GetMapping("/test/google")
    Map<String, Object> testGoogleConnection(@AuthenticationPrincipal OidcUser user, @RegisteredOAuth2AuthorizedClient("google")OAuth2AuthorizedClient authorizedClient);
}
