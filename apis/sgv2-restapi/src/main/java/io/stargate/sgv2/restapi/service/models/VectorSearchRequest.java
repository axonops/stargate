package io.stargate.sgv2.restapi.service.models;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    description = "Request body for vector similarity search",
    example = """
        {
          "vector": [0.1, 0.2, 0.3, 0.4],
          "filter": {
            "category": "electronics",
            "price": {"$lt": 1000}
          }
        }
        """)
public class VectorSearchRequest {
  
  @Schema(
      description = "The vector to search for similar vectors",
      required = true,
      example = "[0.1, 0.2, 0.3, 0.4]")
  private List<Float> vector;
  
  @Schema(
      description = "Optional filters to apply before vector search",
      required = false)
  private Map<String, Object> filter;
  
  public VectorSearchRequest() {}
  
  public VectorSearchRequest(List<Float> vector, Map<String, Object> filter) {
    this.vector = vector;
    this.filter = filter;
  }
  
  public List<Float> getVector() {
    return vector;
  }
  
  public void setVector(List<Float> vector) {
    this.vector = vector;
  }
  
  public Map<String, Object> getFilter() {
    return filter;
  }
  
  public void setFilter(Map<String, Object> filter) {
    this.filter = filter;
  }
}