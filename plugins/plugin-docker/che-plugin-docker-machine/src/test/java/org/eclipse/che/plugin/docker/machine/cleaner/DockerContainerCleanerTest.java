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

import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.api.machine.server.exception.MachineException;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;
import org.eclipse.che.plugin.docker.client.DockerConnector;
import org.eclipse.che.plugin.docker.client.json.ContainerListEntry;
import org.eclipse.che.plugin.docker.machine.DockerContainerNameGenerator;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.eclipse.che.plugin.docker.machine.DockerContainerNameGenerator.ContainerNameInfo;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link DockerContainerCleaner}
 *
 * @author Alexander Andrienko
 */
@Listeners(MockitoTestNGListener.class)
public class DockerContainerCleanerTest {

    private static final String machineId1 = "machineid1";

    private static final String workspaceId1 = "workspaceid1";

    private static final String containerName1 = "containerName1";
    private static final String containerName2 = "containerName2";

    private static final String containerId1 = "containerId1";
    private static final String containerId2 = "containerId2";

    private static final String EXITED_STATUS  = "exited";
    private static final String RUNNING_STATUS = "Up 6 hour ago";

    @Mock
    private MachineManager               machineManager;
    @Mock
    private DockerConnector              dockerConnector;
    @Mock
    private DockerContainerNameGenerator nameGenerator;

    @Mock
    private MachineImpl machineImpl1;

    @Mock
    private ContainerListEntry container1;
    @Mock
    private ContainerListEntry container2;

    @Mock
    private ContainerNameInfo containerNameInfo1;
    @Mock
    private ContainerNameInfo containerNameInfo2;

    @InjectMocks
    private DockerContainerCleaner cleaner;

    @BeforeMethod
    public void setUp() throws MachineException, IOException {
        when(machineManager.getMachines()).thenReturn(singletonList(machineImpl1));
        when(dockerConnector.listContainers()).thenReturn(asList(container1, container2));
        when(machineImpl1.getId()).thenReturn(machineId1);
        when(machineImpl1.getWorkspaceId()).thenReturn(workspaceId1);

        when(container1.getNames()).thenReturn(new String[]{containerName1});
        when(container1.getStatus()).thenReturn(RUNNING_STATUS);
        when(container1.getId()).thenReturn(containerId1);

        when(container2.getNames()).thenReturn(new String[]{containerName2});
        when(container2.getStatus()).thenReturn(RUNNING_STATUS);
        when(container2.getId()).thenReturn(containerId2);

        Optional<ContainerNameInfo> optional1 = Optional.of(containerNameInfo1);
        Optional<ContainerNameInfo> optional2 = Optional.of(containerNameInfo2);
        when(nameGenerator.parse(containerName1)).thenReturn(optional1);
        when(nameGenerator.parse(containerName2)).thenReturn(optional2);

        when(containerNameInfo1.getMachineId()).thenReturn(machineId1);
        when(containerNameInfo1.getWorkspaceId()).thenReturn(workspaceId1);
    }

    @Test
    public void dockerContainerShouldBeKilledAndRemoved() throws MachineException, IOException {
        cleaner.run();

        verify(dockerConnector).listContainers();
        verify(machineManager).getMachines();

        verify(container1).getNames();
        verify(container2).getNames();

        verify(nameGenerator).parse(containerName1);
        verify(nameGenerator).parse(containerName2);

        verify(containerNameInfo1).getMachineId();
        verify(containerNameInfo2).getMachineId();

        verify(container2, times(2)).getId();
        verify(container2).getStatus();
        verify(dockerConnector).killContainer(containerId2);
        verify(dockerConnector).removeContainer(containerId2, true, true);

        verify(dockerConnector, never()).killContainer(containerId1);
        verify(dockerConnector, never()).removeContainer(containerId1, true, true);
    }

    @Test
    public void dockerContainerShouldNotBeKilledButRemoved() throws IOException, MachineException {
        when(container2.getStatus()).thenReturn(EXITED_STATUS);
        cleaner.run();

        verify(dockerConnector, never()).killContainer(containerId2);
        verify(dockerConnector).removeContainer(containerId2, true, true);
    }

    @Test
    public void noneContainerShouldBeRemoved() throws IOException {
        when(nameGenerator.parse(containerName2)).thenReturn(Optional.empty());

        cleaner.run();

        verify(dockerConnector, never()).killContainer(containerId1);
        verify(dockerConnector, never()).removeContainer(containerId1, true, true);

        verify(dockerConnector, never()).killContainer(containerId2);
        verify(dockerConnector, never()).removeContainer(containerId2, true, true);
    }
}
