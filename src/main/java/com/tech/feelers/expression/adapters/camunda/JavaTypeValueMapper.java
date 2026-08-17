package com.tech.feelers.expression.adapters.camunda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValList;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.valuemapper.CustomValueMapper;

import scala.Function1;
import scala.Option;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Value mapper that unpacks engine values into plain Java types.
 *
 * <p>Two problems make this necessary. First, the engine's default mapper converts a number to
 * {@code Double}, which throws away the decimal exactness the engine itself computed with — the
 * arithmetic is correct internally but the result loses precision on the way out. Second, the
 * default mapper returns Scala collections, which would leak Scala types past this adapter.
 *
 * <p>Numbers therefore unpack to {@link java.math.BigDecimal}, and lists and contexts unpack to
 * plain {@link List} and {@link Map} with their elements converted recursively.
 */
public final class JavaTypeValueMapper implements CustomValueMapper {

  private int priority = 10;

  @Override
  public int priority() {
    return priority;
  }

  @Override
  public void org$camunda$feel$valuemapper$CustomValueMapper$_setter_$priority_$eq(int value) {
    this.priority = value;
  }

  /**
   * Leaves Java-to-engine conversion to the default mapper, because only the outbound direction
   * loses information.
   *
   * @param value Java value being converted
   * @param inner default conversion function
   * @return always empty, delegating to the default mapper
   */
  @Override
  public Option<Val> toVal(Object value, Function1<Object, Val> inner) {
    return Option.empty();
  }

  /**
   * Converts the engine value types whose default mapping would lose precision or expose Scala
   * types; everything else falls through to the default mapper.
   *
   * @param value engine value to unpack
   * @param inner default unpack function, used for nested elements
   * @return the plain Java value, or empty to fall through
   */
  @Override
  public Option<Object> unpackVal(Val value, Function1<Val, Object> inner) {
    if (value instanceof ValNumber number) {
      return Option.apply(number.value().bigDecimal());
    }
    if (value instanceof ValList list) {
      List<Object> converted = new ArrayList<>();
      CollectionConverters.asJava(list.items()).forEach(item -> converted.add(inner.apply(item)));
      return Option.apply(converted);
    }
    if (value instanceof ValContext context) {
      Map<String, Object> converted = new LinkedHashMap<>();
      CollectionConverters.asJava(context.context().variableProvider().getVariables())
          .forEach((name, entry) -> converted.put(name,
              entry instanceof Val val ? inner.apply(val) : entry));
      return Option.apply(converted);
    }
    return Option.empty();
  }
}
