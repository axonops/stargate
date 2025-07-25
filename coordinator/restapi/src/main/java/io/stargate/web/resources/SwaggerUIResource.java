package io.stargate.web.resources;

import io.dropwizard.util.Strings;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/swagger-ui")
@Produces({MediaType.TEXT_HTML, "text/css", "image/png"})
@Singleton
public class SwaggerUIResource {

  private static final Logger logger = LoggerFactory.getLogger(SwaggerUIResource.class);

  private static final Pattern fileExtensionPattern =
      Pattern.compile("([^\\s]+(\\.(?i)(css|png|js|map|html))$)");

  private static final Pattern bearerTokenPattern = Pattern.compile("^Bearer\\s");

  private final ClassLoader classLoader;
  private final String indexFile;

  @Inject
  public SwaggerUIResource() throws IOException {
    this.classLoader = this.getClass().getClassLoader();
    URL entry = classLoader.getResource("swagger-ui-cust/index.html");
    if (entry == null) {
      throw new IOException("Could not find swagger-ui-cust/index.html in classpath");
    }

    // Save the templated file away for later so that we only have to do this conversion once.
    indexFile =
        new BufferedReader(
                new InputStreamReader(
                    entry.openConnection().getInputStream(), StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"));
  }

  /**
   * Due to how class loading works, we can't use the standard {@code
   * io.dropwizard.servlets.assets.AssetServlet} so instead we have to serve up our own static
   * resources
   *
   * @param fileName The name of the file that should be returned from the swagger-ui directory
   * @return An {@code javax.ws.rs.core.Response} containing an {@code java.io.InputStream} of the
   *     file requested
   */
  @GET
  @Path("/{fileName}")
  public Response get(@PathParam("fileName") final String fileName) {
    // Immediately return a 404 if an unsupported extension is used
    Matcher matcher = fileExtensionPattern.matcher(fileName);
    if (!matcher.matches()) {
      return Response.status(Status.NOT_FOUND).build();
    }
    return serveFile(fileName);
  }

  @GET
  @Path("/")
  public Response get(
      @Context UriInfo uriInfo,
      @HeaderParam("X-Cassandra-Token") String authToken,
      @HeaderParam("Authorization") String bearerToken) {
    // Redirect ".../swagger-ui" to ".../swagger-ui/" so that relative resources e.g. "./XXXX.css"
    // in "index.html" work correctly.
    if (!uriInfo.getAbsolutePath().getPath().endsWith("/")) {
      return Response.temporaryRedirect(uriInfo.getAbsolutePathBuilder().path("/").build()).build();
    }

    String token = authToken;
    if (Strings.isNullOrEmpty(token)) {
      token =
          Strings.isNullOrEmpty(bearerToken)
              ? bearerToken
              : bearerTokenPattern.matcher(bearerToken).replaceFirst("");
    }

    // Using an HTML file with templated text that's been read in as a String. Yes,  we could use
    // something like Velocity but that feels overkill for a single field.
    String formattedIndexFile =
        indexFile.replaceFirst("AUTHENTICATION_TOKEN", token == null ? "" : token);
    return Response.ok(
            new ByteArrayInputStream(formattedIndexFile.getBytes(StandardCharsets.UTF_8)))
        .type(MediaType.TEXT_HTML)
        .build();
  }

  private Response serveFile(String fileName) {
    InputStream is;
    String type = MediaType.TEXT_HTML;
    try {
      URL entry = classLoader.getResource("swagger-ui/" + fileName);
      if (entry == null) {
        throw new FileNotFoundException("Resource not found: swagger-ui/" + fileName);
      }
      is = entry.openConnection().getInputStream();

      if (fileName.endsWith(".css")) {
        type = "text/css";
      } else if (fileName.endsWith(".png")) {
        type = "image/png";
      }
    } catch (FileNotFoundException e) {
      return Response.status(Status.NOT_FOUND).build();
    } catch (IOException ioe) {
      logger.error("Error when executing request", ioe);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
    return Response.ok(is).type(type).build();
  }
}
