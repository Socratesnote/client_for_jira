package com.almworks.api.application;

import com.almworks.util.collections.SimpleModifiable;
import com.almworks.util.properties.PropertyMap;
import org.almworks.util.Collections15;
import org.almworks.util.TypedKey;

import java.util.Map;

/**
 * A {@link ModelMap} for tests: a plain map of values plus the change notification a controller listens to.
 * <p>
 * The production implementation, {@code com.almworks.explorer.loader.ModelMapImpl}, lives in the Application
 * module and pulls in the item-loader machinery, so it is out of reach both from here and from any test that
 * only wants to drive one controller. Nothing more than this is needed: {@code AbstractModelKey.getValue(ModelMap)}
 * is a map lookup by {@link TypedKey}, so a key created with {@code PredefinedKey.create(name)} works against
 * this class unchanged.
 * <p>
 * What makes it useful is {@link #valueChanged}: {@code BaseTextController.connectTextValue} subscribes with
 * {@code addAWTChangeListener} and re-reads the model whenever that fires. Use {@link #setValue} to write a
 * value and fire in one call - that is the loop a controller test exercises.
 * <p>
 * Caveat on keys: {@code PredefinedKey.getModel(...)} is an {@code assert false}, so a controller that asks a
 * key for a sub-model needs a different key implementation when assertions are enabled.
 */
public class TestModelMap extends SimpleModifiable implements ModelMap {
  private final PropertyMap myValues = new PropertyMap();
  private final Map<String, ModelKey<?>> myKeys = Collections15.hashMap();

  @Override
  public <T> T get(TypedKey<? extends T> key) {
    return myValues.get(key);
  }

  @Override
  public <T> void put(TypedKey<T> key, T value) {
    myValues.put((TypedKey) key, value);
  }

  @Override
  public void registerKey(String name, ModelKey<?> key) {
    myKeys.put(name, key);
  }

  @Override
  public void valueChanged(ModelKey<?> key) {
    fireChanged();
  }

  /**
   * Writes a value through the key and notifies listeners, which is what a real edit does.
   */
  public <T> void setValue(ModelKey<T> key, T value) {
    key.setValue(myValues, value);
    registerKey(key.getName(), key);
    valueChanged(key);
  }

  /**
   * The metadata describing the item type. Controller tests never reach it, and supplying one would mean
   * building the loader machinery this class exists to avoid, so it is absent rather than faked.
   */
  @Override
  public MetaInfo getMetaInfo() {
    return null;
  }

  /**
   * Copies the values of every key registered here, and reports whether anything was copied. The production
   * implementation walks the {@code ModelKey.ALL_KEYS} bit set instead; that set is maintained by the loader,
   * so tests that write values directly would find it empty.
   */
  @Override
  public boolean copyFrom(PropertyMap newValues) {
    boolean copied = false;
    for (ModelKey<?> key : myKeys.values()) {
      if (!key.hasValue(newValues)) continue;
      key.copyValue(this, newValues);
      copied = true;
    }
    if (copied) fireChanged();
    return copied;
  }

  @Override
  public String toString() {
    return "TestModelMap " + myValues;
  }
}
