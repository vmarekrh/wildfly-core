/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.extension.core.management;


import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessState.State;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.server.Services;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.core.management.client.Process;
import org.wildfly.extension.core.management.client.Process.RunningState;
import org.wildfly.extension.core.management.client.RuntimeConfigurationStateChangeEvent;
import org.wildfly.extension.core.management.client.RunningStateChangeEvent;
import org.wildfly.extension.core.management.logging.CoreManagementLogger;
import org.wildfly.extension.core.management.client.ProcessStateListener;
import org.wildfly.extension.core.management.client.ProcessStateListenerInitParameters;

/**
 * Service that listens for process state changes and notifies the ProcessStateListener instance of those events.
 * That means when the running state or the runtime configuration state changes.
 * <strong>RunningStateChangeEvent for suspend / resume can only be sent for servers (somain or standalone) but not on HostControllers.</strong>
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2016 Red Hat, inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ProcessStateListenerService implements Service {

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("core", "management", "process-state-listener");

    private final Supplier<ControlledProcessStateService> controlledProcessStateServiceSupplier;
    private final Supplier<SuspendController> suspendControllerSupplier;
    private final Supplier<ExecutorService> executorServiceSupplier;
    private final PropertyChangeListener propertyChangeListener;
    private final OperationListener operationListener;
    private final ProcessStateListener listener;
    private final ProcessStateListenerInitParameters parameters;
    private final String name;
    private final int timeout;
    private final ProcessType processType;
    private final Object stopLock = new Object();

    private volatile Process.RunningState runningState = null;

    private ProcessStateListenerService(ProcessType processType, RunningMode runningMode, String name, ProcessStateListener listener, Map<String, String> properties, int timeout,
                                        final Supplier<ControlledProcessStateService> controlledProcessStateServiceSupplier,
                                        final Supplier<SuspendController> suspendControllerSupplier,
                                        final Supplier<ExecutorService> executorServiceSupplier
    ) {
        CoreManagementLogger.ROOT_LOGGER.debugf("Initalizing ProcessStateListenerService with a running mode of %s", runningMode);
        this.listener = listener;
        this.name = name;
        this.timeout = timeout;
        this.processType = processType;
        this.parameters = new ProcessStateListenerInitParameters.Builder()
                .setInitProperties(properties)
                .setRunningMode(Process.RunningMode.from(runningMode.name()))
                .setProcessType(Process.Type.valueOf(processType.name()))
                .build();
        this.propertyChangeListener = (PropertyChangeEvent evt) -> {
            if ("currentState".equals(evt.getPropertyName())) {
                Process.RuntimeConfigurationState oldState = Process.RuntimeConfigurationState.valueOf(((ControlledProcessState.State) evt.getOldValue()).name());
                Process.RuntimeConfigurationState newState = Process.RuntimeConfigurationState.valueOf(((ControlledProcessState.State) evt.getNewValue()).name());
                transition(oldState, newState);
            }
        };
        this.controlledProcessStateServiceSupplier = controlledProcessStateServiceSupplier;
        this.suspendControllerSupplier = suspendControllerSupplier;
        this.executorServiceSupplier = executorServiceSupplier;
        if (!processType.isHostController()) {
            this.operationListener = new OperationListener() {
                @Override
                public void suspendStarted() {
                    suspendTransition(runningState, Process.RunningState.SUSPENDING);
                }

                @Override
                public void complete() {
                    suspendTransition(runningState, Process.RunningState.SUSPENDED);
                }

                @Override
                public void cancelled() {
                    if(runningState == null || runningState == Process.RunningState.STARTING) {//gracefull startup
                         suspendTransition(Process.RunningState.STARTING, Process.RunningState.SUSPENDED);
                    }
                    switch (runningMode) {
                        case ADMIN_ONLY:
                            suspendTransition(runningState, Process.RunningState.ADMIN_ONLY);
                            break;
                        case NORMAL:
                            suspendTransition(runningState, Process.RunningState.NORMAL);
                            break;
                    }
                }

                @Override
                public void timeout() {
                }
            };
        } else {
            operationListener = null;
        }
    }

    private void transition(Process.RuntimeConfigurationState oldState, Process.RuntimeConfigurationState newState) {
        synchronized (stopLock) {
            if (oldState == newState) {
                return;
            }
            final RuntimeConfigurationStateChangeEvent event = new RuntimeConfigurationStateChangeEvent(oldState, newState);
            Future<?> controlledProcessStateTransition = executorServiceSupplier.get().submit(() -> {
                CoreManagementLogger.ROOT_LOGGER.debugf("Executing runtimeConfigurationStateChanged %s in thread %s", event, Thread.currentThread().getName());
                listener.runtimeConfigurationStateChanged(event);
            });
            try {
                controlledProcessStateTransition.get(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(ex, name);
            } catch (TimeoutException ex) {
                CoreManagementLogger.ROOT_LOGGER.processStateTimeoutError(ex, name);
            } catch (ExecutionException | RuntimeException t) {
                CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(t, name);
            } finally {
                if (!controlledProcessStateTransition.isDone()) {
                    controlledProcessStateTransition.cancel(true);
                }
            }
            switch (newState) {
                case RUNNING:
                    if (RunningState.NORMAL != runningState && RunningState.ADMIN_ONLY != runningState) {
                        if (!processType.isServer()) {
                            if (parameters.getRunningMode() == Process.RunningMode.NORMAL) {
                                suspendTransition(runningState, Process.RunningState.NORMAL);
                            } else {
                                suspendTransition(runningState, Process.RunningState.ADMIN_ONLY);
                            }
                        } else if (runningState == RunningState.STARTING) {
                            suspendTransition(runningState, Process.RunningState.SUSPENDED);
                        }
                    }
                    break;
                case STARTING:
                    if (Process.RunningState.STARTING != runningState) {
                        suspendTransition(runningState, Process.RunningState.STARTING);
                    }
                    break;
                case STOPPING:
                    if (Process.RunningState.STOPPING != runningState) {
                        suspendTransition(runningState, Process.RunningState.STOPPING);
                    }
                    break;
                case STOPPED:
                    if (Process.RunningState.STOPPED != runningState) {
                        suspendTransition(runningState, Process.RunningState.STOPPED);
                    }
                    break;
                case RELOAD_REQUIRED:
                case RESTART_REQUIRED:
                default:
            }
        }
    }

    /**
     * This will <strong>NEVER</strong> be called on a HostController.
     * @param newState the new running state.
     */
    private void suspendTransition(Process.RunningState oldState, Process.RunningState newState) {
        synchronized (stopLock) {
            if (oldState == newState) {
                return;
            }
            this.runningState = newState;
            final RunningStateChangeEvent event = new RunningStateChangeEvent(oldState, newState);
            Future<?> suspendStateTransition = executorServiceSupplier.get().submit(() -> {
                CoreManagementLogger.ROOT_LOGGER.debugf("Executing runningStateChanged %s in thread %s", event, Thread.currentThread().getName());
                listener.runningStateChanged(event);
            });
            try {
                suspendStateTransition.get(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(ex, name);
            } catch (TimeoutException ex) {
                CoreManagementLogger.ROOT_LOGGER.processStateTimeoutError(ex, name);
            } catch (ExecutionException | RuntimeException t) {
                CoreManagementLogger.ROOT_LOGGER.processStateInvokationError(t, name);
            } finally {
                if (!suspendStateTransition.isDone()) {
                    suspendStateTransition.cancel(true);
                }
            }
        }
    }

    static void install(ServiceTarget serviceTarget, ProcessType processType, RunningMode runningMode, String listenerName, ProcessStateListener listener, Map<String, String> properties, int timeout) {
        final ServiceBuilder<?> builder = serviceTarget.addService(SERVICE_NAME.append(listenerName));
        final Supplier<ControlledProcessStateService> cpssSupplier = builder.requires(ControlledProcessStateService.SERVICE_NAME);
        final Supplier<ExecutorService> esSupplier = Services.requireServerExecutor(builder);
        final Supplier<SuspendController> scSupplier = !processType.isHostController() ? builder.requires(JBOSS_SUSPEND_CONTROLLER) : null;
        builder.setInstance(new ProcessStateListenerService(processType, runningMode, listenerName, listener, properties, timeout, cpssSupplier, scSupplier, esSupplier));
        builder.install();
    }

    @Override
    public void start(StartContext context) {
        Runnable task = () -> {
            try {
                ProcessStateListenerService.this.listener.init(parameters);
                controlledProcessStateServiceSupplier.get().addPropertyChangeListener(propertyChangeListener);
                final Supplier<SuspendController> suspendControllerSupplier = ProcessStateListenerService.this.suspendControllerSupplier;
                SuspendController controller = suspendControllerSupplier != null ? suspendControllerSupplier.get() : null;
                if (controller != null) {
                    controller.addListener(operationListener);
                    CoreManagementLogger.ROOT_LOGGER.debugf("Starting ProcessStateListenerService with a SuspendControllerState %s", controller.getState());
                    switch (controller.getState()) {
                        case PRE_SUSPEND:
                            this.runningState = Process.RunningState.PRE_SUSPEND;
                            break;
                        case RUNNING:
                            if (parameters.getRunningMode() == Process.RunningMode.NORMAL) {
                                this.runningState = Process.RunningState.NORMAL;
                            } else {
                                this.runningState = Process.RunningState.ADMIN_ONLY;
                            }
                            break;
                        case SUSPENDED:
                            if (controlledProcessStateServiceSupplier.get().getCurrentState() == State.STARTING) {
                                this.runningState = Process.RunningState.STARTING;
                            } else {
                                this.runningState = Process.RunningState.SUSPENDED;
                            }
                            break;
                        case SUSPENDING:
                            this.runningState = Process.RunningState.SUSPENDING;
                            break;
                    }
                } else {
                    CoreManagementLogger.ROOT_LOGGER.debugf("Starting ProcessStateListenerService with a ControllerProcessState of %s", controlledProcessStateServiceSupplier.get().getCurrentState());
                    if (controlledProcessStateServiceSupplier.get().getCurrentState() == State.STARTING) {
                        this.runningState = Process.RunningState.STARTING;
                    } else {
                        if (parameters.getRunningMode() == Process.RunningMode.NORMAL) {
                            this.runningState = Process.RunningState.NORMAL;
                        } else {
                            this.runningState = Process.RunningState.ADMIN_ONLY;
                        }
                    }
                }
                context.complete();
            } catch (RuntimeException t) {
                context.failed(new StartException(CoreManagementLogger.ROOT_LOGGER.processStateInitError(t, name)));
            }
        };
        try {
            executorServiceSupplier.get().execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        } finally {
            context.asynchronous();
        }
    }

    /**
     * The stop is asynchronous and will wait until the current transition / suspendTransition ends before effectively stopping.
     * This will force the executorService to be Value
     * @param context the stop context.
     */
    @Override
    public void stop(StopContext context) {
        Runnable asyncStop = () -> {
            synchronized (stopLock) {
                controlledProcessStateServiceSupplier.get().removePropertyChangeListener(propertyChangeListener);
                SuspendController controller = suspendControllerSupplier != null ? suspendControllerSupplier.get() : null;
                if (controller != null) {
                    controller.removeListener(operationListener);
                }
                runningState = null;
                try {
                    listener.cleanup();
                } catch (RuntimeException t) {
                    CoreManagementLogger.ROOT_LOGGER.processStateCleanupError(t, name);
                } finally {
                    context.complete();
                }
            }
        };
        final ExecutorService executorService = executorServiceSupplier.get();
        try {
            try {
                executorService.execute(asyncStop);
            } catch (RejectedExecutionException e) {
                asyncStop.run();
            }
        } finally {
            context.asynchronous();
        }
    }
}
