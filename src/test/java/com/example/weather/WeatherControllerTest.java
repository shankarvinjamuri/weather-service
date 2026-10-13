package com.example.weather;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WeatherControllerTest {
    private WeatherService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(WeatherService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WeatherController(service, "85001"))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void healthcheckReturnsUpWithoutCallingWeatherProvider() throws Exception {
        mvc.perform(get("/api/weather/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(service);
    }

    @Test
    void defaultEndpointUsesConfiguredZip() throws Exception {
        when(service.getWeather("85001")).thenReturn(response("85001"));
        mvc.perform(get("/api/weather")).andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("85001"))
                .andExpect(jsonPath("$.current.temperature_2m").value(25.0));
        verify(service).getWeather("85001");
    }

    @Test
    void zipEndpointPreservesLeadingZeros() throws Exception {
        when(service.getWeather("02108")).thenReturn(response("02108"));
        mvc.perform(get("/api/weather/zip").param("zipCode", "02108"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zipCode").value("02108"));
        verify(service).getWeather("02108");
    }

    @Test
    void cityEndpointUsesCityName() throws Exception {
        when(service.getWeatherByCity("New York")).thenReturn(response(null));
        mvc.perform(get("/api/weather/city").param("cityName", "New York"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.city").value("Example"));
        verify(service).getWeatherByCity("New York");
    }

    @Test
    void missingCityReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/weather/city")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingZipReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/weather/zip")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void providerErrorsHaveStructuredResponse() throws Exception {
        when(service.getWeather("99999")).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "ZIP code not found"));
        mvc.perform(get("/api/weather/zip").param("zipCode", "99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("ZIP code not found"));
    }

    private WeatherService.WeatherResponse response(String zip) {
        return new WeatherService.WeatherResponse(zip, "Example", "Example", "America/Phoenix",
                new WeatherService.CurrentWeather("2026-10-12T12:00", 25.0, 30, 24.0, 0, 8.0));
    }
}
