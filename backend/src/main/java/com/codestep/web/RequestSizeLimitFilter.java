package com.codestep.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Caps API request bodies, as nginx's client_max_body_size did: JSON bodies are otherwise read into
 * memory whole. A declared length over the cap is refused at once; a body sent without one (chunked)
 * is counted as it is read and cut off at the cap.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 256 * 1024;
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    /** Thrown by the counting stream; {@link ApiErrorAdvice} turns it into a 413. */
    public static final class BodyTooLargeException extends IOException {
        BodyTooLargeException() {
            super("Request body is larger than " + MAX_BODY_BYTES + " bytes");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BODY_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body is too large");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() throws IOException {
                return new LimitedInputStream(super.getInputStream());
            }
        }, response);
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long count;

        LimitedInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                counted(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                counted(n);
            }
            return n;
        }

        private void counted(int n) throws BodyTooLargeException {
            count += n;
            if (count > MAX_BODY_BYTES) {
                throw new BodyTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
