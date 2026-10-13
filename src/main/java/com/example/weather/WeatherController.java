package com.example.weather;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {
    private final WeatherService service;
    private final String defaultZip;

    public WeatherController(WeatherService service, @Value("${weather.default-zip}") String defaultZip) {
        this.service = service;
        this.defaultZip = defaultZip;
    }

    @GetMapping
    public WeatherService.WeatherResponse getWeather() {
        return service.getWeather(defaultZip);
    }

    @GetMapping("/city")
    public WeatherService.WeatherResponse getWeatherByCity(@RequestParam String cityName) {
        return service.getWeatherByCity(cityName);
    }

    @GetMapping("/zip")
    public WeatherService.WeatherResponse getWeatherByZip(@RequestParam String zipCode) {
        return service.getWeather(zipCode);
    }
}
