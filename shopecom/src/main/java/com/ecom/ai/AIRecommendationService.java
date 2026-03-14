package com.ecom.ai;

import com.ecom.model.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AIRecommendationService {

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.model}")
    private String groqModel;

    private final WebClient webClient;

    public AIRecommendationService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .build();
    }

    // ── Get AI product recommendations based on category and current product ──
    @CircuitBreaker(name = "groqAI", fallbackMethod = "getDefaultRecommendationText")
    @Cacheable(value = "aiRecommendations", key = "#category + '_' + #productTitle")
    public String getProductRecommendations(String category, String productTitle, List<Product> similarProducts) {
        log.info("Fetching AI recommendations for: {} in category: {}", productTitle, category);

        String productList = similarProducts.stream()
                .limit(5)
                .map(p -> "- " + p.getTitle() + " (₹" + p.getDiscountPrice() + ")")
                .reduce("", (a, b) -> a + "\n" + b);

        String prompt = String.format(
                "You are a helpful shopping assistant for an Indian e-commerce website. " +
                "A customer is viewing '%s' in the '%s' category. " +
                "Based on these available products:\n%s\n\n" +
                "Write a friendly 2-sentence recommendation in simple English suggesting why they might also like these products. " +
                "Be specific and helpful. Keep it under 60 words.",
                productTitle, category, productList
        );

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", groqModel,
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 150,
                    "temperature", 0.7
            );

            Map response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("choices")) {
                List choices = (List) response.get("choices");
                if (!choices.isEmpty()) {
                    Map choice = (Map) choices.get(0);
                    Map message = (Map) choice.get("message");
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.error("Groq API error: {}", e.getMessage());
        }

        return getDefaultRecommendationText(category, productTitle, similarProducts, null);
    }

    // ── Fallback if Groq API fails — circuit breaker ──
    public String getDefaultRecommendationText(String category, String productTitle,
                                                List<Product> similarProducts, Throwable t) {
        log.warn("Using fallback recommendation for: {}", productTitle);
        return "Customers who viewed " + productTitle + " also loved these " + category + " products. Check them out!";
    }

    // ── AI smart search suggestion ──
    @CircuitBreaker(name = "groqAI", fallbackMethod = "getDefaultSearchSuggestion")
    public String getSearchSuggestion(String searchQuery) {
        String prompt = String.format(
                "A user searched for '%s' on an Indian e-commerce site. " +
                "Suggest 3 better search terms they could try, separated by commas. " +
                "Only respond with the search terms, nothing else.",
                searchQuery
        );

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", groqModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", 50
            );

            Map response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("choices")) {
                List choices = (List) response.get("choices");
                if (!choices.isEmpty()) {
                    Map choice = (Map) choices.get(0);
                    Map message = (Map) choice.get("message");
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.error("Search suggestion error: {}", e.getMessage());
        }

        return getDefaultSearchSuggestion(searchQuery, null);
    }

    public String getDefaultSearchSuggestion(String query, Throwable t) {
        return query + ", buy " + query + ", best " + query;
    }
}
