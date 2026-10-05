package com.javalabs.pattern.template.good;

import java.math.BigDecimal;

record BankAProviderRequest(String merchantOrder, BigDecimal total, String currency) {
}
