package enterprises.orbital.evekit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.account.EveKitUserAuthSource;
import enterprises.orbital.oauth.AuthUtil;

public class TestBase {
  @SuppressWarnings("unused")
  private static final Logger         log             = Logger.getLogger(TestBase.class.getName());

  protected static Random             GEN             = new Random(
                                                          Long.parseLong(System.getProperty("test.seed", "0")) == 0 ? OrbitalProperties.getCurrentTime()
                                                              : Long.parseLong(System.getProperty("test.seed", "0")));
  protected static final int          MAX_RANDOM      = 1 << 20;
  protected static final Set<Long>    UNIQUE_LONGS    = new HashSet<Long>();
  protected static final Set<Integer> UNIQUE_INTEGERS = new HashSet<Integer>();

  // User accounts
  protected EveKitUserAccount         regularUser;
  protected EveKitUserAccount         adminUser;
  protected EveKitUserAuthSource      regularSource;
  protected EveKitUserAuthSource      adminSource;

  @Before
  public void setup() throws Exception {
    OrbitalProperties.addPropertyFile("AccountTest.properties");
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(EveKitUserAccountProvider.USER_ACCOUNT_PU_PROP)));
    AuthUtil.setUserAccountProvider(new EveKitUserAccountProvider());
    // Prepare user accounts
    regularUser = EveKitUserAccount.createNewUserAccount(false, true);
    regularSource = EveKitUserAuthSource.createSource(regularUser, "regularSource", "regularUser", "regularDetails");
    adminUser = EveKitUserAccount.createNewUserAccount(true, true);
    adminSource = EveKitUserAuthSource.createSource(adminUser, "adminSource", "adminUser", "adminDetails");
  }

  @After
  public void teardown() throws Exception {}

  public HttpServletRequest setupRequestMock(
                                             String uid,
                                             String source) {
    HttpServletRequest mockRequest = EasyMock.mock(HttpServletRequest.class);
    HttpSession mockSession = EasyMock.mock(HttpSession.class);
    EasyMock.expect(mockRequest.getSession()).andReturn(mockSession).anyTimes();
    EasyMock.expect(mockSession.getAttribute(EasyMock.eq(AuthUtil.UID_SESSION_VAR))).andReturn(uid).anyTimes();
    EasyMock.expect(mockSession.getAttribute(EasyMock.eq(AuthUtil.SOURCE_SESSION_VAR))).andReturn(source).anyTimes();
    EasyMock.replay(mockSession);
    return mockRequest;
  }

  public static void changeSeed(
                                long seed) {
    GEN = new Random(seed);
  }

  public static String getRandomText(
                                     int length) {
    char[] alpha = "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ ".toCharArray();
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; i++) {
      builder.append(alpha[GEN.nextInt(alpha.length)]);
    }
    return builder.toString();
  }

  public static int getRandomInt() {
    return GEN.nextInt(MAX_RANDOM) + 1;
  }

  public static int getRandomInt(
                                 int max) {
    return GEN.nextInt(max);
  }

  public static long getRandomLong() {
    long val = Math.abs(GEN.nextLong());
    if (val == 0) {
      val++;
    }
    return val;
  }

  public static long getRandomLong(
                                   long max) {
    long next = getRandomLong();
    while (next >= max) {
      next = getRandomLong();
    }
    return next;
  }

  public static double getRandomDouble(
                                       double max) {
    return GEN.nextDouble() * max;
  }

  public static boolean getRandomBoolean() {
    return GEN.nextBoolean();
  }

  public static long getUniqueRandomLong() {
    long val = getRandomLong();
    while (UNIQUE_LONGS.contains(val)) {
      val = getRandomLong();
    }
    UNIQUE_LONGS.add(val);
    return val;
  }

  public static int getUniqueRandomInteger() {
    int val = getRandomInt();
    while (UNIQUE_INTEGERS.contains(val)) {
      val = getRandomInt();
    }
    UNIQUE_INTEGERS.add(val);
    return val;
  }

  public static BigDecimal getRandomBigDecimal(
                                               int i) {
    return (new BigDecimal(Math.abs(GEN.nextGaussian() * i))).setScale(2, RoundingMode.HALF_UP);
  }
}
