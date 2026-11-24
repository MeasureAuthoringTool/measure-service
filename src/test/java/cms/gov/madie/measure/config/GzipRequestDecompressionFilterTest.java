package cms.gov.madie.measure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GzipRequestDecompressionFilterTest {

  private static final String JSON = "[{\"groupId\":\"g1\",\"testCaseExecutionResults\":[]}]";

  private final GzipRequestDecompressionFilter filter = new GzipRequestDecompressionFilter();

  @Test
  void passesThroughRequestWithoutContentEncoding() throws Exception {
    MockHttpServletRequest request = jsonRequest(JSON.getBytes(StandardCharsets.UTF_8));

    assertSame(request, filter(request));
  }

  @Test
  void passesThroughOtherContentEncodings() throws Exception {
    MockHttpServletRequest request = jsonRequest(JSON.getBytes(StandardCharsets.UTF_8));
    request.addHeader(HttpHeaders.CONTENT_ENCODING, "br");

    assertSame(request, filter(request));
  }

  @Test
  void decompressesGzipBodyAndHidesHeadersOfCompressedBody() throws Exception {
    byte[] gzipped = gzip(JSON.getBytes(StandardCharsets.UTF_8));
    MockHttpServletRequest request = jsonRequest(gzipped);
    request.addHeader(HttpHeaders.CONTENT_ENCODING, " GZIP "); // values are case-insensitive
    request.addHeader(HttpHeaders.CONTENT_LENGTH, gzipped.length);

    HttpServletRequest wrapped = filter(request);

    assertNotSame(request, wrapped);
    ServletInputStream body = wrapped.getInputStream();
    assertEquals(JSON, new String(body.readAllBytes(), StandardCharsets.UTF_8));
    assertTrue(body.isFinished());
    assertSame(body, wrapped.getInputStream());

    assertEquals(-1, wrapped.getContentLength());
    assertEquals(-1, wrapped.getContentLengthLong());
    assertNull(wrapped.getHeader(HttpHeaders.CONTENT_LENGTH));
    assertNull(wrapped.getHeader(HttpHeaders.CONTENT_ENCODING));
    assertFalse(wrapped.getHeaders(HttpHeaders.CONTENT_LENGTH).hasMoreElements());
    assertFalse(wrapped.getHeaders(HttpHeaders.CONTENT_ENCODING).hasMoreElements());
    List<String> headerNames = Collections.list(wrapped.getHeaderNames());
    assertEquals(List.of(HttpHeaders.CONTENT_TYPE), headerNames);
    assertEquals("application/json", wrapped.getHeader(HttpHeaders.CONTENT_TYPE));
  }

  @Test
  void decompressesThroughReader() throws Exception {
    MockHttpServletRequest request = jsonRequest(gzip(JSON.getBytes(StandardCharsets.UTF_8)));
    request.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip");

    HttpServletRequest wrapped = filter(request);

    assertEquals(JSON, wrapped.getReader().lines().collect(Collectors.joining("\n")));
  }

  @Test
  void failsOnceDecompressedBodyExceedsLimit() throws Exception {
    byte[] zeros = new byte[(int) GzipRequestDecompressionFilter.MAX_DECOMPRESSED_BYTES + 1];
    MockHttpServletRequest request = jsonRequest(gzip(zeros));
    request.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip");

    HttpServletRequest wrapped = filter(request);

    IOException ex =
        assertThrows(
            IOException.class,
            () -> wrapped.getInputStream().transferTo(OutputStream.nullOutputStream()));
    assertEquals(
        "Decompressed request body exceeds "
            + GzipRequestDecompressionFilter.MAX_DECOMPRESSED_BYTES
            + " bytes",
        ex.getMessage());
  }

  @Test
  void acceptsBodyOfExactlyTheLimit() throws Exception {
    byte[] zeros = new byte[(int) GzipRequestDecompressionFilter.MAX_DECOMPRESSED_BYTES];
    MockHttpServletRequest request = jsonRequest(gzip(zeros));
    request.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip");

    ServletInputStream body = filter(request).getInputStream();

    assertEquals(zeros.length, body.transferTo(OutputStream.nullOutputStream()));
    assertTrue(body.isFinished());
  }

  @Test
  void invalidGzipFailsWhenBodyIsReadNotInFilter() throws Exception {
    MockHttpServletRequest request = jsonRequest(JSON.getBytes(StandardCharsets.UTF_8));
    request.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip");

    HttpServletRequest wrapped = filter(request); // no exception here

    assertThrows(ZipException.class, wrapped::getInputStream);
  }

  private HttpServletRequest filter(MockHttpServletRequest request) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return (HttpServletRequest) chain.getRequest();
  }

  private static MockHttpServletRequest jsonRequest(byte[] body) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/measures/m1/test-cases/excel");
    request.addHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    request.setContent(body);
    return request;
  }

  private static byte[] gzip(byte[] value) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(value);
    }
    return out.toByteArray();
  }
}
