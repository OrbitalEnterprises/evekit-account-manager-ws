package enterprises.orbital.evekit.ws.model;

import enterprises.orbital.evekit.model.ESISyncEndpoint;

public class SyncEndpointStats {
  private ESISyncEndpoint endpoint;
  private int attempts;
  private int failures;

  public SyncEndpointStats(ESISyncEndpoint endpoint, int attempts, int failures) {
    this.endpoint = endpoint;
    this.attempts = attempts;
    this.failures = failures;
  }

  public ESISyncEndpoint getEndpoint() {
    return endpoint;
  }

  public int getAttempts() {
    return attempts;
  }

  public int getFailures() {
    return failures;
  }

  public void incrementAttempts() {
    attempts++;
  }

  public void incrementFailures() {
    failures++;
  }
}
