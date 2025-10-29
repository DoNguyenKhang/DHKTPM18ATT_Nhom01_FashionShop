package fit.iuh.edu.fashion.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AiChatbotViewController {

    @GetMapping("/ai-chatbot")
    public String showChatbot() {
        return "ai-chatbot";
    }
}

