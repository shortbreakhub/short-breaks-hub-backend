package com.shortbreakshub.controller;


import com.shortbreakshub.dto.AddMessageRequest;
import com.shortbreakshub.dto.CommunityItineraryQuestionThreadDto;
import com.shortbreakshub.dto.CommunityItineraryQuestionThreadSummaryDto;
import com.shortbreakshub.dto.CreateQuestionThreadRequest;
import com.shortbreakshub.model.User;
import com.shortbreakshub.service.CommunityItineraryQuestionService;
import com.shortbreakshub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community-itineraries")
@RequiredArgsConstructor
public class CommunityItineraryQuestionController {

    private final CommunityItineraryQuestionService questionService;
    private final UserService userService;


    @PostMapping("/{itineraryId}/question-threads")
    public ResponseEntity<CommunityItineraryQuestionThreadDto> createThread(
            HttpServletRequest request,
            @PathVariable Long itineraryId,
            @RequestBody CreateQuestionThreadRequest body
    ) {
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User currentUser = userService.getUserById(userId);

        CreateQuestionThreadRequest createThreadRequest =
                new CreateQuestionThreadRequest(itineraryId, body.content());

        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.createThread(currentUser, createThreadRequest));
    }


    @PostMapping("/{itineraryId}/question-threads/{threadId}/messages")
    public ResponseEntity<CommunityItineraryQuestionThreadDto> addMessage(
            HttpServletRequest request,
            @PathVariable Long itineraryId,
            @PathVariable Long threadId,
            @RequestBody AddMessageRequest body
    ) {
        System.out.println("testing");
        Long userId = (Long) request.getAttribute("authUserId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User currentUser = userService.getUserById(userId);

        AddMessageRequest addMessageReq= new AddMessageRequest(itineraryId,threadId, body.content());

        return ResponseEntity.ok().body(questionService.addMessageToThread(currentUser, addMessageReq)) ;
    }


    @GetMapping("/{itineraryId}/question-threads")
    public ResponseEntity<List<CommunityItineraryQuestionThreadSummaryDto>> getThreads(
            @PathVariable Long itineraryId, HttpServletRequest request
    ) {
        return ResponseEntity.ok(questionService.getThreadsForItinerary(itineraryId, (Long) request.getAttribute("authUserId")));
    }

    @GetMapping("/{itineraryId}/question-threads/{threadId}")
    public ResponseEntity<CommunityItineraryQuestionThreadDto> getThread(
            @PathVariable Long itineraryId,
            @PathVariable Long threadId, HttpServletRequest request
    ) {
        return ResponseEntity.ok(questionService.getThreadById(itineraryId, threadId, (Long) request.getAttribute("authUserId")));
    }

}

