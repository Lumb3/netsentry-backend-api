package com.netsentry.DTOs;

public record ImageUploadResponse(String filename, ImageExtractionJsonResponse extracted_response) {
}
