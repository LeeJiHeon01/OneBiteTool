package com.example.aicook.recipeimport;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Downloads a recipe page and returns its visible text. The address comes from a search result,
 * so it is untrusted: only public http(s) hosts are fetched, redirects are checked the same way,
 * and size and time are limited. The text is used for extraction and checking only, never stored.
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 2_000_000;
    private static final int MAX_TEXT_LENGTH = 20_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** @return the page text, or empty when the page cannot or must not be fetched */
    public Optional<String> fetchText(String url) {
        try {
            URI uri = URI.create(url);
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                if (!isPublicHttp(uri)) {
                    return Optional.empty();
                }
                HttpResponse<InputStream> response = http.send(
                        HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "text/html").GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    Optional<String> location = response.headers().firstValue("Location");
                    if (response.statusCode() / 100 == 3 && location.isPresent()) {
                        uri = uri.resolve(location.get());
                        continue;
                    }
                    if (response.statusCode() != 200 || !isHtml(response)) {
                        return Optional.empty();
                    }
                    return Optional.of(toText(body, uri)).filter(text -> !text.isBlank());
                }
            }
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            log.warn("Page fetch failed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Server-side request forgery guard: no local, private or link-local targets. */
    static boolean isPublicHttp(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (uri.getHost() == null || !("http".equals(scheme) || "https".equals(scheme))) {
            return false;
        }
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private static boolean isHtml(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("html");
    }

    private static String toText(InputStream body, URI uri) throws IOException {
        byte[] bytes = body.readNBytes(MAX_BYTES);
        // A null charset lets jsoup read it from the page's meta tag.
        Document document = Jsoup.parse(new java.io.ByteArrayInputStream(bytes), null, uri.toString());
        document.select("script, style, noscript, nav, header, footer, iframe").remove();
        String text = document.body() == null ? "" : document.body().text();
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }
}
