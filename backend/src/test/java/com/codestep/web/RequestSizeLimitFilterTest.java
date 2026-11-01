package com.codestep.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter();

    /** Sends the body through the filter to a chain that reads it, like Jackson would. */
    private Result send(String method, String uri, byte[] body, boolean declareLength) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri) {
            @Override
            public long getContentLengthLong() {
                return declareLength ? super.getContentLengthLong() : -1;
            }
        };
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Exception> readError = new AtomicReference<>();
        FilterChain readingChain = (req, res) -> {
            try {
                req.getInputStream().readAllBytes();
            } catch (IOException e) {
                readError.set(e);
            }
        };
        filter.doFilter(request, response, readingChain);
        return new Result(response.getStatus(), readError.get());
    }

    private record Result(int status, Exception readError) {
    }

    @Test
    void passesANormalSubmissionThrough() throws Exception {
        Result result = send("POST", "/api/submissions", new byte[50_000], true);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.readError()).isNull();
    }

    @Test
    void refusesADeclaredLengthOverTheCapWithoutReadingIt() throws Exception {
        byte[] body = new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1];
        assertThat(send("POST", "/api/submissions", body, true).status()).isEqualTo(413);
    }

    @Test
    void cutsOffABodyWithoutADeclaredLengthAtTheCap() throws Exception {
        byte[] body = new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1];

        assertThat(send("POST", "/api/submissions", body, false).readError())
                .isInstanceOf(RequestSizeLimitFilter.BodyTooLargeException.class);
    }

    @Test
    void letsASmallChunkedBodyThrough() throws Exception {
        assertThat(send("POST", "/api/submissions", new byte[1_000], false).readError()).isNull();
    }

    @Test
    void leavesReadsAndNonApiPathsAlone() throws Exception {
        byte[] body = new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1];
        assertThat(send("POST", "/elsewhere", body, false).readError()).isNull();
    }
}
