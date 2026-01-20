package mb.oauth2authorizationserver.service.ai;

public interface WeatherService {

    String getWeatherForecastByLocation(double latitude, double longitude);

    String getAlerts(String state);
}
