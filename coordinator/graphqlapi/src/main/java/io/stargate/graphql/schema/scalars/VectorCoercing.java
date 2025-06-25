/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.graphql.schema.scalars;

import graphql.language.ArrayValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import java.util.ArrayList;
import java.util.List;

/**
 * Coercing implementation for Cassandra 5.0 vector types. Vectors are represented as arrays of
 * floating-point numbers in GraphQL.
 */
public class VectorCoercing implements Coercing<float[], List<Float>> {

  public static final VectorCoercing INSTANCE = new VectorCoercing();

  private VectorCoercing() {}

  @Override
  public List<Float> serialize(Object dataFetcherResult) throws CoercingSerializeException {
    if (dataFetcherResult == null) {
      return null;
    }

    if (dataFetcherResult instanceof float[]) {
      float[] array = (float[]) dataFetcherResult;
      List<Float> list = new ArrayList<>(array.length);
      for (float f : array) {
        list.add(f);
      }
      return list;
    } else if (dataFetcherResult instanceof List<?>) {
      List<?> list = (List<?>) dataFetcherResult;
      List<Float> result = new ArrayList<>(list.size());
      for (Object element : list) {
        if (element instanceof Number) {
          result.add(((Number) element).floatValue());
        } else {
          throw new CoercingSerializeException(
              "Vector elements must be numeric values, got " + element.getClass());
        }
      }
      return result;
    } else {
      throw new CoercingSerializeException(
          "Expected float[] or List<Float> for vector type, got " + dataFetcherResult.getClass());
    }
  }

  @Override
  public float[] parseValue(Object input) throws CoercingParseValueException {
    if (input == null) {
      return null;
    }

    if (input instanceof List<?>) {
      List<?> list = (List<?>) input;
      float[] array = new float[list.size()];
      int i = 0;
      for (Object element : list) {
        if (element instanceof Number) {
          array[i++] = ((Number) element).floatValue();
        } else {
          throw new CoercingParseValueException(
              "Vector elements must be numeric values, got " + element.getClass());
        }
      }
      return array;
    } else {
      throw new CoercingParseValueException(
          "Expected List for vector input, got " + input.getClass());
    }
  }

  @Override
  public float[] parseLiteral(Object input) throws CoercingParseLiteralException {
    if (!(input instanceof ArrayValue)) {
      throw new CoercingParseLiteralException(
          "Expected ArrayValue for vector literal, got " + input.getClass());
    }

    ArrayValue arrayValue = (ArrayValue) input;
    List<Value> values = arrayValue.getValues();
    float[] result = new float[values.size()];

    for (int i = 0; i < values.size(); i++) {
      Value value = values.get(i);
      if (value instanceof FloatValue) {
        result[i] = ((FloatValue) value).getValue().floatValue();
      } else if (value instanceof IntValue) {
        result[i] = ((IntValue) value).getValue().floatValue();
      } else {
        throw new CoercingParseLiteralException(
            "Vector elements must be numeric literals, got " + value.getClass());
      }
    }

    return result;
  }
}
