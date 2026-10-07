package com.example.tutorconnect.dto;

import java.util.List;

public record HomeDto(
    String application,
    String scenario,
    int providerCount,
    int serviceCount,
    int openSlotCount,
    List<String> providers) {}
