package org.dave.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final List<String> ignoredPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestLoggingFilter(List<String> ignoredPaths) {
        this.ignoredPaths = ignoredPaths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (ignored(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long took = System.currentTimeMillis() - start;
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), took);
        }
    }

    private boolean ignored(String uri) {
        return ignoredPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }
}
