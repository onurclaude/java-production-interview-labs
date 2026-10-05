package com.javalabs.pattern.adapter.external;

/** Bank B tutarı KURUŞ/CENT biriminde (minor units) ister — Bank A'nın BigDecimal'inden tamamen farklı bir sözleşme. */
public record BankBPayload(long amountInMinorUnits, String orderRef, String currencyCode) {
}
