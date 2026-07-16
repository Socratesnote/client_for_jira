package com.almworks.restconnector.jql;

import com.almworks.api.constraint.CompositeConstraint;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.constraint.ConstraintNegation;
import com.almworks.api.constraint.Constraints;
import com.almworks.util.LogHelper;
import org.almworks.util.TypedKey;
import org.almworks.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class JqlQuery {
  public static final JqlQuery EMPTY = new JqlQuery(null);

  private final Constraint myJqlConstraint;
  @Nullable
  private final String myOrderBy;
  private String myText = null;

  public JqlQuery(Constraint jqlConstraint) {
    this(jqlConstraint, null);
  }

  public JqlQuery(Constraint jqlConstraint, @Nullable  String orderBy) {
    myJqlConstraint = jqlConstraint;
    myOrderBy = orderBy;
  }

  public JqlQuery orderBy(String orderBy) {
    if (Objects.equals(myOrderBy, orderBy)) return this;
    return new JqlQuery(myJqlConstraint, orderBy);
  }

  public Constraint getJqlConstraint() {
    return myJqlConstraint;
  }

  @NotNull
  public String getJqlText() {
    // Initialize with last text.
    //TODO: But doesn't this effectively lock the JQL text once a change has been made? And isn't it limited to only the SimpleConstant version?
    String jqlText = myText;
    if (jqlText == null || jqlText.isEmpty()) {
      jqlText = myJqlConstraint == null ? null : createJqlText(myJqlConstraint);
      if (jqlText == null) jqlText = "";
      if (myOrderBy != null) jqlText += " " + myOrderBy;
      // Store for next call.
      myText = jqlText;
    }
    return jqlText;
  }

  private static String createJqlText(Constraint jqlConstraint) {
    Boolean constant = Constraints.checkSimpleConstant(jqlConstraint);
    if (constant != null) return constant ? "" : null;
    StringBuilder builder = new StringBuilder();
    createJqlText(jqlConstraint, builder);
    return builder.toString();
  }

  private static void createJqlText(Constraint constraint, StringBuilder builder) {
    ConstraintNegation negated = Constraints.cast(ConstraintNegation.NEGATION, constraint);
    CompositeConstraint composite = Util.castNullable(CompositeConstraint.class, negated != null ? negated.getNegated() : constraint);
    if (composite != null) {
      createComposite(composite, builder, negated != null);
      return;
    }
    if (negated != null) {
      LogHelper.error("Negation not supported", negated);
      return;
    }
    JQLConstraint jqlConstraint = Util.castNullable(JQLConstraint.class, constraint);
    if (jqlConstraint == null) {
      LogHelper.error("Unsupported constraint", constraint);
      return;
    }
    jqlConstraint.appendTo(builder);
  }

  private static void createComposite(CompositeConstraint composite, StringBuilder builder, boolean negated) {
    TypedKey<? extends CompositeConstraint> type = composite.getType();
    boolean and;
    if (type == CompositeConstraint.AND) and = true;
    else if (type == CompositeConstraint.OR) and = false;
    else {
      LogHelper.error("Unknown constraint", composite, type);
      return;
    }
    List<? extends Constraint> children = composite.getChildren();
    if (children == null || children.isEmpty()) return;
    if (negated) builder.append(" NOT ");
    if (children.size() == 1) {
      createJqlText(children.get(0), builder);
      return;
    }
    builder.append("(");
    String sep = "";
    for (Constraint child : children) {
      builder.append(sep);
      createJqlText(child, builder);
      sep = and ? " AND " : " OR ";
    }
    builder.append(")");
  }

  @Override
  public String toString() {
    return "JQL(" + getJqlText() + ")";
  }
}
