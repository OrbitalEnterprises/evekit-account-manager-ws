package enterprises.orbital.evekit.ws.model;

import enterprises.orbital.evekit.TestBase;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

public class ESISyncTrackerWSTest extends TestBase {

  @Test
  public void testRequestSyncHistory_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    ESISyncTrackerWS cut = new ESISyncTrackerWS();
    Response result = cut.requestSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestSyncHistory_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    ESISyncTrackerWS cut = new ESISyncTrackerWS();
    Response result = cut.requestSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestSyncHistory_AccountNotFound() {
    HttpServletRequest mock = setupRequestMock(regularUser.getUid(), regularSource.getSource());
    EasyMock.replay(mock);
    ESISyncTrackerWS cut = new ESISyncTrackerWS();
    Response result = cut.requestSyncHistory(mock, 1, 2, 3);
    Assert.assertEquals(404, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestSyncHistory_ResultsReturned() {
    // TODO
  }

  @Test
  public void testRequestSyncHistory_ContIDHonored() {
    // TODO
  }

  @Test
  public void testRequestSyncHistory_MaxResultsHonored() {
    // TODO
  }

  @Test
  public void testRequestStartedSync_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    ESISyncTrackerWS cut = new ESISyncTrackerWS();
    Response result = cut.requestStartedSync(mock);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestStartedSync_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    ESISyncTrackerWS cut = new ESISyncTrackerWS();
    Response result = cut.requestStartedSync(mock);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestStartedSync_NotAdmin() {
    // TODO
  }

  @Test
  public void testRequestStartedSync_ResultsReturned() {
    // TODO
  }

  @Test
  public void testRequestFinishTracker_NotLoggedIn_One() {
    HttpServletRequest mock = setupRequestMock(null, "some source");
    EasyMock.replay(mock);
    ESISyncTrackerWS cut = new ESISyncTrackerWS();
    Response result = cut.requestFinishTracker(mock, 1, 2, 3);
    Assert.assertEquals(401, result.getStatus());
    EasyMock.verify(mock);
  }

  @Test
  public void testRequestFinishTracker_NotLoggedIn_Two() {
    HttpServletRequest mock = setupRequestMock("some user", null);
    EasyMock.replay(mock);
    ESISyncTrackerWS cut = new ESISyncTrackerWS();
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
