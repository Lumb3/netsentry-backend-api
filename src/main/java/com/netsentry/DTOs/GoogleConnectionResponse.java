package com.netsentry.DTOs;

public record GoogleConnectionResponse(
        boolean connected,
        String userId,
        String calendar,
        int eventsReturned
) {
}
