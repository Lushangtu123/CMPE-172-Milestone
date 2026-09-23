package com.example.tutorconnect.dto;

import java.time.LocalDateTime;

public record SlotDto(
        Long slotId,
        String providerName,
        String serviceName,
        String description,
        Integer durationMinutes,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {
}
