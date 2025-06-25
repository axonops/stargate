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
package io.stargate.sgv2.restapi.service.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Specialized request for creating Storage Attached Indexes (SAI) with Cassandra 5.0.
 * This provides a simplified interface specifically for SAI indexes.
 */
@Schema(
    name = "SAIIndexAddRequest",
    description = "Request to create a Storage Attached Index (SAI) for Cassandra 5.0")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Sgv2SAIIndexAddRequest {
  
  public static final String SAI_INDEX_CLASS = 
      "org.apache.cassandra.index.sai.StorageAttachedIndex";
  
  @NotBlank
  private String column;
  private String name;
  private Boolean ifNotExists = false;
  
  // SAI-specific options
  private Boolean normalize;
  private Boolean caseSensitive;
  private Boolean asciiOnly;
  private String analyzerClass;
  private String similarityFunction;
  
  // For deserializer
  protected Sgv2SAIIndexAddRequest() {}
  
  public Sgv2SAIIndexAddRequest(String column) {
    this.column = column;
  }
  
  @Schema(
      required = true, 
      description = "Column name to create the SAI index on")
  @NotBlank(message = "column must be provided")
  public String getColumn() {
    return column;
  }
  
  public void setColumn(String column) {
    this.column = column;
  }
  
  @Schema(
      description = "Optional index name. If not specified, Cassandra generates a default name.")
  public String getName() {
    return name;
  }
  
  public void setName(String name) {
    this.name = name;
  }
  
  @Schema(
      description = "If true, create the index only if it doesn't already exist")
  public Boolean getIfNotExists() {
    return ifNotExists;
  }
  
  public void setIfNotExists(Boolean ifNotExists) {
    this.ifNotExists = ifNotExists;
  }
  
  @Schema(
      description = "For text columns: normalize the text (default: true)")
  public Boolean getNormalize() {
    return normalize;
  }
  
  public void setNormalize(Boolean normalize) {
    this.normalize = normalize;
  }
  
  @Schema(
      description = "For text columns: case-sensitive matching (default: false)")
  public Boolean getCaseSensitive() {
    return caseSensitive;
  }
  
  public void setCaseSensitive(Boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
  }
  
  @Schema(
      description = "For text columns: ASCII-only mode (default: false)")
  public Boolean getAsciiOnly() {
    return asciiOnly;
  }
  
  public void setAsciiOnly(Boolean asciiOnly) {
    this.asciiOnly = asciiOnly;
  }
  
  @Schema(
      description = "Custom analyzer class for text analysis",
      example = "org.apache.cassandra.index.sai.analyzer.StandardAnalyzer")
  public String getAnalyzerClass() {
    return analyzerClass;
  }
  
  public void setAnalyzerClass(String analyzerClass) {
    this.analyzerClass = analyzerClass;
  }
  
  @Schema(
      description = "Similarity function for vector columns (cosine, euclidean, dot_product)")
  public String getSimilarityFunction() {
    return similarityFunction;
  }
  
  public void setSimilarityFunction(String similarityFunction) {
    this.similarityFunction = similarityFunction;
  }
  
  /**
   * Converts this SAI-specific request to a generic index add request.
   */
  public Sgv2IndexAddRequest toGenericRequest() {
    Sgv2IndexAddRequest request = new Sgv2IndexAddRequest(column, name);
    request.setIfNotExists(ifNotExists != null ? ifNotExists : false);
    request.setType(SAI_INDEX_CLASS);
    
    // Build options map from SAI-specific properties
    Map<String, String> options = new HashMap<>();
    
    if (normalize != null) {
      options.put("normalize", normalize.toString());
    }
    if (caseSensitive != null) {
      options.put("case_sensitive", caseSensitive.toString());
    }
    if (asciiOnly != null) {
      options.put("ascii", asciiOnly.toString());
    }
    if (analyzerClass != null) {
      options.put("analyzer_class", analyzerClass);
    }
    if (similarityFunction != null) {
      options.put("similarity_function", similarityFunction);
    }
    
    if (!options.isEmpty()) {
      request.setOptions(options);
    }
    
    return request;
  }
}