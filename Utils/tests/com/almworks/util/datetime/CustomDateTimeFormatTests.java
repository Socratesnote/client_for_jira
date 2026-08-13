package com.almworks.util.datetime;

import com.almworks.util.tests.BaseTestCase;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Covers {@link DateUtil#LOCAL_DATE_TIME} under a custom {@code alm.format.date} / {@code alm.format.time}
 * pair, where parsing takes the composing path that sums a separately parsed date and time.
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

  /** Midnight is the anchor itself, so it exercises the date half with a zero-length duration added. */
  public void testMidnightParsesAsLocalMidnight() throws ParseException {
    assertEquals(localInstant(2026, 8, 11, 0, 0), parse("2026/08/11 00:00"));
  }

  /**
   * Test for a DST transition day **after** the transition, when the
   * local day is 25 hours long. This is a known limitation of the way dates and times are parsed as separate entities. Pinned behaviour for now.
   */
  public void testDstFallBackDayIsOffByTheTransition_currentBehaviour() throws ParseException {
    Date actual_instant= localInstant(2026, 10, 25, 14, 0);
    Date incorrect_instant = parse(DateUtil.LOCAL_DATE_TIME.format(actual_instant));
    // These two values should (incorrectly) be 1 hour apart, i.e. 3.6 million milliseconds.
    assertEquals(60*60*1000,
            actual_instant.getTime() - incorrect_instant.getTime());
  }

  /**
   * Test for a DST transition day **before** the transition, when the
   * local day is (still) 24 hours long.
   */
  public void testDstFallBackDayIsCorrectBeforeTheTransition() throws ParseException {
    Date actual_instant= localInstant(2026, 10, 25, 1, 0);
    Date incorrect_instant = parse(DateUtil.LOCAL_DATE_TIME.format(actual_instant));
    // These two values should be the same because the transition hasn't happened yet.
    assertEquals(0,
            actual_instant.getTime() - incorrect_instant.getTime());
  }

  /** With neither property set the composing path is skipped entirely and the default formatter is used. */
  public void testUnsetPropertiesDelegateToTheDefaultFormat() throws ParseException {
    System.clearProperty(DateUtil.PROP_DATE_FORMAT);
    System.clearProperty(DateUtil.PROP_TIME_FORMAT);
    Date original = localInstant(2026, 8, 11, 14, 33);
    assertEquals(original, parse(DateUtil.LOCAL_DATE_TIME.format(original)));
  }

}
