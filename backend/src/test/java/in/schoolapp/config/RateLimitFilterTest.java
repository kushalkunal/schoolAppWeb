package in.schoolapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis-backed fixed-window rate limiter: allow under cap, 429 over cap, fail-open on Redis error. */
class RateLimitFilterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private RateLimitFilter filter() {
        RateLimitFilter f = new RateLimitFilter(mapper, redis);
        ReflectionTestUtils.setField(f, "capacity", 2);
        ReflectionTestUtils.setField(f, "windowSeconds", 60L);
        ReflectionTestUtils.setField(f, "enabled", true);
        when(redis.opsForValue()).thenReturn(ops);
        return f;
    }

    private HttpServletRequest req() {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getRequestURI()).thenReturn("/api/v1/students");
        when(r.getRemoteAddr()).thenReturn("10.0.0.7");
        return r;
    }

    @Test
    void allowsRequestUnderCapAndSetsTtlOnFirstHit() throws Exception {
        when(ops.increment(anyString())).thenReturn(1L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(req(), res, chain);

        verify(chain).doFilter(any(), any());                 // request passed through
        verify(redis).expire(anyString(), any());             // TTL set on first hit of window
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksRequestOverCapWith429() throws Exception {
        when(ops.increment(anyString())).thenReturn(3L);       // capacity is 2
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(req(), res, chain);

        verify(chain, never()).doFilter(any(), any());         // blocked
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void failsOpenWhenRedisUnavailable() throws Exception {
        when(ops.increment(anyString())).thenThrow(new RuntimeException("connection refused"));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter().doFilter(req(), res, chain);

        verify(chain).doFilter(any(), any());                  // allowed despite Redis being down
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
