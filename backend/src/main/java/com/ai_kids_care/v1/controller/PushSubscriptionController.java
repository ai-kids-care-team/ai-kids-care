package com.ai_kids_care.v1.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Push subscription management API — NOT YET PUBLISHED (see notifications spec).
 * Registered for routing, but exposes no handler methods; any request returns 405.
 */
@Tag(name = "PushSubscription")
@RestController
@RequestMapping("/api/v1/push_subscriptions")
public class PushSubscriptionController {
}
