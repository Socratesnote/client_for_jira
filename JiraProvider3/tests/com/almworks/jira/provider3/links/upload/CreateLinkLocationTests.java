package com.almworks.jira.provider3.links.upload;

import com.almworks.util.tests.BaseTestCase;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Guards CreateLink's extraction of the new link id from the response Location header.
// Jira Cloud may return the Location against api/2 even when posting to api/3, so the
// match must be version-agnostic. LOCATION is a private static field, hence reflection.
public class CreateLinkLocationTests extends BaseTestCase {
  private static Pattern location() throws Exception {
    Field f = CreateLink.class.getDeclaredField("LOCATION");
    f.setAccessible(true);
    return (Pattern) f.get(null);
  }

  public void testExtractsIdRegardlessOfApiVersion() throws Exception {
    Pattern p = location();
    assertEquals("10001", firstGroup(p, "https://x.atlassian.net/rest/api/2/issueLink/10001"));
    assertEquals("10001", firstGroup(p, "https://x.atlassian.net/rest/api/3/issueLink/10001"));
  }

  public void testNoMatchOnUnrelatedLocation() throws Exception {
    Pattern p = location();
    assertNull(firstGroup(p, "https://x.atlassian.net/rest/api/3/issue/10001"));
  }

  private static String firstGroup(Pattern p, String s) {
    Matcher m = p.matcher(s);
    return m.find() ? m.group(1) : null;
  }
}
