package com.almworks.jira.provider3.sync.jql.impl;

import com.almworks.util.tests.BaseTestCase;

/**
 * Covers the assembly of the "Open Query in Browser" URL. The form is the Cloud issue navigator, {@code <base>/issues/?jql=<encoded>};
 * the Server-era {@code secure/IssueNavigator!executeAdvanced.jspa} page it replaced resolves today only through a 302 redirect to it.
 * The base URL arrives from connection configuration unnormalized, so the separator handling is the part worth pinning down.
 */
public class JqlQueryBuilderUrlTests extends BaseTestCase {
  private static final String JQL = "project in (10012)";
  private static final String ENCODED_JQL = "project+in+%2810012%29";

  public void testBaseUrlWithTrailingSlash() {
    assertEquals("https://example.atlassian.net/issues/?jql=" + ENCODED_JQL,
      JqlQueryBuilder.buildQueryUrl("https://example.atlassian.net/", JQL));
  }

  public void testBaseUrlWithoutTrailingSlash() {
    assertEquals("https://example.atlassian.net/issues/?jql=" + ENCODED_JQL,
      JqlQueryBuilder.buildQueryUrl("https://example.atlassian.net", JQL));
  }

  // Both spellings of the base URL have to produce the identical URL: which one is stored depends on how the connection was configured.
  public void testTrailingSlashDoesNotChangeTheResult() {
    assertEquals(JqlQueryBuilder.buildQueryUrl("https://example.atlassian.net", JQL),
      JqlQueryBuilder.buildQueryUrl("https://example.atlassian.net/", JQL));
  }

  // A context path is part of the base URL on a hosted instance, and must survive.
  public void testBaseUrlWithContextPath() {
    assertEquals("https://example.com/jira/issues/?jql=" + ENCODED_JQL,
      JqlQueryBuilder.buildQueryUrl("https://example.com/jira", JQL));
  }

  public void testJqlIsEncoded() {
    String url = JqlQueryBuilder.buildQueryUrl("https://example.atlassian.net/", "summary ~ \"a b\" AND project = ABC");
    assertEquals("https://example.atlassian.net/issues/?jql=summary+%7E+%22a+b%22+AND+project+%3D+ABC", url);
  }

  // An empty constraint means "all issues" rather than an error, so the URL is still well-formed.
  public void testEmptyJql() {
    assertEquals("https://example.atlassian.net/issues/?jql=", JqlQueryBuilder.buildQueryUrl("https://example.atlassian.net/", ""));
  }

  /**
   * The base URL is nullable and the caller has no better answer when it is missing. The URL is then unusable either way;
   * what this pins down is that it does not throw, since an NPE here would break opening any query rather than one connection's.
   */
  public void testNullBaseUrlDoesNotThrow() {
    assertEquals("/issues/?jql=" + ENCODED_JQL, JqlQueryBuilder.buildQueryUrl(null, JQL));
  }
}
