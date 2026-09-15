package net.rocky.coresuite.module.website;

import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebsiteRequestTest {
    @Test void acceptsUtf8BodyWithoutChangingSignedContent() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        String body = "{\"name\":\"Player \u2603\"}\n";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        assertEquals(body, WebsiteModule.readRequestBody(exchange));
        verify(exchange, never()).close();
    }
    @Test void acceptsBodyExactlyAtLimit() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[65536]));
        assertEquals(65536, WebsiteModule.readRequestBody(exchange).length());
        verify(exchange, never()).close();
    }
    @Test void oversizedBodyIsRejectedWithoutReadingTheEntireStream() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[100000]);
        when(exchange.getRequestBody()).thenReturn(stream);
        assertNull(WebsiteModule.readRequestBody(exchange));
        assertEquals(100000 - 65537, stream.available());
        verify(exchange).sendResponseHeaders(413, -1);
        verify(exchange).close();
    }
}
