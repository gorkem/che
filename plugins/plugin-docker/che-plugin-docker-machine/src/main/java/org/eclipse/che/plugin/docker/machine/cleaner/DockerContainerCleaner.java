/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.docker.machine.cleaner;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.api.machine.server.exception.MachineException;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;
import org.eclipse.che.commons.schedule.ScheduleRate;
import org.eclipse.che.plugin.docker.client.DockerConnector;
import org.eclipse.che.plugin.docker.client.json.ContainerFromList;
import org.eclipse.che.plugin.docker.client.json.Filters;
import org.eclipse.che.plugin.docker.machine.DockerContainerNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.eclipse.che.plugin.docker.machine.DockerContainerNameGenerator.ContainerNameInfo;

/**
 * Job for periodically clean up inactive docker containers
 *
 * @author Alexander Andrienko
 */
@Singleton
public class DockerContainerCleaner implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(DockerContainerCleaner.class);

    private final MachineManager               machineManager;
    private final DockerConnector              dockerConnector;
    private final DockerContainerNameGenerator dockerContainerNameGenerator;

    @Inject
    public DockerContainerCleaner(MachineManager machineManager,
                                  DockerConnector dockerConnector,
                                  DockerContainerNameGenerator dockerContainerNameGenerator) {
        this.machineManager = machineManager;
        this.dockerConnector = dockerConnector;
        this.dockerContainerNameGenerator = dockerContainerNameGenerator;
    }

    @ScheduleRate(period = 5, initialDelay = 5, unit = TimeUnit.MINUTES)//every 5 minute schedule job
    @Override
    public void run() {
        try {
            ContainerFromList[] allDockerContainers = getContainers();
            List<MachineImpl> machines = machineManager.getMachines();

            List<ContainerFromList> unUsedContainers = findUnUsedContainers(allDockerContainers, machines);

            for (ContainerFromList container : unUsedContainers) {
                killContainer(container);
                removeContainerByID(container.getId());
            }
        } catch (IOException e) {
            LOG.error("Failed to get list docker containers");
        } catch (MachineException e) {
            LOG.error("Failed to get list machines to clean up unused containers");
        }
    }

    private ContainerFromList[] getContainers() throws IOException {
        Filters filters = new Filters();
        filters.withFilter("status", "created", "restarting", "running", "paused", "exited");
        return dockerConnector.getContainers(true, 0, null, null, false, filters);
    }

    private List<ContainerFromList> findUnUsedContainers(ContainerFromList[] containers, List<MachineImpl> machines) {
        List<ContainerFromList> unUsedContainers = new ArrayList<>();
        for (ContainerFromList container : containers) {
            final ContainerNameInfo containerNameInfo = dockerContainerNameGenerator.parse(container.getImage());
            if (containerNameInfo == null) {
                continue;
            }
            boolean containerIsUsed = machines.stream()
                                              .allMatch(machine -> machine.getId().equals(containerNameInfo.getMachineId())
                                                                   && machine.getWorkspaceId().equals(containerNameInfo.getWorkspaceId()));
            if (!containerIsUsed) {
                unUsedContainers.add(container);
            }
        }
        return unUsedContainers;
    }

    private void killContainer(ContainerFromList container) {
        String containerId = container.getId();
        try {
            if (container.getStatus().equals("running")) {
                dockerConnector.killContainer(containerId);
            }
        } catch (IOException e) {
            LOG.error("Failed to kill unused container {}", containerId);
        }
    }

    private void removeContainerByID(String containerId) {
        try {
            //remove force with volumes
            dockerConnector.removeContainer(containerId, true, true);
        } catch (IOException e) {
            LOG.error("Failed to delete unused container {}", containerId);
        }
    }
}
