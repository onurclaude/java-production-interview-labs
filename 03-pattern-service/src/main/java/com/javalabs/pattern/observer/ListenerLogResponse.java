package com.javalabs.pattern.observer;

import java.util.List;

public record ListenerLogResponse(List<String> notification, List<String> audit, List<String> analytics,
                                   long totalAnalyticsTracked) {
}
