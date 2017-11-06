package enterprises.orbital.evekit.ws.account;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOk;
import enterprises.orbital.evekit.account.*;
import enterprises.orbital.evekit.ws.common.ServiceError;
import enterprises.orbital.evexmlapi.EveXmlApiAdapter;
import enterprises.orbital.evexmlapi.EveXmlApiConfig;
import enterprises.orbital.evexmlapi.IEveXmlApi;
import enterprises.orbital.evexmlapi.act.IAccountAPI;
import enterprises.orbital.evexmlapi.act.ICharacter;
import enterprises.orbital.oauth.AuthUtil;
import enterprises.orbital.oauth.EVEApi;
import enterprises.orbital.oauth.EVEAuthHandler;
import io.swagger.annotations.*;
import org.apache.http.client.utils.URIBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Web service for managing synchronization credentials.
 */
@Path("/ws/v1/cred")
@Consumes({
    "application/json"
})
@Produces({
    "application/json"
})
@Api(
    tags = {
        "Credential"
    },
    produces = "application/json",
    consumes = "application/json")
public class CredentialWS {
  public static final Logger log = Logger.getLogger(CredentialWS.class.getName());

  // EVE SSO Client ID and Secret to be used for token handling.  These are normally different than the client ID
  // and secret we use for EveKit authentication because the callback path is different and EVE SSO is
  // strict about callback path.
  public static final String PROP_TOKEN_EVE_CLIENT_ID = "enterprises.orbital.token.eve_client_id";
  public static final String PROP_TOKEN_EVE_SECRET_KEY = "enterprises.orbital.token.eve_secret_key";

  // The EVE SSO verify URL is generic so we can use whatever authentication is using.
  public static final String PROP_EVE_VERIFY_URL = "enterprises.orbital.auth.eve_verify_url";

  // Location of ESI server
  public static final String PROP_ESI_SERVER_PATH = "enterprises.orbital.evekit.accountws.esiServerPath";
  public static final String DEF_ESI_SERVER_PATH = "https://esi.tech.ccp.is/latest";

  // Application path.  This is set to construct a proper callback path for ESI token construction.
  public static final String PROP_APP_PATH = "enterprises.orbital.evekit.accountws.apppath";
  public static final String DEF_APP_PATH = "http://localhost/controller";

  // Properties to manage the lifetime of temporary tokens for ESI token creation
  public static final String PROP_TEMP_TOKEN_LIFETIME = "enterprises.orbital.evekit.tempTokenLifetime";
  public static final long DEF_TEMP_TOKEN_LIFETIME = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

  // Fragment to redirect to on successful re-authorization of an ESI credential.
  public static final String PROP_REAUTH_SUCCESS_FRAGMENT = "enterprises.orbital.evekit.reauthFragment";
  public static final String DEF_REAUTH_SUCCESS_FRAGMENT = "account";

  // User agent string for web requests
  public static final String PROP_SITE_AGENT = "enterprises.orbital.evekit.site_agent";
  public static final String DEF_SITE_AGENT = "EveKit/4.0.0 (https://evekit.orbital.enterprises; deadlybulb@orbital.enterprises; )";

  /**
   * Create a URI builder initialized with the main application URL.
   *
   * @return initialized URI builder
   */
  protected static URIBuilder makeStandardBuilder() {
    try {
      URIBuilder builder = new URIBuilder(OrbitalProperties.getGlobalProperty(PROP_APP_PATH, DEF_APP_PATH) + "/");
      return builder;
    } catch (URISyntaxException e) {
      // This is a configuration error if this ever happens.  Log it.
      log.log(Level.SEVERE, "Configuration error: " + e);
      throw new RuntimeException("Unrecoverable configuration error");
    }
  }

  /**
   * Start new OAuth flow.
   *
   * @param request incoming request
   * @param user    authorizing user
   * @param account account which will store new ESI token
   * @param scopes  requested scopes
   * @return response containing authorization URL for client
   * @throws IOException if an error occurs while starting the flow
   */
  protected static Response startTokenFlow(HttpServletRequest request, EveKitUserAccount user,
                                           SynchronizedEveAccount account,
                                           String scopes)
      throws IOException {
    // Generate authorization URL for new ESI token
    URIBuilder builder = makeStandardBuilder();
    builder.setPath(builder.getPath() + "api/ws/v1/cred/esi_callback");
    String eveClientID = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_CLIENT_ID);
    String eveSecretKey = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_SECRET_KEY);
    long now = OrbitalProperties.getCurrentTime();
    long expiry = now + OrbitalProperties.getLongGlobalProperty(PROP_TEMP_TOKEN_LIFETIME,
                                                                DEF_TEMP_TOKEN_LIFETIME);
    NewESIToken key = NewESIToken.createKey(user, account, now, expiry, scopes);
    String redirect = EVEAuthHandler.doGet(eveClientID, eveSecretKey, builder.toString(), scopes, key.getStateKey(), request);
    if (redirect == null) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error creating ESI credential, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
    // Success, return redirect allowing client to complete authentication
    return Response.ok()
                   .entity(new Object() {
                     @SuppressWarnings("unused")
                     public final String newLocation = redirect;
                   })
                   .build();
  }

  /**
   * Process the end of an OAuth flow to set an ESI credential.
   *
   * @param req          incoming OAuth callback request
   * @param verifyURL    EVE SSO verify URL to retrieve authorized character info
   * @param eveClientID  EVE SSO client ID
   * @param eveSecretKey EVE SSO secret key
   * @throws AccountUpdateException if an inconsistent state would result from setting the credential
   * @throws IOException            on any other error
   */
  protected static void processTokenCallback(HttpServletRequest req, String verifyURL, String eveClientID,
                                             String eveSecretKey)
      throws AccountUpdateException, IOException {
    // Extract key information associated with state.  Fail if no key information found.
    String stateKey = req.getParameter("state");
    if (stateKey == null) throw new IOException("request missing OAuth state");
    NewESIToken keyState = NewESIToken.getKeyByState(stateKey);
    if (keyState == null) throw new IOException("no temporary key found with provided state");
    NewESIToken.deleteKey(keyState.getKid());

    // Construct the service to use for verification.
    OAuth20Service service = new ServiceBuilder().apiKey(eveClientID)
                                                 .apiSecret(eveSecretKey)
                                                 .build(EVEApi.instance());

    // Exchange for access token
    OAuth2AccessToken accessToken = service.getAccessToken(req.getParameter("code"));

    // Retrieve character and corporation info
    OAuthRequest request = new OAuthRequest(Verb.GET, verifyURL, service.getConfig());
    service.signRequest(accessToken, request);
    com.github.scribejava.core.model.Response response = request.send();
    if (!response.isSuccessful()) throw new IOException("credential request was not successful!");
    JsonObject responseObject = (new JsonParser()).parse(response.getBody())
                                                  .getAsJsonObject();
    String charName = responseObject.get("CharacterName")
                                    .getAsString();
    long charID = responseObject.get("CharacterID")
                                .getAsLong();

    // Use ESI to retrieve corporation name and ID
    String siteAgent = OrbitalProperties.getGlobalProperty(PROP_SITE_AGENT, DEF_SITE_AGENT);
    String corpName;
    long corpID;
    try {
      CharacterApi charApi = new CharacterApi();
      GetCharactersCharacterIdOk charResult = charApi.getCharactersCharacterId((int) charID, null,
                                                                               siteAgent, null);
      corpID = charResult.getCorporationId();
      CorporationApi corpApi = new CorporationApi();
      GetCorporationsCorporationIdOk result = corpApi.getCorporationsCorporationId(charResult.getCorporationId(),
                                                                                   null,
                                                                                   siteAgent, null);
      corpName = result.getCorporationName();
    } catch (ApiException e) {
      throw new IOException("Exception while retrieving corporation information", e);
    }

    // Set credential
    try {
      long tokenExpiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(accessToken.getExpiresIn(), TimeUnit.SECONDS);
      SynchronizedEveAccount.setESICredential(keyState.getUser(), keyState.getAccount()
                                                                          .getAid(), accessToken.getAccessToken(),
                                              tokenExpiry, accessToken.getRefreshToken(), keyState.getScopes(), charID, charName, corpID, corpName);
    } catch (AccountNotFoundException e) {
      throw new IOException("Account disappeared before authorization could complete");
    }
  }

  /**
   * Set the XML API credential associated with a Synchronized EVE account.
   *
   * @param request inbound request
   * @param aid     account ID
   * @param charID  character ID to be used with XML credentials
   * @param key     XML API access key
   * @param vcode   XML API access verificaation code
   * @return the modified SynchronizedEveAccount if the change is successful.
   */
  @Path("/cred_xml/{aid}")
  @POST
  @ApiOperation(
      value = "Set the XML API credential for a synchronized EVE account.  Returns the result of applying the change.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "Credential set successfully",
              response = SynchronizedEveAccount.class),
          @ApiResponse(
              code = 401,
              message = "Requestor is not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 403,
              message = "Provided xml credential has an illegal value.  More details are provided in the response message",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "Requesting for other than logged in user, but target user not found; or, the target account is not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Error using provided credentials, or other internal account service service error",
              response = ServiceError.class),
      })
  public Response setXMLCredential(
      @Context HttpServletRequest request,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "ID of sync account owning credential to be updated or saved.") long aid,
      @QueryParam("key") @ApiParam(
          name = "key",
          required = true,
          value = "XML API access key") int key,
      @QueryParam("charID") @ApiParam(
          name = "charID",
          required = true,
          value = "Character ID to use with this credential") long charID,
      @ApiParam(
          name = "vcode",
          required = true,
          value = "XML API access verification code") String vcode) {
    // Verify post argument
    if (vcode == null) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "POST argument is null");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    }

    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return AccountWS.createUserNotLoggedResponse();

    // Use XML credential to retrieve character info
    long characterID = -1, corporationID = -1;
    String characterName = null, corporationName = null;
    try {
      String siteAgent = OrbitalProperties.getGlobalProperty(PROP_SITE_AGENT, DEF_SITE_AGENT);
      IEveXmlApi apiHandle = new EveXmlApiAdapter(EveXmlApiConfig.get()
                                                                 .agent(siteAgent));
      IAccountAPI accountHandle = apiHandle.getAccountAPIService(key, vcode);
      Collection<ICharacter> charList = accountHandle.requestCharacters();
      for (ICharacter next : charList) {
        if (next.getCharacterID() == charID) {
          // Found, set and break
          characterID = charID;
          characterName = next.getName();
          corporationID = (int) next.getCorporationID();
          corporationName = next.getCorporationName();
          break;
        }
      }
      if (characterName == null) {
        // Provided character not found with this key, return an error.
        ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Could not find character " + charID + " with provided credentials");
        return Response.status(Status.FORBIDDEN)
                       .entity(errMsg)
                       .build();
      }
    } catch (URISyntaxException e) {
      // This is a bug if it happens
      log.log(Level.SEVERE, "error setting site agent", e);
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error saving credential, contact admin if problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      log.log(Level.SEVERE, "error retrieving character info", e);
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Unable to retrieve character info with provided credentials, contact admin if problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }

    // Attempt to set XML credentials
    try {
      SynchronizedEveAccount account = SynchronizedEveAccount.setXMLCredential(user, aid, key, vcode, characterID, characterName, corporationID, corporationName);
      // Return result
      return Response.ok()
                     .entity(account)
                     .build();
    } catch (AccountNotFoundException | AccountUpdateException e) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), e.getMessage());
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error saving credential, contact admin if problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Start the process to set an ESI credential.  If OAuth authorization succeeds, the actual set call is
   * made in the callback function.
   *
   * @param request incoming HTTP request
   * @param aid     account on which credential will be set
   * @param scopes  request scopes
   * @return an OK response containing the authorization URL which should be redirected to
   */
  @Path("/cred_esi/{aid}")
  @POST
  @ApiOperation(
      value = "Set the ESI credential for a synchronized account by starting an OAuth flow.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "OAuth flow started.  Result will be an object with a 'newLocation' field containing a redirect"),
          @ApiResponse(
              code = 401,
              message = "requestor not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 403,
              message = "illegal scope value or can't find target account.",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal service error",
              response = ServiceError.class),
      })
  public Response setESICredential(
      @Context HttpServletRequest request,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "ID of sync account owning credential to be created.") long aid,
      @ApiParam(
          name = "scope",
          required = true,
          value = "Space separated list of scopes for credential") String scopes) {
    // Verify post argument
    if (scopes == null || scopes.length() == 0) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "POST argument is null or has zero length");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    }
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return AccountWS.createUserNotLoggedResponse();

    try {
      // Retrieve account and start new token creation flow
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, false);
      return startTokenFlow(request, user, account, scopes);
    } catch (AccountNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Can't find target account");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error starting OAuth flow, contact admin if problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Handle the second part of setting an ESI credential by accepting the OAuth callback.
   *
   * @param req incoming HTTP request
   * @return a redirect response or error message
   */
  @Path("/esi_callback")
  @GET
  @ApiOperation(
      value = "Handle OAuth callback for ESI credential setting")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 307,
              message = "Temporary redirect back to main site."),
          @ApiResponse(
              code = 403,
              message = "Inconsistency detected when attempting to set credential"),
          @ApiResponse(
              code = 500,
              message = "Internal error while handling callback")
      })
  public Response esiCredentialCallback(
      @Context HttpServletRequest req) {
    String eveClientID = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_CLIENT_ID);
    String eveSecretKey = OrbitalProperties.getGlobalProperty(PROP_TOKEN_EVE_SECRET_KEY);
    String eveVerifyURL = OrbitalProperties.getGlobalProperty(PROP_EVE_VERIFY_URL);
    URIBuilder builder = makeStandardBuilder();
    builder.setFragment(OrbitalProperties.getGlobalProperty(PROP_REAUTH_SUCCESS_FRAGMENT, DEF_REAUTH_SUCCESS_FRAGMENT));
    try {
      processTokenCallback(req, eveVerifyURL, eveClientID, eveSecretKey);
      // Credential creation or re-authorization completed properly, redirect
      return Response.temporaryRedirect(new URI(builder.toString()))
                     .build();
    } catch (URISyntaxException e) {
      // This is a configuration error if this ever happens.  Log it.
      log.log(Level.SEVERE, "Configuration error: " + e);
      throw new RuntimeException("Unrecoverable configuration error");
    } catch (AccountUpdateException e) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), e.getMessage());
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      log.log(Level.SEVERE, "Error processing token callback", e);
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error setting ESI credential, contact admin if problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Clear the XML credential from a synchronized account.
   *
   * @param request incoming HTTP request
   * @param aid     account to clear
   * @return OK if credential cleared successfully, otherwise an error.
   */
  @Path("/cred_xml/{aid}")
  @DELETE
  @ApiOperation(
      value = "Remove the XML credential from a sync account")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "credential removed successfully"),
          @ApiResponse(
              code = 401,
              message = "requestor is not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "target account not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response clearXMLCredential(
      @Context HttpServletRequest request,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "ID of sync account owning credential to be deleted.") long aid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return AccountWS.createUserNotLoggedResponse();

    try {
      SynchronizedEveAccount.clearXMLCredential(user, aid);
    } catch (AccountNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error removing XML credential, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }

    // Success
    return Response.ok()
                   .build();
  }

  /**
   * Clear the ESI credential from a synchronized account.
   *
   * @param request incoming HTTP request
   * @param aid     account to clear
   * @return OK if credential cleared successfully, otherwise an error.
   */
  @Path("/cred_esi/{aid}")
  @DELETE
  @ApiOperation(
      value = "Remove the ESI credential from a sync account")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "credential removed successfully"),
          @ApiResponse(
              code = 401,
              message = "requestor is not logged in",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "target account not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response clearESICredential(
      @Context HttpServletRequest request,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "ID of sync account owning credential to be deleted.") long aid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return AccountWS.createUserNotLoggedResponse();

    try {
      SynchronizedEveAccount.clearESICredential(user, aid);
    } catch (AccountNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error removing ESI credential, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }

    // Success
    return Response.ok()
                   .build();
  }

}
