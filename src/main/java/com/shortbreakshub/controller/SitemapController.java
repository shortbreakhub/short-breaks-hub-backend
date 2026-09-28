package com.shortbreakshub.controller;

import com.shortbreakshub.service.SitemapService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class SitemapController {

    private static final MediaType XML_UTF_8 = new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final SitemapService sitemapService;

    public SitemapController(SitemapService sitemapService) {
        this.sitemapService = sitemapService;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSitemap() {
        return ResponseEntity.ok()
                .contentType(XML_UTF_8)
                .body(sitemapService.generateXml());
    }
}
