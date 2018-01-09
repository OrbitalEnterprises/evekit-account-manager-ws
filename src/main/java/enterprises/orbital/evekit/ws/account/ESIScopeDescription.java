package enterprises.orbital.evekit.ws.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import enterprises.orbital.evekit.model.ESIScope;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "ESI Synchronization endpooint description")
public class ESIScopeDescription {

  @ApiModelProperty(value = "Scope required for endpoint")
  @JsonProperty("scope")
  public String scope;

  @ApiModelProperty(value = "Endpoint description")
  @JsonProperty("description")
  public String description;

  public ESIScopeDescription(String scope, String description) {
    this.scope = scope;
    this.description = description;
  }

  public static ESIScopeDescription fromScope(ESIScope e) {
    return new ESIScopeDescription(e.getName(), e.getDescription());
  }
}
