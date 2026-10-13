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
    private final RestClient geocodingClient;

    public WeatherService(RestClient.Builder builder,
            @Value("${weather.zip-base-url}") String zipBaseUrl,
            @Value("${weather.forecast-base-url}") String forecastBaseUrl,
            @Value("${weather.geocoding-base-url}") String geocodingBaseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        zipClient = builder.clone().requestFactory(factory).baseUrl(zipBaseUrl).build();
        geocodingClient = builder.clone().requestFactory(factory).baseUrl(geocodingBaseUrl).build();
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
        return getForecast(zipCode, place);
    }

    public WeatherResponse getWeatherByCity(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cityName must not be blank");
        }
        CityLocations locations;
        try {
            locations = geocodingClient.get().uri(uri -> uri.path("/v1/search")
                    .queryParam("name", "{name}").queryParam("count", 1)
                    .queryParam("language", "en").build(cityName.strip()))
                    .retrieve().body(CityLocations.class);
        } catch (RestClientException ex) {
            throw unavailable();
        }
        if (locations == null) {
            throw unavailable();
        }
        if (locations.results() == null || locations.results().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found");
        }
        CityLocation city = locations.results().get(0);
        if (city == null || city.latitude() == null || city.longitude() == null) {
            throw unavailable();
        }
        return getForecast(null, new Place(city.name(), city.admin1(), city.latitude(), city.longitude()));
    }

    private WeatherResponse getForecast(String zipCode, Place place) {
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

    record CityLocations(List<CityLocation> results) {}
    record CityLocation(String name, String admin1, Double latitude, Double longitude) {}
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
