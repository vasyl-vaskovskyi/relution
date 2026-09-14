package com.example.appstore.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Tracer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void validIncomingIdIsUsedLoggedAndEchoed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/apps");
        request.addHeader(CorrelationId.HEADER, "client.req-42_A");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get(CorrelationId.MDC_KEY)));

        assertThat(duringRequest).hasValue("client.req-42_A");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("client.req-42_A");
        assertThat(MDC.get(CorrelationId.MDC_KEY))
                .as("MDC cleared after the request")
                .isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"has space", "line\nbreak", "semi;colon", "ünïcode", "<script>"})
    void missingOrUnsafeIdsAreReplacedWithANewUuid(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/apps");
        if (incoming != null) {
            request.addHeader(CorrelationId.HEADER, incoming);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get(CorrelationId.MDC_KEY)));

        assertThat(duringRequest.get()).matches("[0-9a-f-]{36}").isNotEqualTo(incoming);
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(duringRequest.get());
    }

    @Test
    void idsLongerThan64CharactersAreReplaced() {
        assertThat(CorrelationId.resolve("a".repeat(64))).isEqualTo("a".repeat(64));
        assertThat(CorrelationId.resolve("a".repeat(65))).hasSize(36);
    }

    @Test
    void withATraceTheTraceIdIsTheCorrelationIdAndAValidIncomingIdIsKeptAsClientCorrelationId() throws Exception {
        CorrelationIdFilter tracing = new CorrelationIdFilter(() -> TRACE_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/apps");
        request.addHeader(CorrelationId.HEADER, "client.req-42_A");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        tracing.doFilter(
                request,
                response,
                (req, res) -> duringRequest.set(
                        MDC.get(CorrelationId.MDC_KEY) + "|" + MDC.get(CorrelationId.CLIENT_MDC_KEY)));

        assertThat(duringRequest).hasValue(TRACE_ID + "|client.req-42_A");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(TRACE_ID);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
        assertThat(MDC.get(CorrelationId.CLIENT_MDC_KEY))
                .as("MDC cleared after the request")
                .isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"has space", "line\nbreak", "<script>"})
    void withATraceMissingOrUnsafeIncomingIdsAreNotKept(String incoming) throws Exception {
        CorrelationIdFilter tracing = new CorrelationIdFilter(() -> TRACE_ID);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/apps");
        if (incoming != null) {
            request.addHeader(CorrelationId.HEADER, incoming);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        tracing.doFilter(
                request,
                response,
                (req, res) -> duringRequest.set(
                        MDC.get(CorrelationId.MDC_KEY) + "|" + MDC.get(CorrelationId.CLIENT_MDC_KEY)));

        assertThat(duringRequest).hasValue(TRACE_ID + "|null");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(TRACE_ID);
    }

    @Test
    void withoutATracerOrSpanThereIsNoTraceId() {
        assertThat(CorrelationIdFilter.traceIdOf(null)).isNull();
        assertThat(CorrelationIdFilter.traceIdOf(Tracer.NOOP)).isNull();
    }

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
}
