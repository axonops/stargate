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
package io.stargate.bridge.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.stargate.bridge.proto.QueryOuterClass.TypeSpec;
import io.stargate.bridge.proto.QueryOuterClass.TypeSpec.Map;
import io.stargate.bridge.proto.QueryOuterClass.TypeSpec.Udt;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TypeSpecsTest {

  @ParameterizedTest
  @MethodSource("typeFormats")
  public void shouldFormat(TypeSpec type, String expectedOutput) {
    assertThat(TypeSpecs.format(type)).isEqualTo(expectedOutput);
  }

  @ParameterizedTest
  @MethodSource("typeFormats")
  public void shouldParse(TypeSpec type, String expectedOutput) {
    assertThat(TypeSpecs.parse(expectedOutput, Collections.emptyList(), false)).isEqualTo(type);
  }

  @Test
  public void shouldParseWithStrictUdtResolution() {
    Udt udtA = Udt.newBuilder().setName("a").putFields("i", TypeSpecs.INT).build();
    Udt udtB = Udt.newBuilder().setName("b").putFields("s", TypeSpecs.VARCHAR).build();
    java.util.List<Udt> udts = Arrays.asList(udtA, udtB);

    assertThat(TypeSpecs.parse("frozen<map<a,b>>", udts, true))
        .satisfies(
            spec -> {
              assertThat(spec.hasMap()).isTrue();
              Map map = spec.getMap();
              assertThat(map.getFrozen()).isTrue();
              assertThat(map.getKey().getUdt()).isSameAs(udtA);
              assertThat(map.getValue().getUdt()).isSameAs(udtB);
            });

    assertThatThrownBy(() -> TypeSpecs.parse("list<c>", udts, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot find user type c");
  }

  @ParameterizedTest
  @MethodSource("frozenAndUnfrozenTypes")
  public void shouldHandleFrozenness(TypeSpec frozenType, TypeSpec unfrozenType) {
    assertThat(TypeSpecs.unfreeze(frozenType)).isEqualTo(unfrozenType);
    assertThat(TypeSpecs.freeze(unfrozenType)).isEqualTo(frozenType);

    switch (frozenType.getSpecCase()) {
      case BASIC: // always unfrozen
        assertThat(TypeSpecs.isFrozen(frozenType)).isFalse();
        assertThat(TypeSpecs.isFrozen(unfrozenType)).isFalse();
        break;
      case TUPLE: // always frozen
        assertThat(TypeSpecs.isFrozen(frozenType)).isTrue();
        assertThat(TypeSpecs.isFrozen(unfrozenType)).isTrue();
        break;
      default:
        assertThat(TypeSpecs.isFrozen(frozenType)).isTrue();
        assertThat(TypeSpecs.isFrozen(unfrozenType)).isFalse();
    }
  }

  public static Arguments[] typeFormats() {
    return new Arguments[] {
      arguments(TypeSpecs.INT, "int"),
      arguments(TypeSpecs.VARCHAR, "text"),
      arguments(TypeSpecs.POINT, "'PointType'"),
      arguments(TypeSpecs.LINESTRING, "'LineStringType'"),
      arguments(TypeSpecs.POLYGON, "'PolygonType'"),
      arguments(TypeSpecs.list(TypeSpecs.INT), "list<int>"),
      arguments(TypeSpecs.frozenList(TypeSpecs.INT), "frozen<list<int>>"),
      arguments(TypeSpecs.set(TypeSpecs.INT), "set<int>"),
      arguments(TypeSpecs.frozenSet(TypeSpecs.INT), "frozen<set<int>>"),
      arguments(TypeSpecs.map(TypeSpecs.INT, TypeSpecs.VARCHAR), "map<int,text>"),
      arguments(TypeSpecs.frozenMap(TypeSpecs.INT, TypeSpecs.VARCHAR), "frozen<map<int,text>>"),
      // Note: UDTs without fields are functionally invalid, but it doesn't matter for this test
      arguments(TypeSpecs.udt("foo", Collections.emptyMap()), "\"foo\""),
      arguments(TypeSpecs.frozenUdt("foo", Collections.emptyMap()), "frozen<\"foo\">"),
      arguments(
          TypeSpecs.tuple(TypeSpecs.INT, TypeSpecs.VARCHAR, TypeSpecs.FLOAT),
          "tuple<int,text,float>"),
      arguments(TypeSpecs.vector(TypeSpecs.FLOAT, 3), "vector<float, 3>"),
      arguments(TypeSpecs.vector(TypeSpecs.FLOAT, 128), "vector<float, 128>"),
      arguments(TypeSpecs.vector(TypeSpecs.FLOAT, 1536), "vector<float, 1536>"),
    };
  }

  public static Arguments[] frozenAndUnfrozenTypes() {
    return new Arguments[] {
      arguments(TypeSpecs.INT, TypeSpecs.INT),
      arguments(TypeSpecs.frozenList(TypeSpecs.INT), TypeSpecs.list(TypeSpecs.INT)),
      arguments(TypeSpecs.frozenSet(TypeSpecs.INT), TypeSpecs.set(TypeSpecs.INT)),
      arguments(
          TypeSpecs.frozenMap(TypeSpecs.INT, TypeSpecs.UUID),
          TypeSpecs.map(TypeSpecs.INT, TypeSpecs.UUID)),
      arguments(
          TypeSpecs.frozenList(TypeSpecs.set(TypeSpecs.INT)),
          TypeSpecs.list(TypeSpecs.set(TypeSpecs.INT))),
      arguments(
          TypeSpecs.frozenUdt("a", Collections.singletonMap("i", TypeSpecs.INT)),
          TypeSpecs.udt("a", Collections.singletonMap("i", TypeSpecs.INT))),
      arguments(
          TypeSpecs.tuple(TypeSpecs.INT, TypeSpecs.VARCHAR),
          TypeSpecs.tuple(TypeSpecs.INT, TypeSpecs.VARCHAR)),
    };
  }

  @Test
  public void shouldParseVectorTypes() {
    TypeSpec vector3 = TypeSpecs.parse("vector<float, 3>", Collections.emptyList(), false);
    assertThat(vector3.hasVector()).isTrue();
    assertThat(vector3.getVector().getElement()).isEqualTo(TypeSpecs.FLOAT);
    assertThat(vector3.getVector().getSize()).isEqualTo(3);

    TypeSpec vector128 = TypeSpecs.parse("vector<float, 128>", Collections.emptyList(), false);
    assertThat(vector128.hasVector()).isTrue();
    assertThat(vector128.getVector().getElement()).isEqualTo(TypeSpecs.FLOAT);
    assertThat(vector128.getVector().getSize()).isEqualTo(128);
  }

  @Test
  public void shouldThrowOnInvalidVectorSize() {
    assertThatThrownBy(() -> TypeSpecs.parse("vector<float, 0>", Collections.emptyList(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Vector size must be positive");

    assertThatThrownBy(() -> TypeSpecs.parse("vector<float, -5>", Collections.emptyList(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Vector size must be positive");

    assertThatThrownBy(() -> TypeSpecs.parse("vector<float, abc>", Collections.emptyList(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Malformed type name: vector size must be an integer, got 'abc'");
  }

  @Test
  public void shouldThrowOnInvalidVectorParameters() {
    assertThatThrownBy(() -> TypeSpecs.parse("vector<float>", Collections.emptyList(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Malformed type name: vector requires two parameters");

    assertThatThrownBy(() -> TypeSpecs.parse("vector<float, 3, 4>", Collections.emptyList(), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Malformed type name: vector size must be an integer, got '3, 4'");
  }

  @Test
  public void shouldHandleVectorFreezability() {
    TypeSpec vector = TypeSpecs.vector(TypeSpecs.FLOAT, 3);
    // Vectors are never frozen
    assertThat(TypeSpecs.isFrozen(vector)).isFalse();
    assertThat(TypeSpecs.freeze(vector)).isEqualTo(vector);
    assertThat(TypeSpecs.unfreeze(vector)).isEqualTo(vector);
  }
}
