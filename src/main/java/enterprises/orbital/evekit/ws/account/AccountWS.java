package enterprises.orbital.evekit.ws.account;

import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.*;
import enterprises.orbital.evekit.model.ESIEndpointSyncTracker;
import enterprises.orbital.evekit.model.ESIScope;
import enterprises.orbital.evekit.model.TrackerNotFoundException;
import enterprises.orbital.evekit.ws.common.ServiceError;
import enterprises.orbital.oauth.AuthUtil;
import io.swagger.annotations.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

  /**
   * Checks whether the authenticated user is an administrator in cases where the ID of the authenticated user
   * is different than the ID of the user being acted upon.
   *
   * @param user authenticated user
   * @param uid  ID of the user being acted upon, or -1 if another user is NOT being acted upon
   * @return an error response if the authenticated user must be an admin but is not, otherwise null
   */
  public static Response checkRequireAdmin(EveKitUserAccount user, long uid) {
    if (user.getID() != uid && uid != -1) {
      if (!user.isAdmin()) {
        ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor must be an admin for this request");
        return Response.status(Status.UNAUTHORIZED)
                       .entity(errMsg)
                       .build();
      } else {
        try {
          EveKitUserAccount.getAccount(uid);
        } catch (UserNotFoundException | IOException e) {
          ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
          return Response.status(Status.NOT_FOUND)
                         .entity(errMsg)
                         .build();
        }
      }
    }
    return null;
  }

  /**
   * Generate a response for a user who is not logged in.
   *
   * @return ready to return error response.
   */
  public static Response createUserNotLoggedResponse() {
    ServiceError errMsg = new ServiceError(Status.UNAUTHORIZED.getStatusCode(), "Requestor not logged in");
    return Response.status(Status.UNAUTHORIZED)
                   .entity(errMsg)
                   .build();
  }

  /**
   * Retrieve list of sync accounts.
   *
   * @param request incoming HTTP request
   * @param uid     requesting user, or -1 for logged in user
   * @param aid     requested account, or -1 for all accounts
   * @return list of accounts
   */
  @Path("/sync_account/{uid}/{aid}")
  @GET
  @ApiOperation(
      value = "Get list of sync accounts for the given user or only a single account if an id is provided")
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
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Retrieve either target account or all accounts
      List<SynchronizedEveAccount> result = new ArrayList<>();
      if (aid != -1)
        result.add(SynchronizedEveAccount.getSynchronizedAccount(user, aid, true));
      else
        result.addAll(SynchronizedEveAccount.getAllAccounts(user, true));

      // Finish
      for (SynchronizedEveAccount next : result) {
        // Update last synchronized time
        try {
          next.setLastSynchronized(ESIEndpointSyncTracker.getAnyLatestFinishedTracker(next).getSyncEnd());
        } catch (TrackerNotFoundException e) {
          // ignore
        }
        next.updateValid();
      }
      return Response.ok()
                     .entity(result)
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Account with given ID not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving accounts, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Create or update a synchronized account.
   *
   * @param request  incoming HTTP request
   * @param uid      requesting user, or -1 to use the currently logged in user
   * @param aid      account to update, or -1 to create a new account
   * @param name     name of new account, or new name of existing account
   * @param charType if true, account will synchronize a character type.  False otherwise.  Ignored for updates to existing accounts.
   * @return newly created or modified account
   */
  @Path("/sync_account/{uid}/{aid}")
  @POST
  @ApiOperation(
      value = "Create or update a sync account.  Returns the result of applying the change.")
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
      @QueryParam("name") @ApiParam(
          name = "name",
          required = true,
          value = "Name of new account, or new name of existing account") String name,
      @QueryParam("charType") @ApiParam(
          name = "charType",
          required = true,
          value = "True if account will sync a character, false otherwise.  Ignored for updates to existing accounts.") boolean charType) {
    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    // Create or update account
    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Verify name is valid
      if (name == null || name.length() == 0 || name.length() > SynchronizedEveAccount.SYNC_ACCOUNT_NAME_MAX_LENGTH) {
        throw new IllegalArgumentException();
      }
      for (char el : name.toCharArray()) {
        if (!Character.isLetterOrDigit(el) && !(el == '_')) {
          throw new IllegalArgumentException();
        }
      }

      SynchronizedEveAccount result;
      if (aid == -1)
        // New account
        result = SynchronizedEveAccount.createSynchronizedEveAccount(user, name, charType);
      else
        // Update from passed value - legal changes are: name, autoSynchronized
        result = SynchronizedEveAccount.updateAccount(user, aid, name);

      result.updateValid();
      return Response.ok()
                     .entity(result)
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IllegalArgumentException e) {
      // Invalid account name
      ServiceError errMsg = new ServiceError(Status.BAD_REQUEST.getStatusCode(), "Sync account name must be an alphanumeric string (including underscores) no more than 100 characters in length.");
      return Response.status(Status.BAD_REQUEST)
                     .entity(errMsg)
                     .build();
    } catch (AccountCreationException e) {
      // New account but selected name already in use
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Requested account name already in use for user");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Account to update not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountUpdateException e) {
      // Attempt to update account in an illegal way
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Account name change requested, but new name already in use");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // An internal error occurred while performing the update.
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error processing change, contact admin if problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Delete sync account.
   *
   * @param request incoming HTTP request
   * @param uid     requesting user, or -1 for the currently logged in user
   * @param aid     account to delete
   * @return OK if marked for delete is successful
   */
  @Path("/sync_account/{uid}/{aid}")
  @DELETE
  @ApiOperation(
      value = "Delete a sync account.")
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
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have the proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Mark for deletion
      SynchronizedEveAccount.deleteAccount(user, aid);
      return Response.ok()
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // target account not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // An internal error occurred while performing the update.
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error marking account, contact admin if problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Restore an account marked for deletion.
   *
   * @param request incoming HTTP request
   * @param uid     requesting user, or -1 to use current logged in user
   * @param aid
   * @return
   */
  @Path("/restore_sync_account/{uid}/{aid}")
  @PUT
  @ApiOperation(
      value = "Restore a sync account previously marked for deletion.")
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
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Restore and return
      SynchronizedEveAccount.restoreAccount(user, aid);
      return Response.ok()
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Account to restore not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Internal error while performing the update
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Internal error marking account, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Get list of access keys.
   *
   * @param request incoming HTTP request
   * @param uid     user for which keys will be retrieved
   * @param aid     account for which keys will be retrieved
   * @param kid     specific key to retrieve, or -1 to retrieve all keys
   * @return the list of requested keys
   */
  @Path("/access_key/{uid}/{aid}/{kid}")
  @GET
  @ApiOperation(
      value = "Get list of access keys for the given user and account id")
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
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Target account required - find it
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      // Retrieve either target key or all keys
      List<SynchronizedAccountAccessKey> result = new ArrayList<SynchronizedAccountAccessKey>();
      if (kid != -1)
        result.add(SynchronizedAccountAccessKey.getKeyByOwnerAndID(account, kid));
      else
        result.addAll(SynchronizedAccountAccessKey.getAllKeys(account));

      // Make sure transient fields are properly generated before returning result
      for (SynchronizedAccountAccessKey nextKey : result) {
        nextKey.generateMaskValue();
        nextKey.generateMaskValueString();
        nextKey.generateCredential();
      }

      // Finish
      return Response.ok()
                     .entity(result)
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccessKeyNotFoundException e) {
      // Requested access key could not be found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Key with given ID not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Owning account not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Error retrieving key list
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving access keys, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Create or update an access key.
   *
   * @param request incoming HTTP request
   * @param uid     user for which key will be created or updated
   * @param aid     account for which key will be created or updated
   * @param kid     key to modify, or -1 to create a new key
   * @param key     key containing values to store or copy to an updated key
   * @return created or updated access key
   */
  @Path("/access_key/{uid}/{aid}/{kid}")
  @POST
  @ApiOperation(
      value = "Create or update an access key.  Returns the result of applying the change.")
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
    // Verify post argument
    if (key == null) {
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "POST argument is null");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    }

    // Retrieve user and verify as needed
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Target account required - find it
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      // Incoming access mask is ignored. Instead we read the maskValueString and convert to an appropriate byte array
      byte[] maskUpload = AccountAccessMask.unstringifyMask(key.getMaskValueString());

      // Target key determines whether this is a new key or an update
      SynchronizedAccountAccessKey result;
      if (kid == -1) {
        // Create new account
        result = SynchronizedAccountAccessKey.createKey(account, key.getKeyName(), key.getExpiry(), key.getLimit(), maskUpload);
      } else {
        // Update - find the key
        SynchronizedAccountAccessKey existing = SynchronizedAccountAccessKey.getKeyByOwnerAndID(account, kid);

        // Update from passed value - legal changes are: name, expiry, limit, accessMask
        result = SynchronizedAccountAccessKey.updateKey(account, existing.getKeyName(), key.getKeyName(), key.getExpiry(), key.getLimit(), maskUpload);
      }
      // Make sure transient values are generated properly before returning result
      result.generateMaskValue();
      result.generateMaskValueString();
      result.generateCredential();

      return Response.ok()
                     .entity(result)
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccessKeyUpdateException e) {
      // Update violated a constraint
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Key name change requested, but new name already in use");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    } catch (AccessKeyCreationException e) {
      // Access key could not be created or modified
      ServiceError errMsg = new ServiceError(Status.FORBIDDEN.getStatusCode(), "Requested access key name already in use for this account");
      return Response.status(Status.FORBIDDEN)
                     .entity(errMsg)
                     .build();
    } catch (AccessKeyNotFoundException e) {
      // Requested access key could not be found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Key with given ID not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Owning account not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Error retrieving key list
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error modifying access key, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Delete access key.
   *
   * @param request incoming HTTP request
   * @param uid     user owning key to be deleted
   * @param aid     account owning key to be deleted
   * @param kid     ID of key to be deleted
   * @return OK status if delete is successful.
   */
  @Path("/access_key/{uid}/{aid}/{kid}")
  @DELETE
  @ApiOperation(
      value = "Delete an access key.")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "access key deleted successfully"),
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
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Target account required - find it
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, false);

      // Target key required - find it
      SynchronizedAccountAccessKey key = SynchronizedAccountAccessKey.getKeyByOwnerAndID(account, kid);

      // Delete and return
      SynchronizedAccountAccessKey.deleteKey(account, kid);
      return Response.ok()
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccessKeyNotFoundException e) {
      // Requested access key could not be found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Key with given ID not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Owning account not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Error retrieving key list
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error deleting access key, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Get the last source used to login for the specified user (or currently logged in user).
   *
   * @param request incoming HTTP request
   * @param uid     user for which we're retrieving the source, or -1 for the currently authenticated user.
   * @return the last source used to authenticate for the specified user.
   */
  @Path("/user_last_source/{uid}")
  @GET
  @ApiOperation(
      value = "Get the last user auth source used by the given user, or the currently logged in user")
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
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Retrieve and return source
      EveKitUserAuthSource src = EveKitUserAuthSource.getLastUsedSource(user);
      if (src == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving auth source, please contact the administrator if this error persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity(errMsg)
                       .build();
      }
      return Response.ok()
                     .entity(src)
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Error retrieving key list
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving last user source, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Return the list of all auth sources for the specified user (or the currently logged in user).
   *
   * @param request incoming HTTP request
   * @param uid     the user for which sources will be retrieved, or -1 for the currently authenticated user
   * @return the list of auth sources for the specified user.
   */
  @Path("/user_sources/{uid}")
  @GET
  @ApiOperation(
      value = "Get the list of all user auth sources for the given user, or the currently logged in user")
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
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Look up sources
      List<EveKitUserAuthSource> sources = new ArrayList<EveKitUserAuthSource>();
      List<EveKitUserAuthSource> result = EveKitUserAuthSource.getAllSources(user);
      if (result == null) {
        ServiceError errMsg = new ServiceError(
            Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving auth source, please contact the administrator if this error persists");
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity(errMsg)
                       .build();
      }
      sources.addAll(result);
      return Response.ok()
                     .entity(sources)
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Error retrieving key list
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving user sources, contact admin if this problem persists");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Get the currently authenticated user.
   *
   * @param request incoming HTTP request
   * @return the currently authenticated user, or null if no user authenticated
   */
  @Path("/user")
  @GET
  @ApiOperation(
      value = "Get information about the current logged in user")
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
    return Response.ok()
                   .entity(user)
                   .build();
  }

  /**
   * List all users who have authenticated at least once with the site.
   * This is an admin only service.
   *
   * @param request incoming HTTP request
   * @return the list of all authenticated users.
   */
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
      return Response.status(Status.UNAUTHORIZED)
                     .entity(errMsg)
                     .build();
    }
    // Retrieve list and finish
    try {
      List<EveKitUserAccount> allUsers = EveKitUserAccount.getAllAccounts();
      if (allUsers == null) {
        ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving user list, check logs");
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity(errMsg)
                       .build();
      }
      return Response.ok()
                     .entity(allUsers)
                     .build();
    } catch (IOException e) {
      // Error retrieving key list
      ServiceError errMsg = new ServiceError(
          Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error retrieving user list");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Change disabled status of a synchronized account.  This setting is set in a persistent property
   * and is not stored in the account itself.
   *
   * @param request  incoming HTTP request
   * @param uid      user which owns the account to be changed
   * @param aid      account to be changed
   * @param disabled true if the account should be disabled, false otherwise
   * @return OK if the setting was changed successfully.
   */
  @Path("/toggle_account_disabled/{uid}/{aid}/{disabled}")
  @GET
  @ApiOperation(
      value = "Change the disabled state of a synchronized account")
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
  public Response toggleAccountDisabled(
      @Context HttpServletRequest request,
      @PathParam("uid") @ApiParam(
          name = "uid",
          required = true,
          value = "ID of user account to toggle") long uid,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "ID of sync account to toggle") long aid,
      @PathParam("disabled") @ApiParam(
          name = "disabled",
          required = true,
          value = "account disabled status") boolean disabled) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Retrieve target account
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      // Change state and finish
      PersistentProperty.setProperty(account, "disabled", String.valueOf(disabled));
      return Response.ok()
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Target account not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Error toggling sync state
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error changing autosync state, check logs");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  @Path("/is_account_disabled/{uid}/{aid}")
  @GET
  @ApiOperation(
      value = "Check whether a synchronized account is disabled")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "status returned successfully"),
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
  public Response isAccountDisabled(
      @Context HttpServletRequest request,
      @PathParam("uid") @ApiParam(
          name = "uid",
          required = true,
          value = "ID of user account to toggle") long uid,
      @PathParam("aid") @ApiParam(
          name = "aid",
          required = true,
          value = "ID of sync account to toggle") long aid) {
    // Retrieve current logged in user
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Retrieve target account
      SynchronizedEveAccount account = SynchronizedEveAccount.getSynchronizedAccount(user, aid, true);

      return Response.ok()
                     .entity(new Object() {
                       @SuppressWarnings("unused")
                       public final boolean isDisabled = PersistentProperty.getBooleanPropertyWithFallback(account, "disabled", false);
                     })
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (AccountNotFoundException e) {
      // Target account not found
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target account not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      // Error toggling sync state
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error reading account status, check logs");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Change the active state for a user.
   *
   * @param request incoming HTTP request
   * @param uid     user for which state will be changed
   * @param active  new active state for user
   * @return OK if state changed successfully.
   */
  @Path("/toggle_user_active/{uid}/{active}")
  @GET
  @ApiOperation(
      value = "Change the active state of a user")
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
    EveKitUserAccount user = (EveKitUserAccount) AuthUtil.getCurrentUser(request);
    if (user == null) return createUserNotLoggedResponse();
    Response error = checkRequireAdmin(user, uid);
    if (error != null) return error;

    try {
      // Ensure we have proper user
      if (uid != -1) user = EveKitUserAccount.getAccount(uid);

      // Change state and finish
      user.setActive(active);

      EveKitUserAccount.update(user);
      return Response.ok()
                     .build();
    } catch (UserNotFoundException e) {
      ServiceError errMsg = new ServiceError(Status.NOT_FOUND.getStatusCode(), "Target user not found");
      return Response.status(Status.NOT_FOUND)
                     .entity(errMsg)
                     .build();
    } catch (IOException e) {
      ServiceError errMsg = new ServiceError(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Error changing active state, check logs");
      return Response.status(Status.INTERNAL_SERVER_ERROR)
                     .entity(errMsg)
                     .build();
    }
  }

  /**
   * Check whether the authenticated user is an admin.
   *
   * @param request incoming HTTP request
   * @return an object containing an "isAdmin" field which is true if the current user is an admin.
   */
  @Path("/isadmin")
  @GET
  @ApiOperation(
      value = "Check whether the current user is an admin")
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
    if (user == null) return createUserNotLoggedResponse();
    // Retrieve and return unfinished
    return Response.ok()
                   .entity(new Object() {
                     @SuppressWarnings("unused")
                     public final boolean isAdmin = user.isAdmin();
                   })
                   .build();
  }

  /**
   * Return list of character ESI endpoints.
   *
   * @param request incoming HTTP request
   * @return the list of all character ESI endpoints.
   */
  @Path("/list_char_endpoints")
  @GET
  @ApiOperation(value = "List all character ESI endpoints")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "endpoint list",
              response = ESIScopeDescription.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response charEndpoints(
      @Context HttpServletRequest request) {
    // Retrieve list and finish
    List<ESIScopeDescription> results = new ArrayList<>();
    for (ESIScope next : ESIScope.getCharScopes())
      results.add(ESIScopeDescription.fromScope(next));
      return Response.ok()
                     .entity(results)
                     .build();
  }

  /**
   * Return list of corporation ESI endpoints.
   *
   * @param request incoming HTTP request
   * @return the list of all corporation ESI endpoints.
   */
  @Path("/list_corp_endpoints")
  @GET
  @ApiOperation(value = "List all corporation ESI endpoints")
  @ApiResponses(
      value = {
          @ApiResponse(
              code = 200,
              message = "endpoint list",
              response = ESIScopeDescription.class,
              responseContainer = "array"),
          @ApiResponse(
              code = 500,
              message = "Internal account service service error",
              response = ServiceError.class),
      })
  public Response corpEndpoints(
      @Context HttpServletRequest request) {
    // Retrieve list and finish
    List<ESIScopeDescription> results = new ArrayList<>();
    for (ESIScope next : ESIScope.getCorpScopes())
      results.add(ESIScopeDescription.fromScope(next));
    return Response.ok()
                   .entity(results)
                   .build();
  }

}
