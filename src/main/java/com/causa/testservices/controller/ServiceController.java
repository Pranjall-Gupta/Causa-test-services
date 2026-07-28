package com.causa.testservices.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ServiceController {

    @Value("${service.name:unknown-service}")
    private String serviceName;

    @Autowired
    private RestTemplate restTemplate;

    // Static variables for chaos state across requests
    private static volatile boolean latencyChaosEnabled = false;
    private static volatile boolean errorChaosEnabled = false;

    @PostMapping("/chaos/enable")
    public String enableChaos() {
        latencyChaosEnabled = true;
        errorChaosEnabled = true;
        return "Chaos injection ENABLED on " + serviceName;
    }

    @PostMapping("/chaos/disable")
    public String disableChaos() {
        latencyChaosEnabled = false;
        errorChaosEnabled = false;
        return "Chaos injection DISABLED on " + serviceName;
    }

    @GetMapping("/**")
    public ResponseEntity<String> handleRequest() {
        // Chaos behavior for payment-service
        if ("payment-service".equals(serviceName)) {
            if (latencyChaosEnabled) {
                // Latency injection: random 1.8s to 2.8s
                long delay = 1800 + (long) (Math.random() * 1000);
                try { Thread.sleep(delay); } catch (InterruptedException e) {}
            }
            if (errorChaosEnabled) {
                // Error injection: returns 500
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Chaos Error: Payment Gateway Timeout (504)");
            }
            return ResponseEntity.ok("Payment successful (chaos disabled)");
        }

        // Behavior for inventory-service
        if ("inventory-service".equals(serviceName)) {
            return ResponseEntity.ok("Stock available");
        }

        // Behavior for checkout-api
        if ("checkout-api".equals(serviceName)) {
            try {
                // Call order-service
                String orderResponse = restTemplate.getForObject("http://localhost:8082/order", String.class);
                return ResponseEntity.ok("Checkout successful. Response: " + orderResponse);
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Checkout transaction failed: " + e.getMessage());
            }
        }

        // Behavior for order-service
        if ("order-service".equals(serviceName)) {
            try {
                // Call payment-service
                String paymentResponse = restTemplate.getForObject("http://localhost:8083/payment", String.class);
                // Call inventory-service
                String inventoryResponse = restTemplate.getForObject("http://localhost:8084/inventory", String.class);
                
                return ResponseEntity.ok("Order processed. [Payment: " + paymentResponse + ", Inventory: " + inventoryResponse + "]");
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Order processing aborted: " + e.getMessage());
            }
        }

        return ResponseEntity.ok("Response from generic service: " + serviceName);
    }
}
