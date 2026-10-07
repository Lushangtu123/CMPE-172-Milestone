package com.example.tutorconnect.dto;

import java.util.List;

public record SlotPage(List<SlotDto> items, int page, int size, long total) {
  public boolean hasNext() {
    return (long) (page + 1) * size < total;
  }
}
