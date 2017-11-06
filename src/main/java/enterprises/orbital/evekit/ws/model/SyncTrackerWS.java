package enterprises.orbital.evekit.ws.model;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.AccountNotFoundException;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.account.UserNotFoundException;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.RefSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.ws.account.AccountWS;
import enterprises.orbital.evekit.ws.common.ServiceError;
import enterprises.orbital.oauth.AuthUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

@Path("/ws/v1/tracker")
@Produces({
    "application/json"
})
@Api(
    tags = {
        "Account"
    },
    produces = "application/json")
public class SyncTrackerWS {
  @SuppressWarnings("unused")
  private static final Logger log                       = Logger.getLogger(SyncTrackerWS.class.getName());
  public static final int     DEF_MAX_CAP_SYNC_HISTORY  = 100;
  public static final int     DEF_MAX_CORP_SYNC_HISTORY = 100;
  public static final int     DEF_MAX_REF_SYNC_HISTORY  = 100;

  @Path("/cap_sync_history/{aid}")
  @GET
  @ApiOperation(
      value = "Retrieve synchronization history for a Capsuleer account",
      notes = "Retrieves capsuleer synchronization history ordered in descending order by sync start time")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "capsuleer sync history",
              response = CapsuleerSyncTracker.class,
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
  public Response requestCapsuleerSyncHistory(
                                              @Context HttpServletRequest request,
                                              @PathParam("aid") @ApiParam(
                                                  name = "aid",
                                                  required = true,
                                                  value = "Sync Account ID") long aid,
                                              @QueryParam("contid") @DefaultValue("-1") @ApiParam(
                                                  name = "contid",
                                                  required = false,
                                                  defaultValue = "-1",
                                                  value = "Optional sync start time before which results will be returned") long contid,
                                              @QueryParam("maxresults") @ApiParam(
                                                  name = "maxresults",
                                                  required = false,
                                                  value = "Maximum number of results to return") int maxResults) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return AccountWS.createUserNotLoggedResponse();

    try {
      // Retrieve SynchronizedEveAccount
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      // Set defaults
      maxResults = OrbitalProperties.getNonzeroLimited(maxResults, (int) PersistentProperty
          .getLongPropertyWithFallback(OrbitalProperties.getPropertyName(CapsuleerSyncTracker.class, "maxresults"), DEF_MAX_CAP_SYNC_HISTORY));
      // Retrieve and return history
      List<CapsuleerSyncTracker> results = CapsuleerSyncTracker.getHistory(account, contid, maxResults);
      return Response.ok()
                     .entity(results)
                     .build();
    } catch (AccountNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Sync account with the given ID not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving history, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @Path("/corp_sync_history/{aid}")
  @GET
  @ApiOperation(
      value = "Retrieve synchronization history for a Corporation account",
      notes = "Retrieves corporation synchronization history ordered in descending order by sync start time")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "corporation sync history",
              response = CorporationSyncTracker.class,
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
  public Response requestCorporationSyncHistory(
                                                @Context HttpServletRequest request,
                                                @PathParam("aid") @ApiParam(
                                                    name = "aid",
                                                    required = true,
                                                    value = "Sync Account ID") long aid,
                                                @QueryParam("contid") @DefaultValue("-1") @ApiParam(
                                                    name = "contid",
                                                    required = false,
                                                    defaultValue = "-1",
                                                    value = "Optional sync start time before which results will be returned") long contid,
                                                @QueryParam("maxresults") @ApiParam(
                                                    name = "maxresult",
                                                    required = false,
                                                    value = "Maximum number of results to return") int maxResults) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return AccountWS.createUserNotLoggedResponse();

    try {
    // Retrieve SynchronizedEveAccount
    SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

    // Set defaults
    maxResults = OrbitalProperties.getNonzeroLimited(maxResults, (int) PersistentProperty
        .getLongPropertyWithFallback(OrbitalProperties.getPropertyName(CorporationSyncTracker.class, "maxresults"), DEF_MAX_CORP_SYNC_HISTORY));
    // Retrieve and return history
    List<CorporationSyncTracker> results = CorporationSyncTracker.getHistory(account, contid, maxResults);
    return Response.ok().entity(results).build();
    } catch (AccountNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Sync account with the given ID not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving history, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @Path("/cap_sync_unfinished")
  @GET
  @ApiOperation(
      value = "Retrieve all unfinished capsuleer synchronization trackers",
      notes = "Retrieves capsuleer synchronization trackers which are not yet finished")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "list of unfinished capsuleer sync trackers",
              response = CapsuleerSyncTracker.class,
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
  public Response requestUnfinishedCapsuleerSync(
                                                 @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve and return unfinished
    List<CapsuleerSyncTracker> results = CapsuleerSyncTracker.getAllUnfinishedTrackers();
    return Response.ok().entity(results).build();
  }

  @Path("/corp_sync_unfinished")
  @GET
  @ApiOperation(
      value = "Retrieve all unfinished corporation synchronization trackers",
      notes = "Retrieves corporation synchronization trackers which are not yet finished")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "corporation sync history",
              response = CorporationSyncTracker.class,
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
  public Response requestUnfinishedCorporationSync(
                                                   @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve and return unfinished
    List<CorporationSyncTracker> results = CorporationSyncTracker.getAllUnfinishedTrackers();
    return Response.ok().entity(results).build();
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
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    try {
      // Extract user
      EveKitUserAccount user = EveKitUserAccount.getAccount(uid);

      // Extract account
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      // Extract tracker
      SyncTracker tracker = SyncTracker.get(account, tid);
      if (tracker == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target sync tracker not found");
        return Response.status(Status.NOT_FOUND)
                       .entity(errMsg)
                       .build();
      }
      // Finish tracker
      tracker = SyncTracker.finishTracker(tracker);
      if (tracker == null) {
        ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Failed to finish tracker, contact admin for details");
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity(errMsg)
                       .build();
      }
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
    } catch (IOException e) {
      // Database error
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error finishing tracker, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

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
              response = RefSyncTracker.class,
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
                                            required = false,
                                            defaultValue = "-1",
                                            value = "Optional sync start time before which results will be returned") long contid,
                                        @QueryParam("maxresults") @ApiParam(
                                            name = "maxresults",
                                            required = false,
                                            value = "Maximum number of results to return") int maxResults) {
    // Retrieve current logged in user. Must be an admin
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Set defaults
    maxResults = OrbitalProperties.getNonzeroLimited(maxResults, (int) PersistentProperty
        .getLongPropertyWithFallback(OrbitalProperties.getPropertyName(RefSyncTracker.class, "maxresults"), DEF_MAX_REF_SYNC_HISTORY));
    // Retrieve and return history
    List<RefSyncTracker> results = RefSyncTracker.getHistory(contid, maxResults);
    return Response.ok().entity(results).build();
  }

  @Path("/ref_sync_unfinished")
  @GET
  @ApiOperation(
      value = "Retrieve all unfinished ref data synchronization trackers",
      notes = "Retrieves ref data synchronization trackers which are not yet finished")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "list of unfinished ref data sync trackers",
              response = RefSyncTracker.class,
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
  public Response requestUnfinishedRefSync(
                                           @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null || !user.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or is not an administrator");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve and return unfinished
    List<RefSyncTracker> results = RefSyncTracker.getAllUnfinishedTrackers();
    return Response.ok().entity(results).build();
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
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Extract tracker
    RefSyncTracker tracker = RefSyncTracker.get(tid);
    if (tracker == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target sync tracker not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Finish tracker
    tracker = RefSyncTracker.finishTracker(tracker);
    if (tracker == null) {
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Failed to finish tracker, contact admin for details");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

}
