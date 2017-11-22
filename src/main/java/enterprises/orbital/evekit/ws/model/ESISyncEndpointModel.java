package enterprises.orbital.evekit.ws.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import enterprises.orbital.evekit.model.ESISyncEndpoint;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "ESI Synchronization endpooint description")
public class ESISyncEndpointModel {
  @ApiModelProperty(value = "Endpoint name")
  @JsonProperty("name")
  public String name;

  @ApiModelProperty(value = "Scope required for endpoint")
  @JsonProperty("scope")
  public String scope;

  @ApiModelProperty(value = "Endpoint description")
  @JsonProperty("description")
  public String description;

  @ApiModelProperty(value = "True if endpoint represents a character endpoint, false otherwise")
  @JsonProperty("isChar")
  public boolean isChar;

  public ESISyncEndpointModel(String name, String scope, String description, boolean isChar) {
    this.name = name;
    this.scope = scope;
    this.description = description;
    this.isChar = isChar;
  }

  public static ESISyncEndpointModel fromSyncEndpoint(ESISyncEndpoint p) {
    return new ESISyncEndpointModel(p.name(), p.getScope(), p.getDescription(), p.isChar());
  }

}
