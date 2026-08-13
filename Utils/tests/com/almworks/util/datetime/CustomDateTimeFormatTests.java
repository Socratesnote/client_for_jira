package com.almworks.util.datetime;

import com.almworks.util.tests.BaseTestCase;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Covers {@link DateUtil#LOCAL_DATE_TIME} under a custom {@code alm.format.date} / {@code alm.format.time}
 * pair, where the two patterns are joined and handled by a single formatter.
 */
public class CustomDateTimeFormatTests extends BaseTestCase {
  private static final String DATE_PATTERN = "yyyy/MM/dd";
  private static final String TIME_PATTERN = "HH:mm";
  // Testing for a zone with a UTC offset different from zero (and different from the user's default). Has to be a named zone (not just a fixed GMT offset) that observes Daylight Savings time!
  private static final String NOT_MY_ZONE_WITH_DST = "Europe/Helsinki";

  private TimeZone myOriginalZone;
  private String myOriginalDatePattern;
  private String myOriginalTimePattern;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOriginalZone = TimeZone.getDefault();
    myOriginalDatePattern = System.getProperty(DateUtil.PROP_DATE_FORMAT);
    myOriginalTimePattern = System.getProperty(DateUtil.PROP_TIME_FORMAT);
    TimeZone.setDefault(TimeZone.getTimeZone(NOT_MY_ZONE_WITH_DST));
    System.setProperty(DateUtil.PROP_DATE_FORMAT, DATE_PATTERN);
    System.setProperty(DateUtil.PROP_TIME_FORMAT, TIME_PATTERN);
  }

  @Override
  protected void tearDown() throws Exception {
    restoreProperty(DateUtil.PROP_DATE_FORMAT, myOriginalDatePattern);
    restoreProperty(DateUtil.PROP_TIME_FORMAT, myOriginalTimePattern);
    TimeZone.setDefault(myOriginalZone);
    DateUtil.clearCaches();
    super.tearDown();
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }

  /** The instant at which the given local wall-clock reading occurs in {@link #NOT_MY_ZONE_WITH_DST}. */
  private static Date localInstant(int year, int month, int day, int hour, int minute) {
    Calendar cal = new GregorianCalendar(TimeZone.getTimeZone(NOT_MY_ZONE_WITH_DST));
    cal.clear();
    // Months start at 0.
    cal.set(year, month - 1, day, hour, minute, 0);
    return cal.getTime();
  }

  private static Date parse(String source) throws ParseException {
    return DateUtil.LOCAL_DATE_TIME.parse(source);
  }

  /**
   * Summer time at {@link #NOT_MY_ZONE_WITH_DST}.
   */
  public void testSummerTimeParsesAsEntered() throws ParseException {
    assertEquals(localInstant(2026, 8, 11, 14, 33), parse("2026/08/11 14:33"));
  }

  /** Winter time at {@link #NOT_MY_ZONE_WITH_DST}
    */
  public void testWinterTimeParsesAsEntered() throws ParseException {
    assertEquals(localInstant(2026, 1, 1, 14, 33), parse("2026/01/01 14:33"));
  }

  /** Format and parse must be inverses: what the user sees is what reparsing yields. */
  public void testRoundTripThroughFormat() throws ParseException {
    Date original = localInstant(2026, 8, 11, 14, 33);
    assertEquals(original, parse(DateUtil.LOCAL_DATE_TIME.format(original)));
  }

  /** Midnight has every time field at zero, so it exercises the date half of the pattern on its own. */
  public void testMidnightParsesAsLocalMidnight() throws ParseException {
    assertEquals(localInstant(2026, 8, 11, 0, 0), parse("2026/08/11 00:00"));
  }

  /**
   * A DST fall-back day **after** the transition, when the local day is 25 hours long. A joint formatter gets this from a single character and will parse correctly; a composed anchor + delta would be off by an hour.
   */
  public void testDstFallBackDayIsCorrectAfterTheTransition() throws ParseException {
    Date instant = localInstant(2026, 10, 25, 14, 0);
    assertEquals(instant, parse(DateUtil.LOCAL_DATE_TIME.format(instant)));
  }

  /** A DST fall-back day **before** the transition, where both joint and composite are correct. */
  public void testDstFallBackDayIsCorrectBeforeTheTransition() throws ParseException {
    Date instant = localInstant(2026, 10, 25, 1, 0);
    assertEquals(instant, parse(DateUtil.LOCAL_DATE_TIME.format(instant)));
  }

  /**
   * The residual limitation, which no single-format change can remove: on a fall-back day one local hour occurs
   * twice (here {@link #NOT_MY_ZONE_WITH_DST} at Helsinki switches at 04:00, so 03:00-03:59 repeats), and a pattern carrying no zone
   * or offset field cannot say which occurrence is meant. Pinned: the calendar resolves such a reading to the
   * **later** occurrence, the one at standard time, so the earlier one does not survive a format/parse round trip.
   */
  public void testRepeatedHourResolvesToTheLaterOccurrence() throws ParseException {
    Date later = parse("2026/10/25 03:30");
    // Both occurrences print the same reading, so the earlier one is that reading minus the transition.
    Date earlier = new Date(later.getTime() - 60 * 60 * 1000);
    assertEquals("2026/10/25 03:30", DateUtil.LOCAL_DATE_TIME.format(earlier));
    assertEquals(later, parse(DateUtil.LOCAL_DATE_TIME.format(earlier)));
  }

  /** A DST spring-forward day, where the local day is 23 hours long, for an instant after the gap. */
  public void testDstSpringForwardDayIsCorrectAfterTheGap() throws ParseException {
    Date instant = localInstant(2026, 3, 29, 14, 0);
    assertEquals(instant, parse(DateUtil.LOCAL_DATE_TIME.format(instant)));
  }

  /**
   On the spring-forward day the local hour 03:00-03:59 does not exist ({@link #NOT_MY_ZONE_WITH_DST} jumps from
   03:00 to 04:00). Pinned: a reading inside the gap is resolved leniently, one hour forward, so it lands on 04:30.
   */
  public void testNonexistentSpringForwardReadingResolvesForward() throws ParseException {
    assertEquals(localInstant(2026, 3, 29, 4, 30), parse("2026/03/29 03:30"));
  }

  /** With only the date property set, the time half comes from the locale default and must still join into one pattern. */
  public void testOnlyDatePropertySetStillRoundTrips() throws ParseException {
    System.clearProperty(DateUtil.PROP_TIME_FORMAT);
    Date original = localInstant(2026, 10, 25, 14, 0);
    assertEquals(original, parse(DateUtil.LOCAL_DATE_TIME.format(original)));
  }

  /** With only the time property set, the date half comes from the locale default. */
  public void testOnlyTimePropertySetStillRoundTrips() throws ParseException {
    System.clearProperty(DateUtil.PROP_DATE_FORMAT);
    Date original = localInstant(2026, 10, 25, 14, 0);
    assertEquals(original, parse(DateUtil.LOCAL_DATE_TIME.format(original)));
  }

  /** With neither property set the custom path is skipped entirely and the default formatter is used. */
  public void testUnsetPropertiesDelegateToTheDefaultFormat() throws ParseException {
    System.clearProperty(DateUtil.PROP_DATE_FORMAT);
    System.clearProperty(DateUtil.PROP_TIME_FORMAT);
    Date original = localInstant(2026, 8, 11, 14, 33);
    assertEquals(original, parse(DateUtil.LOCAL_DATE_TIME.format(original)));
  }

}
