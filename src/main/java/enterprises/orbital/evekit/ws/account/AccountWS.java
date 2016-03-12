package enterprises.orbital.evekit.ws.account;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import enterprises.orbital.evekit.account.AccessKeyCreationException;
import enterprises.orbital.evekit.account.AccountAccessMask;
import enterprises.orbital.evekit.account.AccountCreationException;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.EveKitUserAuthSource;
import enterprises.orbital.evekit.account.SynchronizedAccountAccessKey;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.ws.common.ServiceError;
import enterprises.orbital.oauth.AuthUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Path("/ws/v1/account")
@Consumes({
    "application/json"
})
@Produces({
    "application/json"
})
@Api(
    tags = {
        "Account"
},
    produces = "application/json",
    consumes = "application/json")
public class AccountWS {

  @Path("/sync_account/{uid}/{aid}")
  @GET
  @ApiOperation(
      value = "Get list of sync accounts for the given user and, optionally, the given account id",
      notes = "If aid != -1, then return a list containing the specified sync account, otherwise return the list of all accounts for the given user")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "list of sync accounts",
              response = SynchronizedEveAccount.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "specified user or sync account not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response getSyncAccount(
                                 @Context HttpServletRequest request,
                                 @PathParam("uid") @ApiParam(
                                     name = "uid",
                                     required = true,
                                     value = "ID of user for which sync accounts will be retrieved.  Set to -1 to retrieve for the current logged in user.") long uid,
                                 @PathParam("aid") @ApiParam(
                                     name = "aid",
                                     required = true,
                                     value = "ID of single sync account to retrieve.  Set to -1 to retrieve all sync accounts for the given user.") long aid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Retrieve either target account or all accounts
    List<SynchronizedEveAccount> result = new ArrayList<SynchronizedEveAccount>();
    if (aid != -1) {
      SynchronizedEveAccount sa = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
      if (sa == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Account with given ID not found");
        return Response.status(Status.NOT_FOUND).entity(errMsg).build();
      }
      result.add(sa);
    } else {
      List<SynchronizedEveAccount> accounts = SynchronizedEveAccount.getAllAccounts(user, true);
      if (accounts == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving accounts, contact admin if this problem persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
      result.addAll(accounts);
    }
    // Finish
    for (SynchronizedEveAccount next : result) {
      // Update last synchronized time
      SyncTracker tracker = SyncTracker.getLatestFinishedTracker(next);
      if (tracker != null) next.setLastSynchronized(tracker.getSyncEnd());
    }
    return Response.ok().entity(result).build();
  }

  @Path("/sync_account/{uid}/{aid}")
  @POST
  @ApiOperation(
      value = "Create or update a sync account.  Returns the result of applying the change.",
      notes = "If aid = -1, then create a new sync account with the given parameters, otherwise update an existing account")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "account saved or updated successfully",
              response = SynchronizedEveAccount.class),
          @ApiResponse(
              code = 401,
              message = "either the requestor is not logged in, or requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 403,
              message = "provided sync account has an illegal value.  More details are provided in the response message.",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting for other than logged in user, but target user not found; or, updating an existing account but target account not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response saveSyncAccount(
                                  @Context HttpServletRequest request,
                                  @PathParam("uid") @ApiParam(
                                      name = "uid",
                                      required = true,
                                      value = "ID of user for which a sync account will be updated or saved.  Set to -1 for the current logged in user.") long uid,
                                  @PathParam("aid") @ApiParam(
                                      name = "aid",
                                      required = true,
                                      value = "ID of sync account to update.  Set to -1 to save a new account.") long aid,
                                  @ApiParam(
                                      name = "account",
                                      required = true,
                                      value = "Account to save or update") SynchronizedEveAccount account) {
    SynchronizedEveAccount result = null;
    // Verify post argument
    if (account == null) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "POST argument is null");
      return Response.status(Status.FORBIDDEN).entity(errMsg).build();
    }
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Target account determines whether this is a new account or an update
    if (aid == -1) {
      // New account
      try {
        result = SynchronizedEveAccount.createSynchronizedEveAccount(user, account.getName(), account.isCharacterType(), account.isAutoSynchronized(),
                                                                     account.getEveKey(), account.getEveVCode(), account.getEveCharacterID(),
                                                                     account.getEveCharacterName(), account.getEveCorporationID(),
                                                                     account.getEveCorporationName());
      } catch (AccountCreationException e) {
        ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Requested account name already in use for this user account");
        return Response.status(Status.FORBIDDEN).entity(errMsg).build();
      }
    } else {
      // Update - find the account
      SynchronizedEveAccount existing = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
      if (existing == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
        return Response.status(Status.NOT_FOUND).entity(errMsg).build();
      }
      // Update from passed value - legal changes are: name, autoSynchronized, eveKey, eveVCode, eveCharacterID, eveCharacterName, eveCorporationID,
      // eveCorporationName
      //
      // A name change requires verification that the given name is not already in use.
      try {
        SynchronizedEveAccount.updateAccount(user, existing.getAid(), account.getName(), existing.isCharacterType(), account.isAutoSynchronized(),
                                             account.getEveKey(), account.getEveVCode(), account.getEveCharacterID(), account.getEveCharacterName(),
                                             account.getEveCorporationID(), account.getEveCorporationName());
      } catch (AccountCreationException e) {
        ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Account name change requested, but new name already in use");
        return Response.status(Status.FORBIDDEN).entity(errMsg).build();
      }
      result = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
      if (result == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error processing change, contact admin if problem persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
    }
    return Response.ok().entity(result).build();
  }

  @Path("/sync_account/{uid}/{aid}")
  @DELETE
  @ApiOperation(
      value = "Delete a sync account.",
      notes = "Delete the specified account, optionally for the specified user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "account marked for delete successfully"),
          @ApiResponse(
              code = 401,
              message = "either the requestor is not logged in, or requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting for other than logged in user, but target user not found; or, deleting an existing account but target account not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response deleteSyncAccount(
                                    @Context HttpServletRequest request,
                                    @PathParam("uid") @ApiParam(
                                        name = "uid",
                                        required = true,
                                        value = "ID of user for which a sync account will be marked for delete.  Set to -1 for the current logged in user.") long uid,
                                    @PathParam("aid") @ApiParam(
                                        name = "aid",
                                        required = true,
                                        value = "ID of sync account to mark.") long aid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Target account required - find it
    SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, false);
    if (account == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    if (account.getMarkedForDelete() != -1) return Response.ok().build();
    // Mark for delete and return
    if (SynchronizedEveAccount.deleteAccount(user, aid) == null) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error marking account, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

  @Path("/restore_sync_account/{uid}/{aid}")
  @PUT
  @ApiOperation(
      value = "Restore a sync account previously marked for deletion.",
      notes = "Restore the specified account, optionally for the specified user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "account restored successfully"),
          @ApiResponse(
              code = 401,
              message = "either the requestor is not logged in, or requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting for other than logged in user, but target user not found; or, restoring an existing account but target account not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response restoreSyncAccount(
                                     @Context HttpServletRequest request,
                                     @PathParam("uid") @ApiParam(
                                         name = "uid",
                                         required = true,
                                         value = "ID of user for which a sync account will be restored.  Set to -1 for the current logged in user.") long uid,
                                     @PathParam("aid") @ApiParam(
                                         name = "aid",
                                         required = true,
                                         value = "ID of sync account to restore.") long aid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Target account required - find it
    SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
    if (account == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    if (account.getMarkedForDelete() == -1) return Response.ok().build();
    // Restore and return
    if (SynchronizedEveAccount.restoreAccount(user, aid) == null) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error marking account, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

  @Path("/start_sync/{uid}/{aid}")
  @GET
  @ApiOperation(
      value = "Request a sync of the given account and, optionally, the given user id",
      notes = "Initiates a sync request for the given account.  The sync will only occur if sufficient time has elapsed since the last sync for this account.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "sync request initiated"),
          @ApiResponse(
              code = 401,
              message = "requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "specified user or sync account not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response requestSync(
                              @Context HttpServletRequest request,
                              @PathParam("uid") @ApiParam(
                                  name = "uid",
                                  required = true,
                                  value = "ID of user for which a sync will be initiated.  Set to -1 to retrieve for the current logged in user.") long uid,
                              @PathParam("aid") @ApiParam(
                                  name = "aid",
                                  required = true,
                                  value = "ID of sync account for which a sync will be initiated.") long aid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Retrieve target account
    SynchronizedEveAccount sa = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
    if (sa == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Account with given ID not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Create a tracker for this account. This will cause the sync manager to attempt a sync if possible
    if (sa.isCharacterType()) {
      if (CapsuleerSyncTracker.createOrGetUnfinishedTracker(sa) == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error creating sync request.  If this problem persists, please contact the system administrator.");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
    } else {
      if (CorporationSyncTracker.createOrGetUnfinishedTracker(sa) == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error creating sync request.  If this problem persists, please contact the system administrator.");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
    }
    // Finish
    return Response.ok().build();
  }

  @Path("/access_key/{uid}/{aid}/{kid}")
  @GET
  @ApiOperation(
      value = "Get list of access keys for the given user and account id",
      notes = "If kid != -1, then return a list containing the specified access key, otherwise return the list of all access keys for the given user and account")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "list of access keys",
              response = SynchronizedAccountAccessKey.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "specified user, sync account, or access key not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response getAccessKey(
                               @Context HttpServletRequest request,
                               @PathParam("uid") @ApiParam(
                                   name = "uid",
                                   required = true,
                                   value = "ID of user for which access keys will be retrieved.  Set to -1 to retrieve for the current logged in user.") long uid,
                               @PathParam("aid") @ApiParam(
                                   name = "aid",
                                   required = true,
                                   value = "ID of sync account for which keys will be retrieved.") long aid,
                               @PathParam("kid") @ApiParam(
                                   name = "kid",
                                   required = true,
                                   value = "ID of access key to retrieve, or -1 to retrieve all access keys for the given account.") long kid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Target account required - find it
    SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
    if (account == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Retrieve either target key or all keys
    List<SynchronizedAccountAccessKey> result = new ArrayList<SynchronizedAccountAccessKey>();
    if (kid != -1) {
      SynchronizedAccountAccessKey sa = SynchronizedAccountAccessKey.getKeyByOwnerAndID(account, kid);
      if (sa == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Key with given ID not found");
        return Response.status(Status.NOT_FOUND).entity(errMsg).build();
      }
      result.add(sa);
    } else {
      List<SynchronizedAccountAccessKey> keys = SynchronizedAccountAccessKey.getAllKeys(account);
      if (keys == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving access keys, contact admin if this problem persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
      result.addAll(keys);
    }
    // Make sure transient fields are properly generated before returning result
    for (SynchronizedAccountAccessKey nextKey : result) {
      nextKey.generateMaskValue();
      nextKey.generateMaskValueString();
      nextKey.generateCredential();
    }
    // Finish
    return Response.ok().entity(result).build();
  }

  @Path("/access_key/{uid}/{aid}/{kid}")
  @POST
  @ApiOperation(
      value = "Create or update an access key.  Returns the result of applying the change.",
      notes = "If kid = -1, then create a new access key with the given parameters, otherwise update an existing access key")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "access key saved or updated successfully",
              response = SynchronizedEveAccount.class),
          @ApiResponse(
              code = 401,
              message = "either the requestor is not logged in, or requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 403,
              message = "provided access key has an illegal value.  More details are provided in the response message.",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting for other than logged in user, but target user not found; or, target account not found; or, updating an existing access key, but target key not found ",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response saveAccessKey(
                                @Context HttpServletRequest request,
                                @PathParam("uid") @ApiParam(
                                    name = "uid",
                                    required = true,
                                    value = "ID of user for which a access key will be updated or saved.  Set to -1 for the current logged in user.") long uid,
                                @PathParam("aid") @ApiParam(
                                    name = "aid",
                                    required = true,
                                    value = "ID of sync account which owns new or updated key.") long aid,
                                @PathParam("kid") @ApiParam(
                                    name = "kid",
                                    required = true,
                                    value = "ID of access key to update.  Set to -1 to save a new access key.") long kid,
                                @ApiParam(
                                    name = "key",
                                    required = true,
                                    value = "Access key to save or update") SynchronizedAccountAccessKey key) {
    SynchronizedAccountAccessKey result = null;
    // Verify post argument
    if (key == null) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "POST argument is null");
      return Response.status(Status.FORBIDDEN).entity(errMsg).build();
    }
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Target account required - find it
    SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
    if (account == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Target key determines whether this is a new key or an update
    if (kid == -1) {
      // New account
      try {
        // Incoming access mask is ignored. Instead we read the maskValueString and convert to an appropriate byte array
        byte[] maskUpload = AccountAccessMask.unstringifyMask(key.getMaskValueString());
        result = SynchronizedAccountAccessKey.createKey(account, key.getKeyName(), key.getExpiry(), key.getLimit(), maskUpload);
      } catch (AccessKeyCreationException e) {
        ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Requested access key name already in use for this account");
        return Response.status(Status.FORBIDDEN).entity(errMsg).build();
      }
    } else {
      // Update - find the key
      SynchronizedAccountAccessKey existing = SynchronizedAccountAccessKey.getKeyByOwnerAndID(account, kid);
      if (existing == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target access key not found");
        return Response.status(Status.NOT_FOUND).entity(errMsg).build();
      }
      // Update from passed value - legal changes are: name, expiry, limit, accessMask
      //
      // A name change requires verification that the given name is not already in use.
      try {
        // Incoming access mask is ignored. Instead we read the maskValueString and convert to an appropriate byte array
        byte[] maskUpload = AccountAccessMask.unstringifyMask(key.getMaskValueString());
        SynchronizedAccountAccessKey.updateKey(account, existing.getKeyName(), key.getKeyName(), key.getExpiry(), key.getLimit(), maskUpload);
      } catch (AccessKeyCreationException e) {
        ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Key name change requested, but new name already in use");
        return Response.status(Status.FORBIDDEN).entity(errMsg).build();
      }
      result = SynchronizedAccountAccessKey.getKeyByOwnerAndID(account, kid);
      if (result == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error processing change, contact admin if problem persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
      // Make sure transient values are generated properly before returning result
      result.generateMaskValue();
      result.generateMaskValueString();
      result.generateCredential();
    }
    return Response.ok().entity(result).build();
  }

  @Path("/access_key/{uid}/{aid}/{kid}")
  @DELETE
  @ApiOperation(
      value = "Delete an access key.",
      notes = "Delete the specified access key, optionally for the specified user.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "acces key deleted successfully"),
          @ApiResponse(
              code = 401,
              message = "either the requestor is not logged in, or requesting for other than logged in user but requestor not logged in or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting for other than logged in user, but target user not found; or, target account not found; or, target key not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response deleteAccessKey(
                                  @Context HttpServletRequest request,
                                  @PathParam("uid") @ApiParam(
                                      name = "uid",
                                      required = true,
                                      value = "ID of user for which a access key will be deleted.  Set to -1 for the current logged in user.") long uid,
                                  @PathParam("aid") @ApiParam(
                                      name = "aid",
                                      required = true,
                                      value = "ID of sync account for which an access key will be deleted.") long aid,
                                  @PathParam("kid") @ApiParam(
                                      name = "kid",
                                      required = true,
                                      value = "ID of access key to delete.") long kid) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
      } else {
        user = EveKitUserAccount.getAccount(uid);
        if (user == null) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND).entity(errMsg).build();
        }
      }
    }
    // Target account required - find it
    SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, false);
    if (account == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Target key required - find it
    SynchronizedAccountAccessKey key = SynchronizedAccountAccessKey.getKeyByOwnerAndID(account, kid);
    if (key == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target key not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Delete and return
    if (!SynchronizedAccountAccessKey.deleteKey(account, kid)) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error deleting access key, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

  @Path("/user_last_source/{uid}")
  @GET
  @ApiOperation(
      value = "Get the last user auth source used by the given user, or the currently logged in user",
      notes = "The last user auth source for the specified user, or null if the user is not logged in")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "last user auth source, or null",
              response = EveKitUserAuthSource.class),
          @ApiResponse(
              code = 401,
              message = "requesting source for other than local user, but requestor not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting source for other than local user, but specified user not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response getUserLastSource(
                                    @Context HttpServletRequest request,
                                    @PathParam("uid") @ApiParam(
                                        name = "uid",
                                        required = true,
                                        value = "ID of user account for which the last source will be retrieved.  Set to -1 to retrieve for the current logged in user.") long uid) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    EveKitUserAuthSource src = null;
    // If requesting for other than the logged in user, check admin
    if (user == null || (user.getID() != uid && uid != -1 && !user.isAdmin())) {
      ServiceError errMsg = new ServiceError(
          Status.UNAUTHORIZED.getStatusCode(), "Requesting source for other than local user, but requestor not logged in or not admin");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // If requesting for other than the logged in user, find user
    if (uid != -1) {
      user = EveKitUserAccount.getAccount(uid);
      if (user == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Requesting source for other than local user, but target user not found");
        return Response.status(Status.NOT_FOUND).entity(errMsg).build();
      }
    }
    // If we found an appropriate user, then look up the source
    if (user != null) {
      src = EveKitUserAuthSource.getLastUsedSource(user);
      if (src == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving auth source, please contact the administrator if this error persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
    }
    return Response.ok().entity(src).build();
  }

  @Path("/user_sources/{uid}")
  @GET
  @ApiOperation(
      value = "Get the list of all user auth sources for the given user, or the currently logged in user",
      notes = "The list of all user auth source for the specified user, or the empty list if the user is not logged in")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "List of all user auth sources, or empty list",
              response = EveKitUserAuthSource.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 401,
              message = "requesting source for other than local user, but requestor not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "requesting source for other than local user, but specified user not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response getUserSources(
                                 @Context HttpServletRequest request,
                                 @PathParam("uid") @ApiParam(
                                     name = "uid",
                                     required = true,
                                     value = "ID of user account for which all sources will be retrieved.  Set to -1 to retrieve for the current logged in user.") long uid) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    // If requesting for other than the logged in user, check admin
    if (user == null || (user.getID() != uid && uid != -1 && !user.isAdmin())) {
      ServiceError errMsg = new ServiceError(
          Status.UNAUTHORIZED.getStatusCode(), "Requesting source for other than local user, but requestor not logged in or not admin");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // If requesting for other than the logged in user, find user
    if (uid != -1) {
      user = EveKitUserAccount.getAccount(uid);
      if (user == null) {
        ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Requesting source for other than local user, but target user not found");
        return Response.status(Status.NOT_FOUND).entity(errMsg).build();
      }
    }
    // If we found an appropriate user, then look up sources
    List<EveKitUserAuthSource> sources = new ArrayList<EveKitUserAuthSource>();
    if (user != null) {
      List<EveKitUserAuthSource> result = EveKitUserAuthSource.getAllSources(user);
      if (result == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving auth source, please contact the administrator if this error persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
      }
      sources.addAll(result);
    }
    return Response.ok().entity(sources).build();
  }

  @Path("/user")
  @GET
  @ApiOperation(
      value = "Get information about the current logged in user",
      notes = "User information about the current logged in user, or null if no user logged in")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "logged in user, or null",
              response = EveKitUserAccount.class),
  })
  public Response getUser(
                          @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    return Response.ok().entity(user).build();
  }

  @Path("/list_users")
  @GET
  @ApiOperation(
      value = "List all site users",
      notes = "List all site users")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "user list",
              response = EveKitUserAccount.class,
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
  public Response listUsers(
                            @Context HttpServletRequest request) {
    // Retrieve current logged in user
    EveKitUserAccount admin = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (admin == null || !admin.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or not an admin");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve list and finish
    List<EveKitUserAccount> allUsers = EveKitUserAccount.getAllAccounts();
    if (allUsers == null) {
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving user list, check logs");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().entity(allUsers).build();
  }

  @Path("/toggle_auto_sync/{uid}/{aid}/{autosync}")
  @GET
  @ApiOperation(
      value = "Change the autosync state of a synchronized account",
      notes = "Set the given synchronized account to manual or auto sync, as specified")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "status successfully changed"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "User with the specified ID not found, or account with specified ID not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response toggleAutoSync(
                                 @Context HttpServletRequest request,
                                 @PathParam("uid") @ApiParam(
                                     name = "uid",
                                     required = true,
                                     value = "ID of user account to toggle") long uid,
                                 @PathParam("aid") @ApiParam(
                                     name = "aid",
                                     required = true,
                                     value = "ID of sync account to toggle") long aid,
                                 @PathParam("autosync") @ApiParam(
                                     name = "autosync",
                                     required = true,
                                     value = "New autosync state for user") boolean autosync) {
    // Retrieve current logged in user
    EveKitUserAccount admin = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (admin == null || !admin.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or not an admin");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve target user
    EveKitUserAccount user = EveKitUserAccount.getAccount(uid);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Retrieve target account
    SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);
    if (account == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Change state and finish
    account.setAutoSynchronized(autosync);
    if (SynchronizedEveAccount.update(account) == null) {
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error changing autosync state, check logs");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

  @Path("/toggle_user_active/{uid}/{active}")
  @GET
  @ApiOperation(
      value = "Change the active state of a user",
      notes = "Set the given user to active or inactive, as specified")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "status successfully changed"),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated or not an admin",
              response = ServiceError.class),
          @ApiResponse(
              code = 404,
              message = "User with the specified ID not found",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response toggleActive(
                               @Context HttpServletRequest request,
                               @PathParam("uid") @ApiParam(
                                   name = "uid",
                                   required = true,
                                   value = "ID of user account to toggle") long uid,
                               @PathParam("active") @ApiParam(
                                   name = "active",
                                   required = true,
                                   value = "New active state for user") boolean active) {
    // Retrieve current logged in user
    EveKitUserAccount admin = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (admin == null || !admin.isAdmin()) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in or not an admin");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve target user
    EveKitUserAccount user = EveKitUserAccount.getAccount(uid);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND).entity(errMsg).build();
    }
    // Change state and finish
    user.setActive(active);
    if (EveKitUserAccount.update(user) == null) {
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error changing active state, check logs");
      return Response.status(Status.INTERNAL_SERVER_ERROR).entity(errMsg).build();
    }
    return Response.ok().build();
  }

  @Path("/isadmin")
  @GET
  @ApiOperation(
      value = "Check whether the current user is an admin",
      notes = "Returns true if the current user is logged in and admin, false otherwise")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "admin status of logged in user",
              response = Boolean.class),
          @ApiResponse(
              code = 401,
              message = "requesting user not authenticated",
              response = ServiceError.class),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
  })
  public Response checkAdmin(
                             @Context HttpServletRequest request) {
    // Retrieve current logged in user
    final EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) {
      ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "User not logged in");
      return Response.status(Status.UNAUTHORIZED).entity(errMsg).build();
    }
    // Retrieve and return unfinished
    return Response.ok().entity(new Object() {
      @SuppressWarnings("unused")
      public final boolean isAdmin = user.isAdmin();
    }).build();
  }

}
