package com.example.appstore.api;

import com.example.appstore.api.ProblemDetails.FieldError;
import com.example.appstore.catalog.AppNotFoundException;
import com.example.appstore.catalog.StorefrontNotServedException;
import com.example.appstore.catalog.UpstreamConnectException;
import com.example.appstore.catalog.UpstreamContractException;
import com.example.appstore.catalog.UpstreamException;
import com.example.appstore.catalog.UpstreamRateLimitedException;
import com.example.appstore.catalog.UpstreamReadTimeoutException;
import com.example.appstore.catalog.UpstreamServerErrorException;
import com.example.appstore.catalog.storefront.InvalidCountryCodeException;
import com.example.appstore.catalog.storefront.UnsupportedStorefrontException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every failure into the problem types of {@code docs/api/README.md}, following the matrix in
 * {@code docs/architecture/error-handling.md}. Upstream outcomes are already logged by the gateway adapters, so only
 * unexpected exceptions are logged here, with the stack trace on the server side only.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UpstreamException.class)
    ResponseEntity<ProblemDetail> upstream(UpstreamException failure, HttpServletRequest request) {
        return switch (failure) {
            case UpstreamRateLimitedException limited ->
                ResponseEntity.status(ProblemType.UPSTREAM_UNAVAILABLE.status())
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(limited.retryAfter())))
                        .body(problem(
                                ProblemType.UPSTREAM_UNAVAILABLE,
                                "The upstream service is rate limited. Retry after the time in the Retry-After header.",
                                request));
            case UpstreamReadTimeoutException ignored ->
                respond(ProblemType.UPSTREAM_TIMEOUT, "The upstream service did not answer in time.", request);
            case UpstreamConnectException ignored -> upstreamError(request);
            case UpstreamServerErrorException ignored -> upstreamError(request);
            case UpstreamContractException ignored -> upstreamError(request);
            case StorefrontNotServedException ignored -> unsupportedStorefront(request);
        };
    }

    @ExceptionHandler(UnsupportedStorefrontException.class)
    ResponseEntity<ProblemDetail> unsupportedStorefront(HttpServletRequest request) {
        return respond(
                ProblemType.UNSUPPORTED_STOREFRONT, "The App Store has no storefront for this country.", request);
    }

    @ExceptionHandler(InvalidCountryCodeException.class)
    ResponseEntity<ProblemDetail> invalidCountryCode(InvalidCountryCodeException failure, HttpServletRequest request) {
        log.debug("invalid country code");
        return invalid(List.of(new FieldError("cc", failure.getMessage())), request.getRequestURI());
    }

    @ExceptionHandler(AppNotFoundException.class)
    ResponseEntity<ProblemDetail> appNotFound(HttpServletRequest request) {
        return respond(
                ProblemType.APP_NOT_FOUND,
                "The app doesn't exist, isn't available in this storefront, or isn't accessible.",
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception failure, HttpServletRequest request) {
        log.error("unexpected error", failure);
        return respond(ProblemType.INTERNAL, "An unexpected error occurred.", request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException failure, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = failure.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(nameOf(result.getMethodParameter()), error.getDefaultMessage())))
                .toList();
        return widen(invalid(errors, pathOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException failure,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return widen(invalid(List.of(new FieldError(failure.getParameterName(), "is required")), pathOf(request)));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException failure, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = failure instanceof MethodArgumentTypeMismatchException argument
                ? argument.getName()
                : failure.getPropertyName();
        return widen(invalid(List.of(new FieldError(field, "has an invalid format")), pathOf(request)));
    }

    /**
     * Spring's own problems (unknown path, wrong method, ...) keep their type and get the correlation id. Spring passes
     * {@code null} here and builds the body later, so the body is built first.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception failure, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object problemBody = body;
        if (problemBody == null && failure instanceof org.springframework.web.ErrorResponse errorResponse) {
            problemBody = errorResponse.updateAndGetBody(
                    getMessageSource(), org.springframework.context.i18n.LocaleContextHolder.getLocale());
        }
        if (problemBody instanceof ProblemDetail problem) {
            ProblemDetails.addCorrelationId(problem);
        }
        return super.handleExceptionInternal(failure, problemBody, headers, status, request);
    }

    private static ResponseEntity<ProblemDetail> upstreamError(HttpServletRequest request) {
        return respond(
                ProblemType.UPSTREAM_ERROR, "The upstream service failed or returned an unexpected response.", request);
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemType type, String detail, HttpServletRequest request) {
        return ResponseEntity.status(type.status()).body(problem(type, detail, request));
    }

    private static ProblemDetail problem(ProblemType type, String detail, HttpServletRequest request) {
        return ProblemDetails.of(type, detail, request.getRequestURI());
    }

    private static ResponseEntity<ProblemDetail> invalid(List<FieldError> errors, String path) {
        return ResponseEntity.status(ProblemType.INVALID_REQUEST.status())
                .body(ProblemDetails.invalidRequest(errors, path));
    }

    private static ResponseEntity<Object> widen(ResponseEntity<ProblemDetail> response) {
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    /** The public parameter name ({@code @RequestParam}/{@code @PathVariable} value), not the Java name. */
    private static String nameOf(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.name().isEmpty()) {
            return pathVariable.name();
        }
        return parameter.getParameterName();
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servlet
                ? servlet.getRequest().getRequestURI()
                : null;
    }

    static long retryAfterSeconds(Duration retryAfter) {
        long seconds = retryAfter.getSeconds() + (retryAfter.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
