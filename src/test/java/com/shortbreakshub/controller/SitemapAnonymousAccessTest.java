package com.shortbreakshub.controller;

import com.shortbreakshub.config.SecurityConfig;
import com.shortbreakshub.repository.ItineraryRepository;
import com.shortbreakshub.security.JwtService;
import com.shortbreakshub.service.SitemapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SitemapController.class)
@Import({SecurityConfig.class, SitemapService.class})
class SitemapAnonymousAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItineraryRepository itineraryRepository;

    @MockBean
    private JwtService jwtService;

    @Test
    void sitemapIsAvailableWithoutAuthentication() throws Exception {
        when(itineraryRepository.findSitemapLocations()).thenReturn(List.of());

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }
}
