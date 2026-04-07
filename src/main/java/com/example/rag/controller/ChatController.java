package com.example.rag.controller;

import com.example.rag.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ask")
    @CrossOrigin(origins = "*")
    public String ask(@RequestBody String message) {
        return chatService.ask(message);
    }

    @CrossOrigin(origins = "*")
    @PostMapping(
            value = "/ask/stream",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> askStream(@RequestBody String message) {
        return chatService.askStream(message);
    }

    // ✅ New token streaming endpoint
    @PostMapping(value = "/ask/stream-token", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStreamToken(@RequestBody String message,
                                       @RequestParam(defaultValue = "default") String sessionId) {
        return chatService.askStreamTokens(message, sessionId);
    }

}
