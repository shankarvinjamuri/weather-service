package com.example.weather;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.http.client.ClientHttpRequestFactory;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WeatherServiceTest {
    private MockRestServiceServer server;
    private WeatherService service;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = spy(RestClient.builder());
        doReturn(builder).when(builder).clone();
        server = MockRestServiceServer.bindTo(builder).build();
        doReturn(builder).when(builder).requestFactory(any(ClientHttpRequestFactory.class));
        service = new WeatherService(builder, "https://zip.test", "https://forecast.test", "https://city.test");
    }

    @Test
    void cityLookupTrimsAndEncodesNameAndFetchesForecast() {
        server.expect(requestTo("https://city.test/v1/search?name=New%20York&count=1&language=en"))
                .andRespond(withSuccess("""
                        {"results":[{"name":"New York","admin1":"New York","latitude":40.7,"longitude":-74.0}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://forecast.test/v1/forecast?latitude=40.7&longitude=-74.0&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto"))
                .andRespond(withSuccess("""
                        {"timezone":"America/New_York","current":{"temperature_2m":20.0}}
                        """, MediaType.APPLICATION_JSON));
        var result = service.getWeatherByCity("  New York  ");
        assertEquals("New York", result.city());
        assertNull(result.zipCode());
        assertEquals(20.0, result.current().temperatureC());
        server.verify();
    }

    @Test
    void blankNamesAreRejectedBeforeCallingProvider() {
        for (String name : new String[]{null, "", "   "}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.getWeatherByCity(name)).getStatusCode());
        }
        server.verify();
    }

    @Test
    void unknownCityReturnsNotFound() {
        server.expect(requestTo("https://city.test/v1/search?name=Unknown&count=1&language=en"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.getWeatherByCity("Unknown")).getStatusCode());
        server.verify();
    }

    @Test
    void providerFailureReturnsBadGateway() {
        server.expect(requestTo("https://city.test/v1/search?name=Phoenix&count=1&language=en"))
                .andRespond(withServerError());
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> service.getWeatherByCity("Phoenix")).getStatusCode());
        server.verify();
    }
}
