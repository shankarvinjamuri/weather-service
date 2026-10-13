# Weather service

Spring Boot REST application using Java 17+. A Maven wrapper is included.

## Run

Use a native ARM64 JDK 17 or 21 on Apple Silicon. The commands below select the Java 17 installation already on this Mac. The wrapper downloads Maven on first use.

```sh
cd /Users/shanx-mac-m2/Desktop/technical/code/weather-service
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
java -version
./mvnw test
./mvnw spring-boot:run
```

## APIs

| Request | Behavior |
| --- | --- |
| `GET /api/weather` | Current weather for the default ZIP (85001, Phoenix) |
| `GET /api/weather/city?cityName=Phoenix` | Current weather for the first matching city |
| `GET /api/weather/zip?zipCode=90210` | Current weather for a five-digit US ZIP |

```sh
curl 'http://localhost:8080/api/weather/city?cityName=Phoenix'
curl http://localhost:8080/api/weather
curl 'http://localhost:8080/api/weather/zip?zipCode=90210'
```

Change the default ZIP with an environment variable:

```sh
WEATHER_DEFAULT_ZIP=10001 ./mvnw spring-boot:run
```

Illustrative response (weather values vary):

```json
{
  "zipCode": "85001",
  "city": "Phoenix",
  "state": "Arizona",
  "timezone": "America/Phoenix",
  "current": {
    "time": "2026-10-12T12:00",
    "temperature_2m": 25.0,
    "relative_humidity_2m": 30,
    "apparent_temperature": 24.0,
    "weather_code": 0,
    "wind_speed_10m": 8.0
  }
}
```

Temperatures are Celsius, wind speed is km/h, humidity is percent, and time is local to the returned timezone. `weather_code` uses WMO codes (0 means clear sky). ZIP codes remain strings to preserve leading zeros. When a ZIP contains multiple places, the first place returned by the location provider is used.

Malformed or missing ZIP input returns HTTP 400, an unknown ZIP returns 404, and provider failures return 502. Provider calls have a 5-second connection timeout and a 10-second read timeout.

Location data comes from [Zippopotam.us](https://zippopotam.us/). Weather data comes from [Open-Meteo](https://open-meteo.com/) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). No API key is required for the configured public services. Internet access is required. Review [Open-Meteo's terms](https://open-meteo.com/en/terms) before commercial deployment; its free hosted endpoint is for non-commercial use.

## Build a JAR

```sh
./mvnw clean package
java -jar target/weather-service-0.0.1-SNAPSHOT.jar
```

Tests cover default-location routing, ZIP input with leading zeros, missing input, and structured errors without calling external providers.

City searches use [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api) (location data from GeoNames). Names are trimmed and the first match is used; qualify ambiguous names, for example `Paris, France`. City responses use the same weather fields with `zipCode: null` and the administrative region in `state`. Missing or blank city names return 400, no matches return 404, and provider failures return 502.
