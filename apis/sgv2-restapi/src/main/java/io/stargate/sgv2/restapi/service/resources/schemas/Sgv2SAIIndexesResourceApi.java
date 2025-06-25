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
package io.stargate.sgv2.restapi.service.resources.schemas;

import io.smallrye.mutiny.Uni;
import io.stargate.sgv2.restapi.config.constants.RestOpenApiConstants;
import io.stargate.sgv2.restapi.service.models.Sgv2SAIIndexAddRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
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
 * REST API endpoints specifically for Storage Attached Index (SAI) management.
 * Provides a simplified interface for creating and managing SAI indexes in Cassandra 5.0.
 */
@ApplicationScoped
@Path("/v2/schemas/keyspaces/{keyspaceName}/tables/{tableName}/sai-indexes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = RestOpenApiConstants.SecuritySchemes.TOKEN)
@Tag(
    name = "SAI Indexes",
    description = "Storage Attached Index (SAI) management for Cassandra 5.0")
public interface Sgv2SAIIndexesResourceApi {
  
  @GET
  @Operation(
      summary = "Get all SAI indexes for a table",
      description = "Lists all Storage Attached Indexes (SAI) for the specified table")
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(type = SchemaType.OBJECT))),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_400),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_401),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_500)
      })
  Uni<RestResponse<Object>> getAllSAIIndexes(
      @Parameter(name = "keyspaceName", ref = RestOpenApiConstants.Parameters.KEYSPACE_NAME)
          @PathParam("keyspaceName")
          @NotBlank(message = "keyspaceName must be provided")
          final String keyspaceName,
      @Parameter(name = "tableName", ref = RestOpenApiConstants.Parameters.TABLE_NAME)
          @PathParam("tableName")
          @NotBlank(message = "tableName must be provided")
          final String tableName,
      @Parameter(name = "compactMapData", ref = RestOpenApiConstants.Parameters.COMPACT_MAP_DATA)
          @QueryParam("compactMapData")
          final Boolean compactMapData);
  
  @POST
  @Operation(
      summary = "Create a SAI index",
      description = "Creates a Storage Attached Index (SAI) on a table column. "
          + "SAI provides high-performance indexing for both equality and range queries, "
          + "and supports vector similarity search for vector columns.")
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "201",
            description = "Created",
            content = @Content(schema = @Schema(type = SchemaType.OBJECT))),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_400),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_401),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_500)
      })
  Uni<RestResponse<Map<String, Object>>> createSAIIndex(
      @Parameter(name = "keyspaceName", ref = RestOpenApiConstants.Parameters.KEYSPACE_NAME)
          @PathParam("keyspaceName")
          @NotBlank(message = "keyspaceName must be provided")
          final String keyspaceName,
      @Parameter(name = "tableName", ref = RestOpenApiConstants.Parameters.TABLE_NAME)
          @PathParam("tableName")
          @NotBlank(message = "tableName must be provided")
          final String tableName,
      @RequestBody(
              description = "SAI index definition",
              required = true,
              content = @Content(
                  mediaType = MediaType.APPLICATION_JSON,
                  schema = @Schema(implementation = Sgv2SAIIndexAddRequest.class)))
          @NotNull @Valid
          final Sgv2SAIIndexAddRequest indexAdd);
  
  @GET
  @Path("/analyze-column/{columnName}")
  @Operation(
      summary = "Analyze column for SAI indexing",
      description = "Analyzes a column and provides recommendations for SAI index configuration. "
          + "Returns suggested options based on the column type (e.g., similarity functions for vectors).")
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "Analysis complete",
            content = @Content(
                schema = @Schema(
                    type = SchemaType.OBJECT,
                    example = "{\n" +
                        "  \"column\": \"embedding\",\n" +
                        "  \"type\": \"vector<float, 384>\",\n" +
                        "  \"recommendations\": {\n" +
                        "    \"indexType\": \"SAI\",\n" +
                        "    \"options\": {\n" +
                        "      \"similarity_function\": \"cosine\"\n" +
                        "    },\n" +
                        "    \"reason\": \"Vector columns benefit from SAI for ANN search\"\n" +
                        "  }\n" +
                        "}"))),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_400),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_401),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_404),
        @APIResponse(ref = RestOpenApiConstants.Responses.GENERAL_500)
      })
  Uni<RestResponse<Map<String, Object>>> analyzeColumnForSAI(
      @Parameter(name = "keyspaceName", ref = RestOpenApiConstants.Parameters.KEYSPACE_NAME)
          @PathParam("keyspaceName")
          @NotBlank(message = "keyspaceName must be provided")
          final String keyspaceName,
      @Parameter(name = "tableName", ref = RestOpenApiConstants.Parameters.TABLE_NAME)
          @PathParam("tableName")
          @NotBlank(message = "tableName must be provided")
          final String tableName,
      @Parameter(
              name = "columnName",
              description = "Name of the column to analyze",
              required = true)
          @PathParam("columnName")
          @NotBlank(message = "columnName must be provided")
          final String columnName);
}