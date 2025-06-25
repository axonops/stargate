package io.stargate.sgv2.restapi.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.stargate.bridge.grpc.Values;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.api.common.config.RequestParams;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class VectorCodecTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  
  @Test
  public void testToProtoVectorFromList() throws Exception {
    ToProtoValueCodecs codecs = new ToProtoValueCodecs();
    QueryOuterClass.ColumnSpec columnSpec = QueryOuterClass.ColumnSpec.newBuilder()
        .setName("vector_column")
        .setType(QueryOuterClass.TypeSpec.newBuilder()
            .setVector(QueryOuterClass.TypeSpec.Vector.newBuilder()
                .setElement(QueryOuterClass.TypeSpec.newBuilder()
                    .setBasic(QueryOuterClass.TypeSpec.Basic.FLOAT))
                .setSize(3)))
        .build();
    
    ToProtoValueCodec codec = codecs.codecFor(columnSpec, RequestParams.DEFAULT);
    
    // Test with List<Float>
    List<Float> floatList = Arrays.asList(1.0f, 2.0f, 3.0f);
    QueryOuterClass.Value value = codec.protoValueFromStrictlyTyped(floatList);
    
    assertThat(value.hasVector()).isTrue();
    assertThat(value.getVector().getValuesList()).containsExactly(1.0f, 2.0f, 3.0f);
  }
  
  @Test
  public void testToProtoVectorFromArray() throws Exception {
    ToProtoValueCodecs codecs = new ToProtoValueCodecs();
    QueryOuterClass.ColumnSpec columnSpec = QueryOuterClass.ColumnSpec.newBuilder()
        .setName("vector_column")
        .setType(QueryOuterClass.TypeSpec.newBuilder()
            .setVector(QueryOuterClass.TypeSpec.Vector.newBuilder()
                .setElement(QueryOuterClass.TypeSpec.newBuilder()
                    .setBasic(QueryOuterClass.TypeSpec.Basic.FLOAT))
                .setSize(3)))
        .build();
    
    ToProtoValueCodec codec = codecs.codecFor(columnSpec, RequestParams.DEFAULT);
    
    // Test with float[]
    float[] floatArray = new float[]{1.0f, 2.0f, 3.0f};
    QueryOuterClass.Value value = codec.protoValueFromStrictlyTyped(floatArray);
    
    assertThat(value.hasVector()).isTrue();
    assertThat(value.getVector().getValuesList()).containsExactly(1.0f, 2.0f, 3.0f);
  }
  
  @Test
  public void testToProtoVectorFromString() throws Exception {
    ToProtoValueCodecs codecs = new ToProtoValueCodecs();
    QueryOuterClass.ColumnSpec columnSpec = QueryOuterClass.ColumnSpec.newBuilder()
        .setName("vector_column")
        .setType(QueryOuterClass.TypeSpec.newBuilder()
            .setVector(QueryOuterClass.TypeSpec.Vector.newBuilder()
                .setElement(QueryOuterClass.TypeSpec.newBuilder()
                    .setBasic(QueryOuterClass.TypeSpec.Basic.FLOAT))
                .setSize(3)))
        .build();
    
    ToProtoValueCodec codec = codecs.codecFor(columnSpec, RequestParams.DEFAULT);
    
    // Test with string representation
    QueryOuterClass.Value value = codec.protoValueFromStringified("[1.0, 2.0, 3.0]");
    
    assertThat(value.hasVector()).isTrue();
    assertThat(value.getVector().getValuesList()).containsExactly(1.0f, 2.0f, 3.0f);
  }
  
  @Test
  public void testToProtoVectorInvalidType() throws Exception {
    ToProtoValueCodecs codecs = new ToProtoValueCodecs();
    QueryOuterClass.ColumnSpec columnSpec = QueryOuterClass.ColumnSpec.newBuilder()
        .setName("vector_column")
        .setType(QueryOuterClass.TypeSpec.newBuilder()
            .setVector(QueryOuterClass.TypeSpec.Vector.newBuilder()
                .setElement(QueryOuterClass.TypeSpec.newBuilder()
                    .setBasic(QueryOuterClass.TypeSpec.Basic.FLOAT))
                .setSize(3)))
        .build();
    
    ToProtoValueCodec codec = codecs.codecFor(columnSpec, RequestParams.DEFAULT);
    
    // Test with invalid type
    assertThatThrownBy(() -> codec.protoValueFromStrictlyTyped("not a vector"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot coerce");
  }
  
  @Test
  public void testFromProtoVector() throws Exception {
    FromProtoValueCodecs codecs = new FromProtoValueCodecs();
    QueryOuterClass.ColumnSpec columnSpec = QueryOuterClass.ColumnSpec.newBuilder()
        .setName("vector_column")
        .setType(QueryOuterClass.TypeSpec.newBuilder()
            .setVector(QueryOuterClass.TypeSpec.Vector.newBuilder()
                .setElement(QueryOuterClass.TypeSpec.newBuilder()
                    .setBasic(QueryOuterClass.TypeSpec.Basic.FLOAT))
                .setSize(3)))
        .build();
    
    FromProtoValueCodec codec = codecs.codecFor(columnSpec, RequestParams.DEFAULT);
    
    // Create a vector value
    QueryOuterClass.Value value = Values.vector(1.0f, 2.0f, 3.0f);
    
    // Test conversion to object
    Object result = codec.fromProtoValue(value);
    assertThat(result).isInstanceOf(List.class);
    assertThat((List<Float>) result).containsExactly(1.0f, 2.0f, 3.0f);
    
    // Test conversion to JSON
    JsonNode jsonNode = codec.jsonNodeFrom(value);
    assertThat(jsonNode.isArray()).isTrue();
    assertThat(jsonNode.size()).isEqualTo(3);
    assertThat(jsonNode.get(0).floatValue()).isEqualTo(1.0f);
    assertThat(jsonNode.get(1).floatValue()).isEqualTo(2.0f);
    assertThat(jsonNode.get(2).floatValue()).isEqualTo(3.0f);
  }
  
  @Test
  public void testFromProtoVectorNull() throws Exception {
    FromProtoValueCodecs codecs = new FromProtoValueCodecs();
    QueryOuterClass.ColumnSpec columnSpec = QueryOuterClass.ColumnSpec.newBuilder()
        .setName("vector_column")
        .setType(QueryOuterClass.TypeSpec.newBuilder()
            .setVector(QueryOuterClass.TypeSpec.Vector.newBuilder()
                .setElement(QueryOuterClass.TypeSpec.newBuilder()
                    .setBasic(QueryOuterClass.TypeSpec.Basic.FLOAT))
                .setSize(3)))
        .build();
    
    FromProtoValueCodec codec = codecs.codecFor(columnSpec, RequestParams.DEFAULT);
    
    // Test with null value
    QueryOuterClass.Value nullValue = Values.NULL;
    
    Object result = codec.fromProtoValue(nullValue);
    assertThat(result).isNull();
    
    JsonNode jsonNode = codec.jsonNodeFrom(nullValue);
    assertThat(jsonNode.isNull()).isTrue();
  }
}