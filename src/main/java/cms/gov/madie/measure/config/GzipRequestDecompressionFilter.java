package cms.gov.madie.measure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Decompresses request bodies sent with {@code Content-Encoding: gzip}, so the browser can compress
 * large JSON bodies (e.g. the QDM test case Excel export) to stay under the CMS WAF request size
 * limit. Other requests pass through untouched.
 */
@Component
public class GzipRequestDecompressionFilter extends OncePerRequestFilter {

  /** Zip-bomb guard: the most a body may decompress to. Matches excel-export's 50mb limit. */
  static final long MAX_DECOMPRESSED_BYTES = 50L * 1024 * 1024;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String encoding = request.getHeader(HttpHeaders.CONTENT_ENCODING);
    return encoding == null || !"gzip".equalsIgnoreCase(encoding.trim());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    filterChain.doFilter(new DecompressedRequest(request), response);
  }

  /**
   * Serves the decompressed body, and hides Content-Length and Content-Encoding because they
   * describe the compressed body (Spring's String and byte[] converters read exactly Content-Length
   * bytes).
   */
  private static class DecompressedRequest extends HttpServletRequestWrapper {

    private ServletInputStream body;

    DecompressedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      // Lazy, so invalid gzip fails while Spring reads the body (400), not in the filter (500).
      if (body == null) {
        body = new GunzipInputStream(super.getInputStream());
      }
      return body;
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
      return -1;
    }

    @Override
    public long getContentLengthLong() {
      return -1;
    }

    @Override
    public String getHeader(String name) {
      return isCompressedBodyHeader(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return isCompressedBodyHeader(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return Collections.enumeration(
          Collections.list(super.getHeaderNames()).stream()
              .filter(name -> !isCompressedBodyHeader(name))
              .toList());
    }

    private static boolean isCompressedBodyHeader(String name) {
      return HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
          || HttpHeaders.CONTENT_ENCODING.equalsIgnoreCase(name);
    }
  }

  /** Gunzips the request body, failing once it exceeds {@link #MAX_DECOMPRESSED_BYTES}. */
  private static class GunzipInputStream extends ServletInputStream {

    private final InputStream gunzip;
    private long bytesRead;
    private boolean finished;

    GunzipInputStream(InputStream compressed) throws IOException {
      this.gunzip = new GZIPInputStream(compressed);
    }

    @Override
    public int read() throws IOException {
      int b = gunzip.read();
      count(b == -1 ? -1 : 1);
      return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int n = gunzip.read(buffer, offset, length);
      count(n);
      return n;
    }

    private void count(int n) throws IOException {
      if (n == -1) {
        finished = true;
        return;
      }
      bytesRead += n;
      if (bytesRead > MAX_DECOMPRESSED_BYTES) {
        throw new IOException(
            "Decompressed request body exceeds " + MAX_DECOMPRESSED_BYTES + " bytes");
      }
    }

    @Override
    public boolean isFinished() {
      return finished;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      throw new UnsupportedOperationException("Non-blocking reads are not supported");
    }

    @Override
    public void close() throws IOException {
      gunzip.close();
    }
  }
}
