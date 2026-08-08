package com.almworks.api.application;

import com.almworks.api.application.util.PredefinedKey;
import com.almworks.engine.gui.BaseTextController;
import com.almworks.util.tests.GUITestCase;
import org.almworks.util.detach.DetachComposite;

import javax.swing.*;

/**
 * Proves the loop every controller test relies on: a value written to the model reaches the component.
 * If this fails, nothing built on {@link TestModelMap} means anything.
 */
public class TestModelMapTests extends GUITestCase {
  private final ModelKey<String> myKey = PredefinedKey.create("text");
  private final DetachComposite myLife = new DetachComposite();
  private TestModelMap myModel;
  private JTextArea myComponent;

  protected void setUp() throws Exception {
    super.setUp();
    myModel = new TestModelMap();
    myComponent = new JTextArea();
  }

  protected void tearDown() throws Exception {
    myLife.detach();
    myComponent = null;
    myModel = null;
    super.tearDown();
  }

  public void testConnectPushesCurrentValue() {
    myModel.setValue(myKey, "initial");
    connect();
    assertEquals("initial", myComponent.getText());
  }

  public void testValueChangeReachesComponent() {
    connect();
    assertEquals("", myComponent.getText());
    myModel.setValue(myKey, "written later");
    assertEquals("written later", myComponent.getText());
  }

  public void testAbsentValueGivesEmptyText() {
    myModel.setValue(myKey, "something");
    connect();
    myModel.setValue(myKey, null);
    assertEquals("", myComponent.getText());
  }

  public void testDetachStopsUpdates() {
    connect();
    myLife.detach();
    myModel.setValue(myKey, "after detach");
    assertEquals("", myComponent.getText());
  }

  private void connect() {
    new PlainTextController(myKey).connectUI(myLife, myModel, myComponent);
  }

  /**
   * The simplest possible controller: the model value is the displayed text.
   */
  private static class PlainTextController extends BaseTextController<String> {
    PlainTextController(ModelKey<String> key) {
      super(key, true);
    }

    protected String toValue(String text) {
      return text;
    }

    protected boolean isEditable() {
      return false;
    }

    protected String toText(String value) {
      return value;
    }

    protected String getEmptyStringValue() {
      return null;
    }
  }
}
