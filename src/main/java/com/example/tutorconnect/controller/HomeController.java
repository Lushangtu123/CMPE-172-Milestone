package com.example.tutorconnect.controller;

import com.example.tutorconnect.dto.HomeDto;
import com.example.tutorconnect.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  private final HomeService homeService;

  public HomeController(HomeService homeService) {
    this.homeService = homeService;
  }

  @GetMapping("/api/home")
  public HomeDto home() {
    return homeService.getHome();
  }
}
