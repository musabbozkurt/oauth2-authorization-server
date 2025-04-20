package mb.oauth2authorizationserver.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.service.WeatherService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WeatherServiceImpl implements WeatherService {

    private static final String BASE_URL = "https://api.weather.gov";
    private static final String TEMPERATURE_WIND_FORECAST = """
            %s:
            Temperature: %s %s
            Wind: %s %s
            Forecast: %s
            """;
    private static final String EVENT_AREA_SEVERITY_DESCRIPTION_INSTRUCTIONS = """
            Event: %s
            Area: %s
            Severity: %s
            Description: %s
            Instructions: %s
            """;

    private final RestClient restClient;

    public WeatherServiceImpl() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/geo+json")
                .defaultHeader("User-Agent", "WeatherApiClient/1.0 (mb@email.com)")
                .build();
    }

    /**
     * Get forecast for a specific latitude/longitude
     *
     * @param latitude  Latitude
     * @param longitude Longitude
     * @return The forecast for the given location
     * @throws RestClientException if the request fails
     */
    @Tool(description = "Get weather forecast for a specific latitude/longitude")
    public String getWeatherForecastByLocation(double latitude, double longitude) {
        var points = restClient.get()
                .uri("/points/{latitude},{longitude}", latitude, longitude)
                .retrieve()
                .body(Points.class);

        var forecast = restClient.get().uri(Optional.ofNullable(points).orElseThrow().properties().forecast()).retrieve().body(Forecast.class);

        return Optional.ofNullable(forecast).orElseThrow()
                .properties()
                .periods()
                .stream()
                .map(p -> String.format(TEMPERATURE_WIND_FORECAST, p.name(), p.temperature(), p.temperatureUnit(), p.windSpeed(), p.windDirection(), p.detailedForecast()))
                .collect(Collectors.joining());
    }

    /**
     * Get alerts for a specific area
     *
     * @param state Area code. Two-letter US state code (e.g. CA, NY)
     * @return Human-readable alert information
     * @throws RestClientException if the request fails
     */
    @Tool(description = "Get weather alerts for a US state. Input is Two-letter US state code (e.g. CA, NY)")
    public String getAlerts(@ToolParam(description = "Two-letter US state code (e.g. CA, NY") String state) {
        Alert alert = restClient.get().uri("/alerts/active/area/{state}", state).retrieve().body(Alert.class);

        return Optional.ofNullable(alert).orElseThrow()
                .features()
                .stream()
                .map(f -> String.format(EVENT_AREA_SEVERITY_DESCRIPTION_INSTRUCTIONS, f.properties().event(), f.properties.areaDesc(), f.properties.severity(), f.properties.description(), f.properties.instruction()))
                .collect(Collectors.joining("\n"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Points(@JsonProperty("properties") Props properties) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Props(@JsonProperty("forecast") String forecast) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(@JsonProperty("properties") Props properties) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Props(@JsonProperty("periods") List<Period> periods) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Period(@JsonProperty("number") Integer number, @JsonProperty("name") String name,
                             @JsonProperty("startTime") String startTime, @JsonProperty("endTime") String endTime,
                             @JsonProperty("isDaytime") Boolean isDayTime,
                             @JsonProperty("temperature") Integer temperature,
                             @JsonProperty("temperatureUnit") String temperatureUnit,
                             @JsonProperty("temperatureTrend") String temperatureTrend,
                             @JsonProperty("probabilityOfPrecipitation") Map<?, ?> probabilityOfPrecipitation,
                             @JsonProperty("windSpeed") String windSpeed,
                             @JsonProperty("windDirection") String windDirection,
                             @JsonProperty("icon") String icon, @JsonProperty("shortForecast") String shortForecast,
                             @JsonProperty("detailedForecast") String detailedForecast) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alert(@JsonProperty("features") List<Feature> features) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Feature(@JsonProperty("properties") Properties properties) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Properties(@JsonProperty("event") String event, @JsonProperty("areaDesc") String areaDesc,
                                 @JsonProperty("severity") String severity,
                                 @JsonProperty("description") String description,
                                 @JsonProperty("instruction") String instruction) {
        }
    }
}
