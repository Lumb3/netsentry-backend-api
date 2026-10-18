package com.netsentry.DTOs;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ImageExtractionJsonResponse(
        @JsonProperty(required = true)
        List<CalendarEvent> events
) {
    public record CalendarEvent(
            @JsonProperty(required = true)
            String title,

            @JsonProperty(required = true)
            String startTime,

            @JsonProperty(required = true)
            String endTime
    ) {}
}