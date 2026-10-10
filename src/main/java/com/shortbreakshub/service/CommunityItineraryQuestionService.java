package com.shortbreakshub.service;


import com.shortbreakshub.dto.AddMessageRequest;
import com.shortbreakshub.dto.CommunityItineraryQuestionThreadSummaryDto;
import com.shortbreakshub.dto.CreateQuestionThreadRequest;
import com.shortbreakshub.dto.CommunityItineraryQuestionThreadDto;
import com.shortbreakshub.model.*;

import com.shortbreakshub.repository.CommunityItineraryQuestionThreadRepository;
import com.shortbreakshub.repository.CommunityItineraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityItineraryQuestionService {

    private final CommunityItineraryQuestionThreadRepository threadRepository;
    private final CommunityItineraryRepository communityItineraryRepository;
    private static final int MAX_MESSAGES_PER_THREAD = 4;


    public CommunityItineraryQuestionThreadDto createThread(User currentUser,
                                                            CreateQuestionThreadRequest request) {

        // 1) Validate input
        if (request.communityItineraryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "communityItineraryId is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question content cannot be empty");
        }

        // 2) Load the community itinerary
        CommunityItinerary itinerary = communityItineraryRepository.findById(request.communityItineraryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Community itinerary not found"
                ));

        requireVisibleItinerary(itinerary.getId(), currentUser.getId());

        // 3) Prevent creator from asking themselves a question
        if (itinerary.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Creators cannot ask questions on their own itinerary."
            );
        }

        String trimmedContent = request.content().trim();
        if (trimmedContent.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message too long");
        }

        boolean exists = threadRepository.existsByCommunityItineraryIdAndAskerId(
                request.communityItineraryId(), currentUser.getId()
        );

        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You already have a thread for this itinerary.");
        }



        // 4) Create the thread
        CommunityItineraryQuestionThread thread = new CommunityItineraryQuestionThread();
        thread.setCommunityItinerary(itinerary);
        thread.setAsker(currentUser);
        thread.setClosed(false);

        // 5) Create the initial message (traveller's question)
        CommunityItineraryQuestionMessage message = new CommunityItineraryQuestionMessage();
        message.setThread(thread);
        message.setSender(QuestionMessageSender.TRAVELLER);
        message.setContent(trimmedContent);

        // 6) Add message to thread's list (because of cascade = ALL, saving thread will save message)
        thread.getMessages().add(message);

        // 7) Save and return DTO
        CommunityItineraryQuestionThread saved = threadRepository.save(thread);
        return CommunityItineraryQuestionThreadDto.from(saved);
    }

    public CommunityItineraryQuestionThreadDto addMessageToThread(
            User currentUser,
            AddMessageRequest request
    ) {
        if (request.threadId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "threadId is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content cannot be empty");
        }

        // 1) Load thread + related itinerary
        CommunityItineraryQuestionThread thread = threadRepository.findById(request.threadId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Question thread not found"
                ));

        if (!thread.getCommunityItinerary().getId().equals(request.itineraryId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This thread does not belong to the specified itinerary."
            );
        }


        requireVisibleItinerary(thread.getCommunityItinerary().getId(), currentUser.getId());

        if (thread.isClosed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thread is closed");
        }

        CommunityItinerary itinerary = thread.getCommunityItinerary();
        User asker = thread.getAsker();
        User creator = itinerary.getUser();

        // 2) Check currentUser is either asker or creator
        boolean isAsker = asker.getId().equals(currentUser.getId());
        boolean isCreator = creator.getId().equals(currentUser.getId());

        if (!isAsker && !isCreator) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the asker or the itinerary creator can write in this thread."
            );
        }

        List<CommunityItineraryQuestionMessage> messages = thread.getMessages();

        ensureCorrectTurn(messages, isAsker, isCreator);

        // 4) Create new message
        QuestionMessageSender sender = isAsker
                ? QuestionMessageSender.TRAVELLER
                : QuestionMessageSender.CREATOR;

        CommunityItineraryQuestionMessage message = new CommunityItineraryQuestionMessage();
        message.setThread(thread);
        message.setSender(sender);
        message.setContent(request.content().trim());

        // 5) Add to thread's messages list
        messages.add(message);

        // 6) Optional: auto-close after 4 messages (1Q + 1A + 1 follow-up + 1 final answer)
        if (messages.size() >= MAX_MESSAGES_PER_THREAD) {
            thread.setClosed(true);
        }

        // 7) Save and return
        CommunityItineraryQuestionThread saved = threadRepository.save(thread);
        return CommunityItineraryQuestionThreadDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CommunityItineraryQuestionThreadSummaryDto> getThreadsForItinerary(Long itineraryId, Long userId) {
        requireVisibleItinerary(itineraryId, userId);
        return threadRepository.findByCommunityItineraryId(itineraryId)
                .stream()
                .map(CommunityItineraryQuestionThreadSummaryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityItineraryQuestionThreadDto getThreadById(Long itineraryId, Long threadId, Long userId) {
        requireVisibleItinerary(itineraryId, userId);
        var thread = threadRepository.findById(threadId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found"));

        if (!thread.getCommunityItinerary().getId().equals(itineraryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found");
        }
        return CommunityItineraryQuestionThreadDto.from(thread);
    }

    private void requireVisibleItinerary(Long itineraryId, Long userId) {
        var itinerary = communityItineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Itinerary not found"));
        if (itinerary.getVisibility() != Visibility.PUBLIC && !itinerary.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Itinerary not found");
        }
    }

    private void ensureCorrectTurn(
            List<CommunityItineraryQuestionMessage> messages,
            boolean isAsker,
            boolean isCreator
    ) {
        if (messages.isEmpty()) {
            if (!isAsker) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only the traveller can start a new question thread."
                );
            }
            return;
        }

        boolean messageCountIsEven = (messages.size() % 2 == 0);

        boolean nextIsCreator = !messageCountIsEven;
        boolean nextIsTraveller = messageCountIsEven;


        if (nextIsCreator && !isCreator) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You must wait for the itinerary creator to reply."
            );
        }

        if (nextIsTraveller && !isAsker) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only the traveller can send the next message."
            );
        }
    }


}