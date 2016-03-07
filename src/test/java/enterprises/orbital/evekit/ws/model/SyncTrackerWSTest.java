package enterprises.orbital.evekit.ws.model;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;

public class SyncTrackerWSTest extends TestBase {

  @Test
  public void testRequestCapsuleerSyncHistory_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestCapsuleerSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestCapsuleerSyncHistory_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestCapsuleerSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestCapsuleerSyncHistory_AccountNotFound() {
    HttpServletRequest mock = setupRequestMock(regularUser.getUid(), regularSource.getSource());
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestCapsuleerSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(404, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestCapsuleerSyncHistory_ResultsReturned() {
    // TODO
  }

  @Test
  public void testRequestCapsuleerSyncHistory_ContIDHonored() {
    // TODO
  }

  @Test
  public void testRequestCapsuleerSyncHistory_MaxResultsHonored() {
    // TODO
  }

  @Test
  public void testRequestCorporationSyncHistory_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestCorporationSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestCorporationSyncHistory_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestCorporationSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestCorporationSyncHistory_AccountNotFound() {
    HttpServletRequest mock = setupRequestMock(regularUser.getUid(), regularSource.getSource());
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestCorporationSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(404, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestCorporationSyncHistory_ResultsReturned() {
    // TODO
  }

  @Test
  public void testRequestCorporationSyncHistory_ContIDHonored() {
    // TODO
  }

  @Test
  public void testRequestCorporationSyncHistory_MaxResultsHonored() {
    // TODO
  }

  @Test
  public void testRequestUnfinishedCapsuleerSync_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestUnfinishedCapsuleerSync(mock);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestUnfinishedCapsuleerSync_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestUnfinishedCapsuleerSync(mock);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestUnfinishedCapsuleerSync_NotAdmin() {
    // TODO
  }

  @Test
  public void testRequestUnfinishedCapsuleerSync_ResultsReturned() {
    // TODO
  }

  @Test
  public void testRequestUnfinishedCorporationSync_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestUnfinishedCorporationSync(mock);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestUnfinishedCorporationSync_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestUnfinishedCorporationSync(mock);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestUnfinishedCorporationSync_NotAdmin() {
    // TODO
  }

  @Test
  public void testRequestUnfinishedCorporationSync_ResultsReturned() {
    // TODO
  }

  @Test
  public void testRequestFinishTracker_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestFinishTracker(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestFinishTracker_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    SyncTrackerWS cut = new SyncTrackerWS();
    Response result = cut.requestFinishTracker(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestFinishTracker_NotAdmin() {
    // TODO
  }

  @Test
  public void testRequestFinishTracker_TargetUserNotFound() {
    // TODO
  }

  @Test
  public void testRequestFinishTracker_TargetAccountNotFound() {
    // TODO
  }

  @Test
  public void testRequestFinishTracker_TargetTrackerNotFound() {
    // TODO
  }

  @Test
  public void testRequestFinishTracker_TrackerFinished() {
    // TODO
  }

}
