package dev.railroadide.railroad.project.onboarding.flow;

import dev.railroadide.railroad.project.onboarding.OnboardingContext;
import dev.railroadide.railroad.project.onboarding.step.OnboardingStep;
import lombok.Getter;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OnboardingFlow {
    private final Map<String, Supplier<OnboardingStep>> stepLookup;
    @Getter
    private final List<OnboardingTransition> transitions;
    @Getter
    private final String firstStepId;

    public OnboardingFlow(Map<String, Supplier<OnboardingStep>> stepLookup, List<OnboardingTransition> transitions, String firstStepId) {
        this.stepLookup = Map.copyOf(stepLookup);
        this.transitions = List.copyOf(transitions);
        this.firstStepId = firstStepId;
    }

    public static OnboardingFlowBuilder builder() {
        return new OnboardingFlowBuilder();
    }

    public Supplier<OnboardingStep> lookup(String id) {
        return stepLookup.get(id);
    }

    public int getTotalSteps() {
        return stepLookup.size();
    }

    public static class OnboardingFlowBuilder {
        private final LinkedHashMap<String, Supplier<OnboardingStep>> stepLookup = new LinkedHashMap<>();
        private final List<OnboardingTransition> transitions = new ArrayList<>();
        private String firstStepId;

        public OnboardingFlowBuilder addStep(String id, Supplier<OnboardingStep> step) {
            stepLookup.put(id, step);
            if (firstStepId == null) firstStepId = id;
            return this;
        }

        public OnboardingFlowBuilder firstStep(String id) {
            this.firstStepId = id;
            return this;
        }

        public OnboardingFlowBuilder addConditionalTransition(String from, String to, Predicate<OnboardingContext> condition) {
            transitions.add(new OnboardingTransition(from, to, condition));
            return this;
        }

        public List<OnboardingTransition> getTransitionsTo(String stepId) {
            return transitions.stream().filter(t -> t.getToStepId().equals(stepId)).collect(Collectors.toList());
        }

        public List<OnboardingTransition> getTransitionsFrom(String stepId) {
            return transitions.stream().filter(t -> t.getFromStepId().equals(stepId)).collect(Collectors.toList());
        }

        public void removeTransition(OnboardingTransition transition) {
            transitions.remove(transition);
        }

        public OnboardingFlow build() {
            List<String> keys = new ArrayList<>(stepLookup.keySet());
            for (int i = 0; i < keys.size() - 1; i++) {
                String from = keys.get(i);
                String to = keys.get(i + 1);
                if (transitions.stream().noneMatch(t -> t.getFromStepId().equals(from) && t.getToStepId().equals(to))) {
                    transitions.add(new OnboardingTransition(from, to, null));
                }
            }
            return new OnboardingFlow(stepLookup, transitions, firstStepId);
        }
    }
}
