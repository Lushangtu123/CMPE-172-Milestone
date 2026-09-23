package com.example.tutorconnect.service;

import com.example.tutorconnect.dto.SlotDto;
import com.example.tutorconnect.repository.SlotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SlotService {

    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<SlotDto> getSlots() {
        return slotRepository.findAll();
    }
}
