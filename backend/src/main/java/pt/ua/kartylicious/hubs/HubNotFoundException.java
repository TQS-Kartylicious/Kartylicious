package pt.ua.kartylicious.hubs;

public class HubNotFoundException extends RuntimeException {
  public HubNotFoundException(String id) {
    super("Hub not found: " + id);
  }
}
