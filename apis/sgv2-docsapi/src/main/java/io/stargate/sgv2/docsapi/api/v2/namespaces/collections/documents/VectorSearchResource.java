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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.stargate.sgv2.docsapi.api.v2.namespaces.collections.documents;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import io.stargate.sgv2.api.common.exception.model.dto.ApiError;
import io.stargate.sgv2.api.common.properties.datastore.DataStoreProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.v2.model.dto.ExecutionProfile;
import io.stargate.sgv2.docsapi.config.DocumentConfig;
import io.stargate.sgv2.docsapi.config.constants.OpenApiConstants;
import io.stargate.sgv2.docsapi.service.ExecutionContext;
import io.stargate.sgv2.docsapi.service.json.JsonConverter;
import io.stargate.sgv2.docsapi.service.query.search.VectorSearchService;
import io.stargate.sgv2.docsapi.service.schema.CollectionManager;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

/** Vector search resource. */
@Path(VectorSearchResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = OpenApiConstants.SecuritySchemes.TOKEN)
@Tag(ref = OpenApiConstants.Tags.DOCUMENTS)
public class VectorSearchResource {

  public static class VectorSearchResponse {
    @JsonProperty("data")
    private final List<String> documentIds;

    @JsonProperty("profile")
    private final ExecutionProfile profile;

    public VectorSearchResponse(List<String> documentIds, ExecutionProfile profile) {
      this.documentIds = documentIds;
      this.profile = profile;
    }

    public List<String> getDocumentIds() {
      return documentIds;
    }

    public ExecutionProfile getProfile() {
      return profile;
    }
  }

  public static class VectorSearchDocumentsResponse {
    @JsonProperty("data")
    private final List<JsonNode> documents;

    @JsonProperty("profile")
    private final ExecutionProfile profile;

    public VectorSearchDocumentsResponse(List<JsonNode> documents, ExecutionProfile profile) {
      this.documents = documents;
      this.profile = profile;
    }

    public List<JsonNode> getDocuments() {
      return documents;
    }

    public ExecutionProfile getProfile() {
      return profile;
    }
  }

  public static final String BASE_PATH = "/v2/namespaces/{namespace:\\w+}/collections";

  @Inject VectorSearchService vectorSearchService;
  @Inject CollectionManager collectionManager;
  @Inject DocumentConfig documentConfig;
  @Inject JsonConverter jsonConverter;
  @Inject DocumentProperties documentProperties;
  @Inject DataStoreProperties dataStoreProperties;

  public static class VectorSearchRequest {
    @JsonProperty("vector")
    @NotNull(message = "vector must not be null")
    @Size(min = 1, message = "vector must not be empty")
    private float[] vector;

    @JsonProperty("limit")
    @Min(1)
    @Max(1000)
    private int limit = 10;

    @JsonProperty("filter")
    private JsonNode filter;

    public float[] getVector() {
      return vector;
    }

    public void setVector(float[] vector) {
      this.vector = vector;
    }

    public int getLimit() {
      return limit;
    }

    public void setLimit(int limit) {
      this.limit = limit;
    }

    public JsonNode getFilter() {
      return filter;
    }

    public void setFilter(JsonNode filter) {
      this.filter = filter;
    }
  }

  @Operation(
      summary = "Vector search in a collection",
      description =
          "Perform a vector similarity search to find documents with similar vector embeddings.")
  @Parameters(
      value = {
        @Parameter(name = "namespace", ref = OpenApiConstants.Parameters.NAMESPACE),
        @Parameter(name = "collection", ref = OpenApiConstants.Parameters.COLLECTION),
        @Parameter(name = "profile", ref = OpenApiConstants.Parameters.PROFILE),
      })
  @RequestBody(
      description = "The vector search request",
      required = true,
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = VectorSearchRequest.class),
              examples =
                  @ExampleObject(
                      name = "Vector search example",
                      value =
                          """
                  {
                    "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
                    "limit": 10,
                    "filter": {"age": {"$gt": 21}}
                  }
                  """)))
  @APIResponses({
    @APIResponse(
        responseCode = "200",
        description = "Call successful. Returns documents ordered by vector similarity.",
        content = {
          @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema =
                  @Schema(
                      name = "VectorSearchResult",
                      properties = {
                        @SchemaProperty(
                            name = "data",
                            type = SchemaType.ARRAY,
                            minItems = 0,
                            example = "[]"),
                        @SchemaProperty(name = "profile", implementation = ExecutionProfile.class)
                      }))
        }),
    @APIResponse(ref = OpenApiConstants.Responses.GENERAL_400),
    @APIResponse(
        responseCode = "404",
        description = "Not found.",
        content =
            @Content(
                examples = {
                  @ExampleObject(ref = OpenApiConstants.Examples.NAMESPACE_DOES_NOT_EXIST),
                  @ExampleObject(ref = OpenApiConstants.Examples.COLLECTION_DOES_NOT_EXIST)
                },
                schema = @Schema(implementation = ApiError.class))),
    @APIResponse(ref = OpenApiConstants.Responses.GENERAL_401),
    @APIResponse(ref = OpenApiConstants.Responses.GENERAL_500),
    @APIResponse(ref = OpenApiConstants.Responses.GENERAL_503),
  })
  @POST
  @Path("{collection:\\w+}/vector-search")
  public Uni<RestResponse<Object>> vectorSearch(
      @PathParam("namespace") String namespace,
      @PathParam("collection") String collection,
      @QueryParam("profile") Boolean profile,
      @NotNull(message = "request body must not be null") VectorSearchRequest request) {

    // Create execution context
    ExecutionContext context = ExecutionContext.create(profile);

    // Validate vector dimension
    int expectedDimension = documentConfig.vectorDimension();
    vectorSearchService.validateVectorDimension(request.getVector(), expectedDimension);

    // Check if collection exists
    return collectionManager
        .getValidCollectionTable(namespace, collection)
        .onItem()
        .transformToUni(
            table -> {
              // TODO: Parse filter from JSON if provided
              Optional<io.stargate.sgv2.docsapi.service.query.FilterExpression> filter =
                  Optional.empty();

              // Perform vector search
              return vectorSearchService
                  .vectorSearch(
                      namespace,
                      collection,
                      request.getVector(),
                      request.getLimit(),
                      filter,
                      context)
                  .onItem()
                  .transform(
                      documents -> {
                        // Convert RawDocuments to JSON documents
                        List<JsonNode> jsonDocuments =
                            documents.stream()
                                .map(
                                    doc ->
                                        jsonConverter.convertToJsonDoc(
                                            doc.rows(),
                                            false, // writeAllPathsAsObjects
                                            dataStoreProperties.treatBooleansAsNumeric()))
                                .collect(Collectors.toList());

                        // Create response with full documents
                        VectorSearchDocumentsResponse response =
                            new VectorSearchDocumentsResponse(
                                jsonDocuments,
                                Boolean.TRUE.equals(profile) ? context.toProfile() : null);

                        return RestResponse.ok((Object) response);
                      });
            });
  }
}
