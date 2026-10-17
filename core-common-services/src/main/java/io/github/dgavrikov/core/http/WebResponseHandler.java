package io.github.dgavrikov.core.http;

import io.github.dgavrikov.core.masking.MaskingMarker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class WebResponseHandler {
    private static final String FORMAT_REQUEST = "\n>>> Body: {}\n";
    private static final String FORMAT_RESPONSE = "\n<<< Body: {}\n";

    private final PathExclusion pathExclusion;

    public <T> WebResponseWrapper<T> mapResponse(ResponseEntity<T> response, boolean bodyRequired) {
        if (bodyRequired && !response.hasBody()) {
            throw new OverrideHttpStatusCodeException(HttpStatusCode.valueOf(500), "No body found");
        }

        return WebResponseWrapper.<T>builder()
                .success(true)
                .statusCode(response.getStatusCode().value())
                .statusMessage("success")
                .result(response.getBody())
                .headers(response.getHeaders())
                .build();
    }

    public <T> WebResponseWrapper<T> handleError(String message, Throwable throwable) {
        Optional<Throwable> rootCause = Stream.iterate(throwable, Throwable::getCause)
                .filter(element -> element.getCause() == null)
                .findFirst();

        var currentError = rootCause.orElse(throwable);
        log.error("{}: {}", message, currentError.getLocalizedMessage(), currentError);

        var builder = WebResponseWrapper.<T>builder().success(false);

        return switch (currentError) {
            case OverrideHttpStatusCodeException ex -> builder
                    .statusCode(ex.getStatusCode().value())
                    .statusMessage(StringUtils.defaultIfBlank(ex.getLocalizedMessage(), ex.getStatusText()))
                    .build();
            case HttpStatusCodeException ex -> {
                var responseBody = ex.getResponseBodyAsString();
                yield builder
                        .statusCode(ex.getStatusCode().value())
                        .statusMessage(StringUtils.defaultIfBlank(ex.getLocalizedMessage(), ex.getStatusText()))
                        .responseBody(responseBody)
                        .headers(ex.getResponseHeaders())
                        .build();
            }
            default -> builder
                    .statusCode(500)
                    .statusMessage(currentError.getLocalizedMessage())
                    .build();
        };
    }

    public <T> T executeWithLoggingBody(
            RestClient restClient,
            Object requestBody,
            String url,
            Function<RestClient, T> requestExecutor) {
        return this.executeWithLoggingBody(restClient, Optional.empty(), url, requestExecutor, true);
    }

    /**
     * A universal execution wrapper for {@link RestClient} queries that handles contextual logging and lifecycle.
     * <p>Usage example:</p>
     * <pre>{@code
     * var response = webResponseHandler.executeWithLoggingBody(
     *      userRestClient,
     *      request,
     *      getPath,
     *      restClient -> restClient.post()
     *          .uri(getPath)
     *          .header(Constants.METHOD_NAME, getPath)
     *          .header(...)
     *          .body(request)
     *          .retrieve()
     *          .toEntity(CustomDto.class)
     * );
     * }</pre>
     *
     * @param restClient      the {@link RestClient} instance to use
     * @param requestBody     the request payload object used for accurate logging
     * @param url             the target request URL, required for log filtering/masking rules
     * @param requestExecutor the functional interface encapsulating the query build and execution logic
     * @param isLogError      enables error body logging tagged with the MASKING_JSON_ALL_MARKER marker
     * @return the execution response
     * @param <T>             the expected type of the response body or wrapper
     */
    public <T> T executeWithLoggingBody(
            @NotNull RestClient restClient,
            Object requestBody,
            String url,
            @NotNull Function<RestClient, T> requestExecutor,
            boolean isLogError) {
        try{
            RestClientContextHolder.setUri(url);

            if(requestBody instanceof Optional<?> p) {
                if(p.isPresent())
                    writeLogDebug(requestBody, FORMAT_REQUEST);
            } else {
                writeLogDebug(requestBody, FORMAT_REQUEST);
            }

            var response = requestExecutor.apply(restClient);
            if(response instanceof ResponseEntity<?> rs) {
                writeLogDebug(rs.getBody(), FORMAT_RESPONSE);
            } else if (response instanceof WebResponseWrapper<?> wr) {
                writeLogDebug(wr.result(), FORMAT_RESPONSE);
            }
            return response;
        } catch (Exception e) {
            if(isLogError)
                switch (e) {
                    case HttpStatusCodeException ex ->
                        writeLogDebugWithError(ex.getResponseBodyAsString(), FORMAT_RESPONSE);
                    default -> writeLogDebugWithError(e.getMessage(), FORMAT_RESPONSE);
                }
            throw e;
        } finally {
            RestClientContextHolder.clear();
        }
    }

    /**
     * A universal execution wrapper for {@link RestClient} queries that handles contextual lifecycle.
     * The context is used to propagate the request URL across execution methods within the same thread.
     *
     * @param restClient      the {@link RestClient} instance to use
     * @param url             the target request URL, required for log filtering/masking rules
     * @param requestExecutor the functional interface encapsulating the query build and execution logic
     * @return the execution response
     * @param <T>             the expected type of the response body or wrapper
     */
    public <T> T executeWithContextHolder(
            @NotNull RestClient restClient,
            String url,
            @NotNull Function<RestClient, T> requestExecutor
    ){
        try{
            RestClientContextHolder.setUri(url);
            return requestExecutor.apply(restClient);
        } finally {
            RestClientContextHolder.clear();
        }
    }

    private void writeLogDebugWithError(Object body, String format) {
        if(!log.isDebugEnabled())
            return;

        if(pathExclusion.isExcluded())
            return;

        log.atDebug()
                .addMarker(MaskingMarker.MASKING_JSON_ALL_MARKER)
                .log(format, body);
    }

    private void writeLogDebug(Object body, String format) {
        if(!log.isDebugEnabled())
            return;

        if(pathExclusion.isExcluded())
            return;

        log.atDebug()
                .addMarker(MaskingMarker.MASKING_OBJECT_MARKER)
                .addMarker(MaskingMarker.MASKING_JSON_MARKER)
                .addMarker(MaskingMarker.MASKING_MARKER)
                .log(format, body);
    }
}
