/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.watcher.execution;

import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.watcher.actions.Action;
import org.elasticsearch.watcher.actions.ActionWrapper;
import org.elasticsearch.watcher.condition.Condition;
import org.elasticsearch.watcher.input.Input;
import org.elasticsearch.watcher.trigger.manual.ManualTriggerEvent;
import org.elasticsearch.watcher.watch.Watch;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

/**
 */
public class ManualExecutionContext extends WatchExecutionContext {

    private final Map<String, ActionExecutionMode> actionModes;
    private final boolean recordExecution;
    private final boolean knownWatch;

    ManualExecutionContext(Watch watch, boolean knownWatch, DateTime executionTime, ManualTriggerEvent triggerEvent,
                           TimeValue defaultThrottlePeriod, Input.Result inputResult, Condition.Result conditionResult,
                           Map<String, ActionExecutionMode> actionModes, boolean recordExecution) {

        super(watch, executionTime, triggerEvent, defaultThrottlePeriod);

        this.actionModes = actionModes;
        this.recordExecution = recordExecution;
        this.knownWatch = knownWatch;

        if (inputResult != null) {
            onInputResult(inputResult);
        }
        if (conditionResult != null) {
            onConditionResult(conditionResult);
        }
        ActionExecutionMode allMode = actionModes.get(Builder.ALL);
        if (allMode == null || allMode == ActionExecutionMode.SKIP) {
            boolean throttleAll = allMode == ActionExecutionMode.SKIP;
            for (ActionWrapper action : watch.actions()) {
                if (throttleAll) {
                    onActionResult(new ActionWrapper.Result(action.id(), new Action.Result.Throttled(action.action().type(), "manually skipped")));
                } else {
                    ActionExecutionMode mode = actionModes.get(action.id());
                    if (mode == ActionExecutionMode.SKIP) {
                        onActionResult(new ActionWrapper.Result(action.id(), new Action.Result.Throttled(action.action().type(), "manually skipped")));
                    }
                }
            }
        }
    }

    @Override
    public boolean knownWatch() {
        return knownWatch;
    }

    @Override
    public final boolean simulateAction(String actionId) {
        ActionExecutionMode mode = actionModes.get(Builder.ALL);
        if (mode == null) {
            mode = actionModes.get(actionId);
        }
        return mode != null && mode.simulate();
    }

    @Override
    public boolean skipThrottling(String actionId) {
        ActionExecutionMode mode = actionModes.get(Builder.ALL);
        if (mode == null) {
            mode = actionModes.get(actionId);
        }
        return mode != null && mode.force();
    }

    @Override
    public final boolean recordExecution() {
        return recordExecution;
    }

    public static Builder builder(Watch watch, boolean knownWatch, ManualTriggerEvent event, TimeValue defaultThrottlePeriod) {
        return new Builder(watch, knownWatch, event, defaultThrottlePeriod);
    }

    public static class Builder {

        static final String ALL = "_all";

        private final Watch watch;
        private final boolean knownWatch;
        private final ManualTriggerEvent triggerEvent;
        private final TimeValue defaultThrottlePeriod;
        protected DateTime executionTime;
        private boolean recordExecution = false;
        private Map<String, ActionExecutionMode> actionModes = new HashMap<>();
        private Input.Result inputResult;
        private Condition.Result conditionResult;

        private Builder(Watch watch, boolean knownWatch, ManualTriggerEvent triggerEvent, TimeValue defaultThrottlePeriod) {
            this.watch = watch;
            this.knownWatch = knownWatch;
            assert triggerEvent != null;
            this.triggerEvent = triggerEvent;
            this.defaultThrottlePeriod = defaultThrottlePeriod;
        }

        public Builder executionTime(DateTime executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder recordExecution(boolean recordExecution) {
            this.recordExecution = recordExecution;
            return this;
        }

        public Builder allActionsMode(ActionExecutionMode mode) {
            return actionMode(ALL, mode);
        }

        public Builder actionMode(String id, ActionExecutionMode mode) {
            if (actionModes == null) {
                throw new IllegalStateException("ManualExecutionContext has already been built!");
            }
            if (ALL.equals(id)) {
                actionModes = new HashMap<>();
            }
            actionModes.put(id, mode);
            return this;
        }

        public Builder withInput(Input.Result inputResult) {
            this.inputResult = inputResult;
            return this;
        }

        public Builder withCondition(Condition.Result conditionResult) {
            this.conditionResult = conditionResult;
            return this;
        }

        public ManualExecutionContext build() {
            if (executionTime == null) {
                executionTime = DateTime.now(DateTimeZone.UTC);
            }
            ManualExecutionContext context = new ManualExecutionContext(watch, knownWatch, executionTime, triggerEvent, defaultThrottlePeriod, inputResult, conditionResult, unmodifiableMap(actionModes), recordExecution);
            actionModes = null;
            return context;
        }
    }
}
