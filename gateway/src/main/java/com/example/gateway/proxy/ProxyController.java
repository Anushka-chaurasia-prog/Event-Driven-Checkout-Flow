package com.example.gateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

@RestController
public class ProxyController {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.order-service.url:http://localhost:8081}")
    private String orderServiceUrl;

    @RequestMapping(value = "/api/orders/**")
    public ResponseEntity<byte[]> proxyOrders(
            @RequestBody(required = false) byte[] body,
            HttpMethod method,
            HttpServletRequest request) {

        String path = request.getRequestURI();
        String query = request.getQueryString();
        String targetUrl = orderServiceUrl + path + (query != null ? "?" + query : "");

        HttpHeaders requestHeaders = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!HOP_BY_HOP.contains(name.toLowerCase())) {
                requestHeaders.put(name, Collections.list(request.getHeaders(name)));
            }
        }

        // For GET / HEAD with no body, do NOT attach a byte array to HttpEntity
        // Otherwise HttpURLConnection throws "HTTP method GET doesn't support output"
        HttpEntity<?> entity;
        if (body != null && body.length > 0) {
            entity = new HttpEntity<>(body, requestHeaders);
        } else {
            entity = new HttpEntity<>(requestHeaders);
        }

        try {
            ResponseEntity<byte[]> upstream = restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);
            return filterResponse(upstream.getStatusCode(), upstream.getHeaders(), upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return filterResponse(ex.getStatusCode(), ex.getResponseHeaders(), ex.getResponseBodyAsByteArray());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage().getBytes());
        }
    }

    private ResponseEntity<byte[]> filterResponse(HttpStatusCode status, HttpHeaders upstreamHeaders, byte[] body) {
        HttpHeaders responseHeaders = new HttpHeaders();
        if (upstreamHeaders != null) {
            upstreamHeaders.forEach((key, values) -> {
                if (!HOP_BY_HOP.contains(key.toLowerCase())) {
                    responseHeaders.put(key, values);
                }
            });
        }
        return new ResponseEntity<>(body, responseHeaders, status);
    }
}
