package io.stargate.sgv2.restapi.service.resources;

import static io.stargate.sgv2.restapi.service.resources.RestResourceBase.convertRowsToResponse;

import io.smallrye.mutiny.Uni;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.api.common.StargateRequestInfo;
import io.stargate.sgv2.api.common.cql.builder.VectorSearchQueryBuilder;
import io.stargate.sgv2.api.common.exception.model.dto.ApiError;
import io.stargate.sgv2.restapi.config.RestApiConfig;
import io.stargate.sgv2.restapi.config.RestApiUtils;
import io.stargate.sgv2.restapi.config.constants.RestOpenApiConstants;
import io.stargate.sgv2.restapi.service.models.Sgv2RowsResponse;
import io.stargate.sgv2.restapi.service.models.VectorSearchRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

/**
 * Resource for vector similarity search operations on tables with vector columns.
 * Implements Cassandra 5.0's ANN (Approximate Nearest Neighbor) search functionality.
 */
@ApplicationScoped
@Path("/v2/keyspaces/{keyspaceName}/tables/{tableName}/vector-search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = RestOpenApiConstants.SecuritySchemes.TOKEN)
@Tag(name = "Vector Search", description = "Vector similarity search operations using Cassandra 5.0 ANN")
public class VectorSearchResource {

  @Inject protected StargateRequestInfo requestInfo;
  @Inject protected RestApiConfig restApiConfig;

  @POST
  @Operation(
      summary = "Search for similar vectors",
      description = "Performs approximate nearest neighbor (ANN) search to find vectors similar to the provided query vector using Cassandra 5.0's vector search capabilities")
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "Search completed successfully",
            content = @Content(
                schema = @Schema(
                    implementation = Sgv2RowsResponse.class,
                    type = SchemaType.OBJECT))),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_400),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_401),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_404),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_500)
      })
  public Uni<RestResponse<Object>> searchVectors(
      @Parameter(name = "keyspaceName", ref = RestOpenApiConstants.Parameters.KEYSPACE_NAME)
          @PathParam("keyspaceName")
          @NotBlank(message = "keyspaceName is required")
          String keyspaceName,
      @Parameter(name = "tableName", ref = RestOpenApiConstants.Parameters.TABLE_NAME)
          @PathParam("tableName")
          @NotBlank(message = "tableName is required")
          String tableName,
      @Parameter(
              name = "vectorColumn",
              description = "Name of the vector column to search",
              required = true)
          @QueryParam("vectorColumn")
          @NotBlank(message = "vectorColumn is required")
          String vectorColumn,
      @Parameter(
              name = "limit",
              description = "Maximum number of results to return",
              required = false)
          @QueryParam("limit")
          @DefaultValue("10")
          @Min(value = 1, message = "limit must be at least 1")
          @Max(value = 1000, message = "limit cannot exceed 1000")
          int limit,
      @Parameter(name = "raw", ref = RestOpenApiConstants.Parameters.RAW) 
          @QueryParam("raw")
          final boolean raw,
      @Parameter(name = "compactMapData", ref = RestOpenApiConstants.Parameters.COMPACT_MAP_DATA)
          @QueryParam("compactMapData")
          final Boolean compactMapData,
      @RequestBody(
              description = "Vector search request containing the query vector and optional filters",
              required = true,
              content = @Content(
                  mediaType = MediaType.APPLICATION_JSON,
                  schema = @Schema(implementation = VectorSearchRequest.class)))
          @Valid VectorSearchRequest request) {
    
    // Validate input
    if (request.getVector() == null || request.getVector().isEmpty()) {
      ApiError error = new ApiError("Vector cannot be null or empty", 400);
      return Uni.createFrom().item(RestResponse.status(RestResponse.Status.BAD_REQUEST, error));
    }
    
    // Build the vector search query
    VectorSearchQueryBuilder queryBuilder = new VectorSearchQueryBuilder()
        .selectAll()
        .from(keyspaceName, tableName)
        .whereFilters(request.getFilter())
        .orderByAnn(vectorColumn, request.getVector())
        .limit(limit);
    
    QueryOuterClass.Query query = queryBuilder.build();
    
    // Execute the query
    return requestInfo
        .getStargateBridge()
        .executeQuery(query)
        .map(
            response ->
                convertRowsToResponse(
                    response, raw, RestApiUtils.getRequestParams(restApiConfig, compactMapData)))
        .onFailure()
        .recoverWithItem(
            throwable -> {
              ApiError error = new ApiError(
                  "Failed to execute vector search: " + throwable.getMessage(), 500);
              return RestResponse.status(RestResponse.Status.INTERNAL_SERVER_ERROR, error);
            });
  }
}