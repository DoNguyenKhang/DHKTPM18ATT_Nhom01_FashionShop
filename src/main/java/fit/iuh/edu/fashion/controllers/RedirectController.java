package fit.iuh.edu.fashion.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Controller to handle VNPay redirect and bypass ngrok warning page
 */
@Controller
public class RedirectController {

    @GetMapping("/payment/redirect")
    public void redirectToVNPay(
            @RequestParam String url,
            HttpServletResponse response
    ) throws IOException {
        // Decode the URL
        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);

        // Create an HTML page that auto-redirects
        response.setContentType("text/html; charset=UTF-8");
        response.getWriter().write(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset=\"UTF-8\">" +
            "    <title>Redirecting to VNPay...</title>" +
            "    <script>" +
            "        window.location.href = '" + decodedUrl.replace("'", "\\'") + "';" +
            "    </script>" +
            "</head>" +
            "<body>" +
            "    <p>Đang chuyển hướng đến VNPay...</p>" +
            "    <p>Redirecting to VNPay...</p>" +
            "    <p>If you are not redirected automatically, <a href=\"" + decodedUrl + "\">click here</a>.</p>" +
            "</body>" +
            "</html>"
        );
    }
}

