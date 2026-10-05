package com.javalabs.concurrency.cow;

import com.javalabs.concurrency.common.LabInputValidation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/labs/cow")
public class CowLabController {

    private final OrderProcessingListenerRegistry listenerRegistry;
    private final FeatureRuleRegistry featureRuleRegistry;

    public CowLabController(OrderProcessingListenerRegistry listenerRegistry, FeatureRuleRegistry featureRuleRegistry) {
        this.listenerRegistry = listenerRegistry;
        this.featureRuleRegistry = featureRuleRegistry;
    }

    @GetMapping("/listeners")
    public List<String> listeners() {
        return listenerRegistry.list();
    }

    @PostMapping("/listeners/{name}")
    public AddListenerResult addListener(@PathVariable String name) {
        LabInputValidation.requireNonBlank("name", name);
        return listenerRegistry.add(name);
    }

    @DeleteMapping("/listeners/{name}")
    public RemoveListenerResult removeListener(@PathVariable String name) {
        return listenerRegistry.remove(name);
    }

    @PostMapping("/listeners/demo/concurrent-iteration")
    public ConcurrentIterationDemoResult demoConcurrentIteration(@RequestBody ConcurrentIterationDemoRequest request) {
        LabInputValidation.requireNonBlank("nameToAddDuringIteration", request.nameToAddDuringIteration());
        return listenerRegistry.demoConcurrentIteration(request.nameToAddDuringIteration());
    }

    @GetMapping("/feature-rules")
    public List<String> featureRules() {
        return featureRuleRegistry.list();
    }

    @PostMapping("/feature-rules/{name}")
    public AddRuleResult addFeatureRule(@PathVariable String name) {
        LabInputValidation.requireNonBlank("name", name);
        return featureRuleRegistry.add(name);
    }

    @DeleteMapping("/feature-rules/{name}")
    public RemoveRuleResult removeFeatureRule(@PathVariable String name) {
        return featureRuleRegistry.remove(name);
    }
}
