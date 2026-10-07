package com.example.tutorconnect.dto;

import java.time.LocalDateTime;

public record AppointmentDto(
    long appointmentId,
    long userId,
    long slotId,
    String customerName,
    String providerName,
    String serviceName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String status) {}
