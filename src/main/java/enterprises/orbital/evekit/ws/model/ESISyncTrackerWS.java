package enterprises.orbital.evekit.ws.model;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.AccountNotFoundException;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.account.UserNotFoundException;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.ws.account.AccountWS;
import enterprises.orbital.evekit.ws.common.ServiceError;
import enterprises.orbital.oauth.AuthUtil;
import io.swagger.annotations.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/ws/v2/tracker")
@Produces({
    "application/json"
})
@Api(
    tags = {
        "AccountV2"
    },
    produces = "application/json")
public class ESISyncTrackerWS {
  @SuppressWarnings("unused")
  private static final Logger log = Logger.getLogger(ESISyncTrackerWS.class.getName());
  private static final int DEF_MAX_ACCOUNT_SYNC_HISTORY = 100;
  private static final int DEF_MAX_REF_SYNC_HISTORY = 100;

  @Path("/sync_history/{aid}")
  @GET
  @ApiOperation(
      value = "Retrieve synchronization history for an account",
      notes = "Retrieves synchronization history ordered in descending order by sync start time")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "account sync history",
              response = ESIEndpointSyncTracker.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requested account ID not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response requestSyncHistory(
      @Context HttpServletRequest request,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "Sync Account ID") long aid,
      @QueryParam("contid") @DefaultValue("-1") @ApiParam(
          name = "contid",
          defaultValue = "-1",
          value = "Optional sync start time before which results will be returned") long contid,
      @QueryParam("maxresults") @ApiParam(
          name = "maxresults",
          value = "Maximum number of results to return") int maxResults) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return AccountWS.createUserNotLoggedResponse();

    try {
      // Retrieve SynchronizedEveAccount
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      // Set defaults
      maxResults = OrbitalProperties.getNonzeroLimited(maxResults, (int) PersistentProperty
          .getLongPropertyWithFallback(OrbitalProperties.getPropertyName(ESIEndpointSyncTracker.class, "maxresults"), DEF_MAX_ACCOUNT_SYNC_HISTORY));

      // Retrieve and return history
      List<ESIEndpointSyncTracker> results = ESIEndpointSyncTracker.getAllHistory(account, contid, maxResults);
      return Response.ok()
                     .entity(results)
                     .build();
    } catch (AccountNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Sync account with the given ID not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      log.log(Level.WARNING, "query error", e);
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving history, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @Path("/sync_started")
  @GET
  @ApiOperation(
      value = "Retrieve all started but unfinished synchronization trackers",
      notes = "Retrieves synchronization trackers which are started but not yet finished")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "list of started but unfinished sync trackers",
              response = ESIEndpointSyncTracker.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response requestStartedSync(
      @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED)
                     .entity(errMsg)
                     .build();
    }

    try {
      // Retrieve and return unfinished
      List<ESIEndpointSyncTracker> results = ESIEndpointSyncTracker.getAllStartedUnfinishedTrackers();
      return Response.ok()
                     .entity(results)
                     .build();
    } catch (IOException e) {
      log.log(Level.WARNING, "query error", e);
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving unfinished, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @Path("/finish_tracker/{uid}/{aid}/{tid}")
  @GET
  @ApiOperation(
      value = "Force a tracker to be marked finished if it's not finished already",
      notes = "Forces a tracker into the finished state whether it's been finished or not")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "finished successfully"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "Given user ID, account ID or tracker ID not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response requestFinishTracker(
      @Context HttpServletRequest request,
      @PathParam("uid") @ApiParam(
          name = "uid",
          required = true,
          value = "EveKit User Account ID") long uid,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "Sync Account ID") long aid,
      @PathParam("tid") @ApiParam(
          name = "tid",
          required = true,
          value = "Sync Tracker ID") long tid) {
    // Retrieve current logged in user
    EveKitUserAccount admin = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (admin == null || !admin.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED)
                     .entity(errMsg)
                     .build();
    }
    try {
      // Extract user
      EveKitUserAccount user = EveKitUserAccount.getAccount(uid);

      // Extract account
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      // Extract tracker
      ESIEndpointSyncTracker tracker = ESIEndpointSyncTracker.get(tid);
      if (!tracker.getAccount().equals(account)) throw new TrackerNotFoundException("No such tracker found for the given user and account");

      // Finish tracker
      ESIEndpointSyncTracker.finishTracker(tracker);
      return Response.ok()
                     .build();
    } catch (UserNotFoundException e) {
      // Target user not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Target account not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target synchronized account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (TrackerNotFoundException e) {
      // Target tracker not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target sync tracker not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Database error
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error finishing tracker, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @SuppressWarnings("Duplicates")
  @Path("/ref_sync_history")
  @GET
  @ApiOperation(
      value = "Retrieve synchronization history for a reference data",
      notes = "Retrieves reference data synchronization history ordered in descending order by sync start time")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "reference data sync history",
              response = ESIRefEndpointSyncTracker.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response requestRefSyncHistory(
      @Context HttpServletRequest request,
      @QueryParam("contid") @DefaultValue("-1") @ApiParam(
          name = "contid",
          defaultValue = "-1",
          value = "Optional sync start time before which results will be returned") long contid,
      @QueryParam("maxresults") @ApiParam(
          name = "maxresults",
          value = "Maximum number of results to return") int maxResults) {
    // Retrieve current logged in user. Must be an admin
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED)
                     .entity(errMsg)
                     .build();
    }
    // Set defaults
    maxResults = OrbitalProperties.getNonzeroLimited(maxResults, (int) PersistentProperty
        .getLongPropertyWithFallback(OrbitalProperties.getPropertyName(ESIRefEndpointSyncTracker.class, "maxresults"), DEF_MAX_REF_SYNC_HISTORY));
    try {
      // Retrieve and return history
      List<ESIRefEndpointSyncTracker> results = ESIRefEndpointSyncTracker.getHistory(contid, maxResults);
      return Response.ok()
                     .entity(results)
                     .build();
    } catch (IOException e) {
      log.log(Level.WARNING, "query error", e);
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error retrieving history.  Contact the administrator if this problem persists.");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @Path("/ref_sync_started")
  @GET
  @ApiOperation(
      value = "Retrieve all started but unfinished ref data synchronization trackers",
      notes = "Retrieves ref data synchronization trackers which have started but are not yet finished")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "list of started but unfinished ref data sync trackers",
              response = ESIRefEndpointSyncTracker.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response requestStartedRefSync(
      @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED)
                     .entity(errMsg)
                     .build();
    }
    try {
      // Retrieve and return unfinished
      List<ESIRefEndpointSyncTracker> results = ESIRefEndpointSyncTracker.getAllStartedUnfinishedTrackers();
      return Response.ok()
                     .entity(results)
                     .build();
    } catch (IOException e) {
      log.log(Level.WARNING, "query error", e);
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error retrieving trackers.  Contact the administrator if this problem persists.");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @Path("/finish_ref_tracker/{tid}")
  @GET
  @ApiOperation(
      value = "Force a ref tracker to be marked finished if it's not finished already",
      notes = "Forces a ref tracker into the finished state whether it's been finished or not")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "finished successfully"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "Given tracker ID not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response requestFinishRefTracker(
      @Context HttpServletRequest request,
      @PathParam("tid") @ApiParam(
          name = "tid",
          required = true,
          value = "Ref Sync Tracker ID") long tid) {
    // Retrieve current logged in user
    EveKitUserAccount admin = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (admin == null || !admin.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED)
                     .entity(errMsg)
                     .build();
    }
    try {
      // Extract and finish tracker
      ESIRefEndpointSyncTracker tracker = ESIRefEndpointSyncTracker.get(tid);
      if (tracker.getSyncStart() <= 0) tracker.setSyncStart(OrbitalProperties.getCurrentTime());
      tracker.setStatus(ESISyncState.WARNING);
      tracker.setDetail("Tracker forced to finish by administrator request.");
      ESIRefEndpointSyncTracker.finishTracker(tracker);
      return Response.ok()
                     .build();
    } catch (TrackerNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target sync tracker not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Failed to finish tracker, contact admin for details");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

}
