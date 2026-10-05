package com.javalabs.pattern.adapter;

public record AdapterChargeResponse(String lab, String orderId, String provider, boolean success, String lesson) {
}
