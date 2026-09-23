package com.example.tutorconnect.controller;

import com.example.tutorconnect.dto.SlotDto;
import com.example.tutorconnect.service.SlotService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/slots")
    public List<SlotDto> slots() {
        return slotService.getSlots();
    }
}
