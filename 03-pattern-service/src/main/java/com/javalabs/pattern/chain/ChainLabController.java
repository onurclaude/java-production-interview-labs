package com.javalabs.pattern.chain;

import com.javalabs.pattern.chain.bad.BadCheckoutValidationService;
import com.javalabs.pattern.chain.good.CheckoutRuleChain;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/labs/chain")
public class ChainLabController {

    private final BadCheckoutValidationService badService;
    private final CheckoutRuleChain ruleChain;

    public ChainLabController(BadCheckoutValidationService badService, CheckoutRuleChain ruleChain) {
        this.badService = badService;
        this.ruleChain = ruleChain;
    }

    @PostMapping("/bad")
    public ChainCheckoutResponse bad(@RequestBody ChainCheckoutRequest request) {
        CheckoutContext context = toContext(request);
        List<RuleOutcome> outcomes = badService.validate(context);
        return toResponse("CHAIN_BAD", request.orderId(), outcomes);
    }

    @PostMapping("/good")
    public ChainCheckoutResponse good(@RequestBody ChainCheckoutRequest request) {
        CheckoutContext context = toContext(request);
        List<RuleOutcome> outcomes = ruleChain.run(context);
        return toResponse("CHAIN_GOOD", request.orderId(), outcomes);
    }

    private CheckoutContext toContext(ChainCheckoutRequest request) {
        return new CheckoutContext(request.orderId(), request.customerId(), request.amount(),
                request.customerBlocked(), request.simulateFraud(), request.stockAvailable());
    }

    private ChainCheckoutResponse toResponse(String lab, String orderId, List<RuleOutcome> outcomes) {
        boolean allPassed = outcomes.stream().allMatch(o -> o.status().equals("PASSED"));
        return new ChainCheckoutResponse(lab, orderId, allPassed, outcomes);
    }
}
