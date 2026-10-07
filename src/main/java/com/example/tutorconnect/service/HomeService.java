package com.example.tutorconnect.service;

import com.example.tutorconnect.dto.HomeDto;
import com.example.tutorconnect.repository.HomeRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

  private final HomeRepository homeRepository;

  public HomeService(HomeRepository homeRepository) {
    this.homeRepository = homeRepository;
  }

  public HomeDto getHome() {
    return homeRepository.findHome();
  }
}
