package com.example.tutorconnect.dto;

public record ServiceDto(
    long serviceId, long providerId, String name, String providerName, int durationMinutes) {}
