package com.prismprd.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** Bounds JSON bodies even when the client uses chunked transfer without Content-Length. */
@RestControllerAdvice
public class RequestBodyLimits extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 2 * 1024 * 1024;

    public static class TooLargeException extends BizException {
        public TooLargeException() { super(413, "请求体不能超过 2 MiB"); }
    }

    @Override
    public boolean supports(MethodParameter parameter, Type type,
                            Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage message, MethodParameter parameter, Type type,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if (message.getHeaders().getContentLength() > MAX_BYTES) throw new TooLargeException();
        InputStream limited = new FilterInputStream(message.getBody()) {
            private int count;
            private void add(int n) {
                if (n > 0 && (count += n) > MAX_BYTES) throw new TooLargeException();
            }
            @Override public int read() throws IOException {
                int value = in.read();
                if (value >= 0) add(1);
                return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int n = in.read(bytes, offset, Math.min(length, MAX_BYTES - count + 1));
                add(n);
                return n;
            }
        };
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return limited; }
            @Override public HttpHeaders getHeaders() { return message.getHeaders(); }
        };
    }
}
