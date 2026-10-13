package com.example.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WeatherService {
    private final RestClient zipClient;
    private final RestClient forecastClient;

    public WeatherService(RestClient.Builder builder,
            @Value("${weather.zip-base-url}") String zipBaseUrl,
            @Value("${weather.forecast-base-url}") String forecastBaseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        zipClient = builder.clone().requestFactory(factory).baseUrl(zipBaseUrl).build();
        forecastClient = builder.clone().requestFactory(factory).baseUrl(forecastBaseUrl).build();
    }

    public WeatherResponse getWeather(String zipCode) {
        if (zipCode == null || !zipCode.matches("[0-9]{5}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zipCode must contain exactly five digits (US ZIP code)");
        }
        ZipLocation location;
        try {
            location = zipClient.get().uri("/us/{zip}", zipCode).retrieve().body(ZipLocation.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ZIP code not found");
            }
            throw unavailable();
        } catch (RestClientException ex) {
            throw unavailable();
        }
        if (location == null || location.places() == null || location.places().isEmpty()) {
            throw unavailable();
        }
        Place place = location.places().get(0);
        if (place == null || place.latitude() == null || place.longitude() == null) {
            throw unavailable();
        }
        try {
            Forecast forecast = forecastClient.get().uri(uri -> uri.path("/v1/forecast")
                    .queryParam("latitude", place.latitude())
                    .queryParam("longitude", place.longitude())
                    .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m")
                    .queryParam("temperature_unit", "celsius")
                    .queryParam("wind_speed_unit", "kmh")
                    .queryParam("timezone", "auto").build()).retrieve().body(Forecast.class);
            if (forecast == null || forecast.current() == null || forecast.current().temperatureC() == null) {
                throw unavailable();
            }
            return new WeatherResponse(zipCode, place.name(), place.state(), forecast.timezone(), forecast.current());
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Weather provider unavailable; try again later");
    }

    record ZipLocation(List<Place> places) {}
    record Place(@JsonProperty("place name") String name, String state, Double latitude, Double longitude) {}
    record Forecast(String timezone, CurrentWeather current) {}
    public record CurrentWeather(String time,
            @JsonProperty("temperature_2m") Double temperatureC,
            @JsonProperty("relative_humidity_2m") Integer humidityPercent,
            @JsonProperty("apparent_temperature") Double feelsLikeC,
            @JsonProperty("weather_code") Integer weatherCode,
            @JsonProperty("wind_speed_10m") Double windSpeedKmh) {}
    public record WeatherResponse(String zipCode, String city, String state, String timezone, CurrentWeather current) {}
}
