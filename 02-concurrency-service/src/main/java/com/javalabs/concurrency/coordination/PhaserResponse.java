package com.javalabs.concurrency.coordination;

import java.util.List;

public record PhaserResponse(List<String> events, long elapsedMs, String note) {
}
