package com.almworks.platform;

/**
 * Product identity. The field initialisers below are the values a debug run reports; a release build overwrites them.
 *
 * <p>The declarations are a load-bearing text format, not just source. For a release build, {@code ant/genHeader.xml}
 * copies this file aside and rewrites it with {@code com.almworks.tools.modpip.ModPip}, which matches the literal
 * text {@code private final String <name> = "}. Reformatting a declaration or adding a modifier breaks that match, and
 * ModPip then fails the distribution build with "cannot find constant" - a compile of this module will not catch it.
 */
class ProductInfoProperties {
  private final String version = "4.0.0.debug";
  private final String buildNumber = "9999";
  private final String versionType = "DEBUG";
  private final String productName = "#Client for Jira";
  private final String buildDate = "2020/04/01 14:51 MSK";

  public String getVersion() {
    return version;
  }

  public String getBuildNumber() {
    return buildNumber;
  }

  public String getVersionType() {
    return versionType;
  }

  public String getProductName() {
    return productName;
  }

  public String getBuildDate() {
    return buildDate;
  }
}