package com.javalabs.pattern.builder;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/labs/builder")
public class BuilderLabController {

    /**
     * Builder'ı kullanarak {@link ProviderPaymentRequest} inşa eder ve TÜM alanları (opsiyonel olanlar
     * default değerleriyle) geri döner — her alanın adıyla set edildiğini ve immutable nesnenin doğru
     * oluştuğunu gözlemleyebilirsiniz.
     */
    @PostMapping("/good")
    public ProviderPaymentResponse good(@RequestBody BuilderLabRequest request) {
        ProviderPaymentRequest providerRequest = ProviderPaymentRequest.builder()
                .orderId(request.orderId())
                .customerId(request.customerId())
                .amount(request.amount())
                .description(request.description())
                .callbackUrl(request.callbackUrl())
                .installment(request.installment() == null ? 0 : request.installment())
                .merchantReference(request.merchantReference())
                .metadata("source", "builder-lab")
                .build();

        return new ProviderPaymentResponse("BUILDER_GOOD", providerRequest.orderId(), providerRequest.customerId(),
                providerRequest.amount(), providerRequest.currency(), providerRequest.description(),
                providerRequest.callbackUrl(), providerRequest.installment(), providerRequest.merchantReference(),
                providerRequest.metadata());
    }
}
