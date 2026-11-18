// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.network.tungsten.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloud.agent.AgentManager;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.IpAddressManager;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkProfile;
import com.cloud.network.Networks;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkDetailVO;
import com.cloud.network.dao.NetworkDetailsDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.dao.TungstenProviderDao;
import com.cloud.network.element.TungstenProviderVO;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.offering.NetworkOffering;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.offerings.dao.NetworkOfferingServiceMapDao;
import com.cloud.user.Account;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.TungstenUtils;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.Nic;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import net.juniper.tungsten.api.types.LogicalRouter;
import org.apache.cloudstack.network.tungsten.model.TungstenRule;
import org.apache.cloudstack.network.tungsten.agent.api.ClearTungstenNetworkGatewayCommand;
import org.apache.cloudstack.network.tungsten.agent.api.CreateTungstenLogicalRouterCommand;
import org.apache.cloudstack.network.tungsten.agent.api.CreateTungstenNetworkCommand;
import org.apache.cloudstack.network.tungsten.agent.api.CreateTungstenVirtualMachineCommand;
import org.apache.cloudstack.network.tungsten.agent.api.DeleteTungstenFloatingIpCommand;
import org.apache.cloudstack.network.tungsten.agent.api.DeleteTungstenNetworkCommand;
import org.apache.cloudstack.network.tungsten.agent.api.DeleteTungstenNetworkPolicyCommand;
import org.apache.cloudstack.network.tungsten.agent.api.DeleteTungstenVRouterPortCommand;
import org.apache.cloudstack.network.tungsten.agent.api.DeleteTungstenVmCommand;
import org.apache.cloudstack.network.tungsten.agent.api.DeleteTungstenVmInterfaceCommand;
import org.apache.cloudstack.network.tungsten.agent.api.GetTungstenNatIpCommand;
import org.apache.cloudstack.network.tungsten.agent.api.SetTungstenNetworkGatewayCommand;
import org.apache.cloudstack.network.tungsten.agent.api.SetupTungstenVRouterCommand;
import org.apache.cloudstack.network.tungsten.agent.api.TungstenAnswer;
import org.apache.cloudstack.network.tungsten.agent.api.TungstenCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class TungstenGuestNetworkGuruTest {

    @Mock
    NetworkOfferingServiceMapDao ntwkOfferingSrvcDao;
    @Mock
    NetworkOfferingDao networkOfferingDao;
    @Mock
    DataCenterVO dc;
    @Mock
    PhysicalNetworkDao physicalNetworkDao;
    @Mock
    NetworkOffering offering;
    @Mock
    DataCenterDao dcDao;
    @Mock
    DeploymentPlan plan;
    @Mock
    PhysicalNetworkVO physicalNetwork;
    @Mock
    NetworkModel networkModel;
    @Mock
    TungstenFabricUtils tungstenFabricUtils;
    @Mock
    TungstenService tungstenService;
    @Mock
    AccountDao accountDao;
    @Mock
    IpAddressManager ipAddressManager;
    @Mock
    IPAddressDao ipAddressDao;
    @Mock
    VlanDao vlanDao;
    @Mock
    VMInstanceDao vmInstanceDao;
    @Mock
    HostDao hostDao;
    @Mock
    NetworkDao networkDao;
    @Mock
    FirewallRulesDao firewallRulesDao;
    @Mock
    NetworkDetailsDao networkDetailsDao;
    @Mock
    TungstenProviderDao tungstenProviderDao;
    @Mock
    NicDao nicDao;
    @Mock
    AgentManager agentMgr;

    TungstenGuestNetworkGuru guru;

    AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        guru = new TungstenGuestNetworkGuru();
        ReflectionTestUtils.setField(guru, "_physicalNetworkDao", physicalNetworkDao);
        ReflectionTestUtils.setField(guru, "_dcDao", dcDao);
        ReflectionTestUtils.setField(guru, "_networkModel", networkModel);
        ReflectionTestUtils.setField(guru, "_nicDao", nicDao);
        ReflectionTestUtils.setField(guru, "networkOfferingDao", networkOfferingDao);
        guru.networkOfferingServiceMapDao = ntwkOfferingSrvcDao;
        guru.tungstenFabricUtils = tungstenFabricUtils;
        guru.tungstenService = tungstenService;
        guru.accountDao = accountDao;
        guru.ipAddressManager = ipAddressManager;
        guru.ipAddressDao = ipAddressDao;
        guru.vlanDao = vlanDao;
        guru.vmInstanceDao = vmInstanceDao;
        guru.hostDao = hostDao;
        guru.networkDao = networkDao;
        guru.firewallRulesDao = firewallRulesDao;
        guru.networkDetailsDao = networkDetailsDao;
        guru.tungstenProviderDao = tungstenProviderDao;
        guru.agentMgr = agentMgr;

        when(dc.getNetworkType()).thenReturn(DataCenter.NetworkType.Advanced);
        when(dc.getGuestNetworkCidr()).thenReturn("10.1.1.1/24");
        when(dcDao.findById(anyLong())).thenReturn(dc);

        when(physicalNetwork.getIsolationMethods()).thenReturn(List.of("TF"));
        when(physicalNetworkDao.findById(anyLong())).thenReturn(physicalNetwork);

        when(offering.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(offering.getGuestType()).thenReturn(Network.GuestType.Isolated);
        when(offering.getId()).thenReturn(1L);

        when(ntwkOfferingSrvcDao.isProviderForNetworkOffering(offering.getId(), Network.Provider.Tungsten)).thenReturn(
            true);

        when(plan.getDataCenterId()).thenReturn(1L);
        when(plan.getPhysicalNetworkId()).thenReturn(1L);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testIsMyIsolationMethod() {
        assertTrue(guru.isMyIsolationMethod(physicalNetwork));
    }

    @Test
    public void testIsolationMethods() {
        PhysicalNetwork.IsolationMethod[] expected = new PhysicalNetwork.IsolationMethod[] {
            new PhysicalNetwork.IsolationMethod("TF")};
        assertEquals(expected, guru.getIsolationMethods());
    }

    @Test
    public void testTrafficTypes() {
        assertTrue(guru.isMyTrafficType(Networks.TrafficType.Guest));
    }

    @Test
    public void testCanHandle() {
        assertTrue(guru.canHandle(offering, dc.getNetworkType(), physicalNetwork));
    }

    @Test
    public void testCanDesign() {
        List<Network.Service> networkOfferingServiceList = new ArrayList<>(
            Arrays.asList(Network.Service.Connectivity, Network.Service.Dns, Network.Service.Dhcp,
                Network.Service.SourceNat, Network.Service.StaticNat, Network.Service.UserData));
        when(networkModel.listNetworkOfferingServices(anyLong())).thenReturn(networkOfferingServiceList);

        final Network network = mock(Network.class);
        final Account account = mock(Account.class);

        final Network designedNetwork = guru.design(offering, plan, network, "", 1L, account);
        assertNotNull(designedNetwork);
        assertSame(Networks.BroadcastDomainType.TUNGSTEN, designedNetwork.getBroadcastDomainType());
        assertSame(Network.State.Allocated, designedNetwork.getState());
    }

    @Test
    public void testDeallocate() {
        final Network network = mock(Network.class);
        final NicProfile nicProfile = mock(NicProfile.class);
        final VirtualMachineProfile virtualMachineProfile = mock(VirtualMachineProfile.class);
        final NicVO nicVO = mock(NicVO.class);

        when(network.getDataCenterId()).thenReturn(1L);
        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(virtualMachineProfile.getType()).thenReturn(VirtualMachine.Type.User);
        when(nicDao.listByVmId(anyLong())).thenReturn(List.of(nicVO));

        guru.deallocate(network, nicProfile, virtualMachineProfile);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenVmCommand.class), anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenVmInterfaceCommand.class), anyLong());
    }

    @Test(expected = CloudRuntimeException.class)
    public void testDeallocateWithException() {
        final Network network = mock(Network.class);
        final NicProfile nicProfile = mock(NicProfile.class);
        final VirtualMachineProfile virtualMachineProfile = mock(VirtualMachineProfile.class);
        final NicVO nicVO = mock(NicVO.class);

        when(tungstenFabricUtils.sendTungstenCommand(any(DeleteTungstenVmCommand.class), anyLong())).thenThrow(
            new IllegalArgumentException());
        when(nicDao.listByVmId(anyLong())).thenReturn(List.of(nicVO));
        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(virtualMachineProfile.getType()).thenReturn(VirtualMachine.Type.User);

        guru.deallocate(network, nicProfile, virtualMachineProfile);
    }

    @Test
    public void testImplementGuestNetwork() {
        final Network network = mock(Network.class);
        final DeployDestination deployDestination = mock(DeployDestination.class);
        final ReservationContext reservationContext = mock(ReservationContext.class);
        final IPAddressVO ipAddressVO = mock(IPAddressVO.class);

        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(network.getMode()).thenReturn(Networks.Mode.Dhcp);
        when(network.getGateway()).thenReturn("192.168.1.1");
        when(network.getCidr()).thenReturn("192.168.1.0/24");
        when(network.getBroadcastDomainType()).thenReturn(Networks.BroadcastDomainType.TUNGSTEN);
        when(network.getNetworkOfferingId()).thenReturn(1L);
        when(network.getState()).thenReturn(Network.State.Implementing);
        when(network.getDataCenterId()).thenReturn(2L);
        when(network.getPhysicalNetworkId()).thenReturn(3L);
        when(offering.isRedundantRouter()).thenReturn(false);
        when(offering.getGuestType()).thenReturn(Network.GuestType.Isolated);
        when(tungstenService.getTungstenProjectFqn(network)).thenReturn("default-domain:default-project");
        when(networkModel.getSystemNetworkByZoneAndTrafficType(anyLong(), any())).thenReturn(new NetworkVO());
        when(tungstenFabricUtils.sendTungstenCommand(any(CreateTungstenNetworkCommand.class), anyLong())).thenReturn(
            new TungstenAnswer(new TungstenCommand(), true, ""));
        when(tungstenFabricUtils.sendTungstenCommand(any(CreateTungstenLogicalRouterCommand.class),
            anyLong())).thenReturn(new TungstenAnswer(new TungstenCommand(), new LogicalRouter(), true, ""));
        when(tungstenFabricUtils.sendTungstenCommand(any(GetTungstenNatIpCommand.class), anyLong())).thenReturn(
            new TungstenAnswer(new TungstenCommand(), true, "192.168.1.100"));
        when(ipAddressDao.findByIpAndDcId(anyLong(), anyString())).thenReturn(ipAddressVO);
        when(tungstenService.allocateDnsIpAddress(any(Network.class), any(), anyString())).thenReturn(true);

        final Network implemented = guru.implement(network, offering, deployDestination, reservationContext);
        assertEquals(Networks.BroadcastDomainType.TUNGSTEN.toUri("tf"), implemented.getBroadcastUri());
        assertEquals("192.168.1.1", implemented.getGateway());
        assertEquals("192.168.1.0/24", implemented.getCidr());
        assertEquals(Networks.Mode.Dhcp, implemented.getMode());
        assertEquals(Networks.BroadcastDomainType.TUNGSTEN, implemented.getBroadcastDomainType());
        assertEquals(1L, implemented.getNetworkOfferingId());
        assertEquals(Network.State.Implemented, implemented.getState());
        assertEquals(2L, implemented.getDataCenterId());
        assertEquals(3L, implemented.getPhysicalNetworkId().longValue());
        assertFalse(implemented.isRedundant());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(CreateTungstenNetworkCommand.class), anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(CreateTungstenLogicalRouterCommand.class),
            anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(GetTungstenNatIpCommand.class), anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(SetTungstenNetworkGatewayCommand.class),
            anyLong());
    }

    @Test
    public void testImplementSharedNetwork() {
        final Network network = mock(Network.class);
        final DeployDestination deployDestination = mock(DeployDestination.class);
        final ReservationContext reservationContext = mock(ReservationContext.class);

        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(network.getMode()).thenReturn(Networks.Mode.Dhcp);
        when(network.getGateway()).thenReturn("192.168.1.1");
        when(network.getCidr()).thenReturn("192.168.1.0/24");
        when(network.getBroadcastDomainType()).thenReturn(Networks.BroadcastDomainType.TUNGSTEN);
        when(network.getNetworkOfferingId()).thenReturn(1L);
        when(network.getState()).thenReturn(Network.State.Implementing);
        when(network.getDataCenterId()).thenReturn(2L);
        when(network.getPhysicalNetworkId()).thenReturn(3L);
        when(offering.isRedundantRouter()).thenReturn(false);
        when(offering.getGuestType()).thenReturn(Network.GuestType.Shared);
        when(vlanDao.listVlansByNetworkId(anyLong())).thenReturn(List.of(new VlanVO()));
        when(tungstenService.createSharedNetwork(any(Network.class), any(VlanVO.class))).thenReturn(true);

        final Network implemented = guru.implement(network, offering, deployDestination, reservationContext);
        assertEquals(Networks.BroadcastDomainType.TUNGSTEN.toUri("tf"), implemented.getBroadcastUri());
        assertEquals("192.168.1.1", implemented.getGateway());
        assertEquals("192.168.1.0/24", implemented.getCidr());
        assertEquals(Networks.Mode.Dhcp, implemented.getMode());
        assertEquals(Networks.BroadcastDomainType.TUNGSTEN, implemented.getBroadcastDomainType());
        assertEquals(1L, implemented.getNetworkOfferingId());
        assertEquals(Network.State.Implemented, implemented.getState());
        assertEquals(2L, implemented.getDataCenterId());
        assertEquals(3L, implemented.getPhysicalNetworkId().longValue());
        assertFalse(implemented.isRedundant());
    }

    @Test(expected = CloudRuntimeException.class)
    public void testImplementWithException() {
        final Network network = mock(Network.class);
        final DeployDestination deployDestination = mock(DeployDestination.class);
        final ReservationContext reservationContext = mock(ReservationContext.class);

        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(network.getMode()).thenReturn(Networks.Mode.Dhcp);
        when(network.getGateway()).thenReturn("192.168.1.1");
        when(network.getCidr()).thenReturn("192.168.1.0/24");
        when(network.getBroadcastDomainType()).thenReturn(Networks.BroadcastDomainType.TUNGSTEN);
        when(network.getState()).thenReturn(Network.State.Implementing);
        when(offering.isRedundantRouter()).thenReturn(false);
        when(offering.getGuestType()).thenReturn(Network.GuestType.Isolated);
        when(tungstenService.getTungstenProjectFqn(any(Network.class))).thenReturn("default-domain:default-project");
        when(networkModel.getSystemNetworkByZoneAndTrafficType(anyLong(), any())).thenReturn(new NetworkVO());
        when(tungstenFabricUtils.sendTungstenCommand(any(CreateTungstenNetworkCommand.class), anyLong())).thenReturn(
            new TungstenAnswer(new TungstenCommand(), new IOException()));

        guru.implement(network, offering, deployDestination, reservationContext);
    }

    @Test
    public void testReserve() throws InsufficientVirtualNetworkCapacityException, InsufficientAddressCapacityException {
        final NicProfile nic = mock(NicProfile.class);
        final Network network = mock(Network.class);
        final VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
        final DeployDestination dest = mock(DeployDestination.class);
        final ReservationContext context = mock(ReservationContext.class);
        final VMInstanceVO vmInstanceVO = mock(VMInstanceVO.class);
        final HostVO host = mock(HostVO.class);

        when(nic.getReservationStrategy()).thenReturn(Nic.ReservationStrategy.Start);
        when(vm.getType()).thenReturn(VirtualMachine.Type.User);
        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(vmInstanceDao.findById(anyLong())).thenReturn(vmInstanceVO);
        when(hostDao.findById(anyLong())).thenReturn(host);

        guru.reserve(nic, network, vm, dest, context);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(CreateTungstenVirtualMachineCommand.class),
            anyLong());
    }

    @Test
    public void testRelease() {
        final NicProfile nic = mock(NicProfile.class);
        final VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
        final IPAddressVO ipAddressVO = mock(IPAddressVO.class);
        final VMInstanceVO vmInstanceVO = mock(VMInstanceVO.class);
        final HostVO host = mock(HostVO.class);

        when(hostDao.findById(anyLong())).thenReturn(host);
        when(
            tungstenFabricUtils.sendTungstenCommand(any(DeleteTungstenVRouterPortCommand.class), anyLong())).thenReturn(
            new TungstenAnswer(new TungstenCommand(), true, ""));
        when(vmInstanceDao.findById(anyLong())).thenReturn(vmInstanceVO);

        guru.release(nic, vm, "test");
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenVRouterPortCommand.class),
            anyLong());
    }

    @Test
    public void testShutdown() {
        final NetworkProfile profile = mock(NetworkProfile.class);
        final IPAddressVO ipAddressVO = mock(IPAddressVO.class);
        final FirewallRuleVO firewallRuleVO = mock(FirewallRuleVO.class);

        when(offering.getGuestType()).thenReturn(Network.GuestType.Isolated);
        when(networkModel.getSystemNetworkByZoneAndTrafficType(anyLong(), any())).thenReturn(new NetworkVO());
        when(ipAddressDao.listByAssociatedNetwork(anyLong(), any())).thenReturn(List.of(ipAddressVO));
        when(firewallRulesDao.listByNetworkAndPurpose(anyLong(), any())).thenReturn(List.of(firewallRuleVO));

        guru.shutdown(profile, offering);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenFloatingIpCommand.class),
            anyLong());
        verify(tungstenFabricUtils, times(2)).sendTungstenCommand(any(DeleteTungstenNetworkPolicyCommand.class),
            anyLong());
    }

    @Test
    public void testTrash() {
        final Network network = mock(Network.class);

        guru.trash(network, offering);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenNetworkPolicyCommand.class),
            anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(ClearTungstenNetworkGatewayCommand.class),
            anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenNetworkCommand.class), anyLong());
        verify(tungstenService, times(1)).deallocateDnsIpAddress(any(), any(), anyString());
    }

    @Test
    public void testTrashSharedNetwork() {
        final Network network = mock(Network.class);
        final NetworkDetailVO networkDetailVO = mock(NetworkDetailVO.class);
        final TungstenProviderVO tungstenProviderVO = mock(TungstenProviderVO.class);
        final HostVO hostVO = mock(HostVO.class);

        when(network.getGuestType()).thenReturn(Network.GuestType.Shared);
        when(networkDetailsDao.findDetail(anyLong(), anyString())).thenReturn(networkDetailVO);
        when(tungstenProviderDao.findByZoneId(anyLong())).thenReturn(tungstenProviderVO);
        when(hostDao.findByPublicIp(any())).thenReturn(hostVO);

        guru.trash(network, offering);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenNetworkPolicyCommand.class),
            anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(ClearTungstenNetworkGatewayCommand.class),
            anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenNetworkCommand.class), anyLong());
        verify(tungstenService, times(1)).deallocateDnsIpAddress(any(), any(), anyString());
        verify(agentMgr, times(1)).easySend(any(), any(SetupTungstenVRouterCommand.class));
        verify(networkDetailsDao, times(1)).expunge(anyLong());
    }

    @Test
    public void testPrepareMigration() {
        final NicProfile nic = mock(NicProfile.class);
        final Network network = mock(Network.class);
        final VirtualMachineProfile vm = mock(VirtualMachineProfile.class);
        final DeployDestination dest = mock(DeployDestination.class);
        final ReservationContext context = mock(ReservationContext.class);
        final VMInstanceVO vmInstanceVO = mock(VMInstanceVO.class);
        final HostVO hostVO = mock(HostVO.class);

        when(vm.getType()).thenReturn(VirtualMachine.Type.User);
        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(vmInstanceDao.findById(anyLong())).thenReturn(vmInstanceVO);
        when(hostDao.findById(anyLong())).thenReturn(hostVO);
        when(tungstenFabricUtils.sendTungstenCommand(any(CreateTungstenVirtualMachineCommand.class),
            anyLong())).thenReturn(new TungstenAnswer(new TungstenCommand(), true, ""));

        guru.prepareMigration(nic, network, vm, dest, context);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(CreateTungstenVirtualMachineCommand.class),
            anyLong());
    }

    @Test
    public void testRollbackMigration() {
        final NicProfile nic = mock(NicProfile.class);
        final Network network = mock(Network.class);
        final VirtualMachineProfile vmProfile = mock(VirtualMachineProfile.class);
        final ReservationContext context = mock(ReservationContext.class);
        final VirtualMachine vm = mock(VirtualMachine.class);
        final HostVO hostVO = mock(HostVO.class);

        when(vmProfile.getType()).thenReturn(VirtualMachine.Type.User);
        when(vmProfile.getVirtualMachine()).thenReturn(vm);
        when(hostDao.findById(anyLong())).thenReturn(hostVO);

        guru.rollbackMigration(nic, network, vmProfile, context, context);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenVRouterPortCommand.class),
            anyLong());
    }

    @Test
    public void testCommitMigration() {
        final NicProfile nic = mock(NicProfile.class);
        final Network network = mock(Network.class);
        final VirtualMachineProfile vmProfile = mock(VirtualMachineProfile.class);
        final ReservationContext context = mock(ReservationContext.class);
        final VirtualMachine vm = mock(VirtualMachine.class);
        final HostVO hostVO = mock(HostVO.class);

        when(vmProfile.getType()).thenReturn(VirtualMachine.Type.User);
        when(vmProfile.getVirtualMachine()).thenReturn(vm);
        when(hostDao.findById(anyLong())).thenReturn(hostVO);

        guru.commitMigration(nic, network, vmProfile, context, context);
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(DeleteTungstenVRouterPortCommand.class),
            anyLong());
    }

    /**
     * The generated default policy's egress rule is the one whose destination
     * prefix is 0.0.0.0/0. The destination network is "any" on every rule, so
     * the prefix is what identifies it.
     */
    private TungstenRule egressRuleOf(List<TungstenRule> rules) {
        return rules.stream()
            .filter(r -> TungstenUtils.ALL_IP4_PREFIX.equals(r.getDstIpPrefix()) && r.getDstIpPrefixLen() == 0)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no egress rule to 0.0.0.0/0 was generated"));
    }

    private NetworkVO guestNetworkWithOffering(boolean egressDefaultAllow) {
        NetworkVO network = new NetworkVO();
        network.setCidr("10.1.1.0/24");
        ReflectionTestUtils.setField(network, "networkOfferingId", 1L);

        NetworkOfferingVO networkOffering = mock(NetworkOfferingVO.class);
        when(networkOffering.isEgressDefaultPolicy()).thenReturn(egressDefaultAllow);
        when(networkOfferingDao.findById(1L)).thenReturn(networkOffering);

        return network;
    }

    @Test
    public void testDefaultPolicyDeniesEgressWhenOfferingDefaultsToDeny() {
        List<TungstenRule> rules = guru.getDefaultGuestNetworkPolicyRule(guestNetworkWithOffering(false));

        assertEquals("a deny-egress offering must produce a deny rule",
            TungstenUtils.DENY_ACTION, egressRuleOf(rules).getAction());
    }

    @Test
    public void testDefaultPolicyAllowsEgressWhenOfferingDefaultsToAllow() {
        List<TungstenRule> rules = guru.getDefaultGuestNetworkPolicyRule(guestNetworkWithOffering(true));

        assertEquals("an allow-egress offering must not be silently denied (#7967)",
            TungstenUtils.PASS_ACTION, egressRuleOf(rules).getAction());
    }

    @Test
    public void testDefaultPolicyDeniesEgressWhenOfferingCannotBeFound() {
        NetworkVO network = new NetworkVO();
        network.setCidr("10.1.1.0/24");
        ReflectionTestUtils.setField(network, "networkOfferingId", 99L);
        when(networkOfferingDao.findById(99L)).thenReturn(null);

        List<TungstenRule> rules = guru.getDefaultGuestNetworkPolicyRule(network);

        assertEquals("an unresolvable offering must fail closed, not open",
            TungstenUtils.DENY_ACTION, egressRuleOf(rules).getAction());
    }

    @Test
    public void testDefaultPolicyAlwaysPermitsIntraNetworkTraffic() {
        List<TungstenRule> denyEgress = guru.getDefaultGuestNetworkPolicyRule(guestNetworkWithOffering(false));
        List<TungstenRule> allowEgress = guru.getDefaultGuestNetworkPolicyRule(guestNetworkWithOffering(true));

        for (List<TungstenRule> rules : List.of(denyEgress, allowEgress)) {
            TungstenRule intra = rules.stream()
                .filter(r -> "10.1.1.0".equals(r.getDstIpPrefix()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("intra-network rule missing"));
            assertEquals(TungstenUtils.PASS_ACTION, intra.getAction());
            assertEquals(TungstenUtils.TWO_WAY_DIRECTION, intra.getDirection());
        }
    }

    @Test
    public void testFabricSnatRequestedWhenProviderHasNoGateway() {
        // apache/cloudstack#7463: a provider with no gateway vRouter must fall
        // back to IP fabric SNAT so guest networks still reach the outside.
        TungstenProviderVO provider = mock(TungstenProviderVO.class);
        when(provider.getGateway()).thenReturn(null);

        assertTrue("no gateway means fabric SNAT is required",
            guru.shouldUseFabricSnat(provider));
    }

    @Test
    public void testFabricSnatRequestedWhenProviderGatewayIsBlank() {
        TungstenProviderVO provider = mock(TungstenProviderVO.class);
        when(provider.getGateway()).thenReturn("   ");

        assertTrue("a blank gateway is no gateway", guru.shouldUseFabricSnat(provider));
    }

    @Test
    public void testFabricSnatNotRequestedWhenProviderHasAGateway() {
        TungstenProviderVO provider = mock(TungstenProviderVO.class);
        when(provider.getGateway()).thenReturn("192.0.2.1");

        assertFalse("with a gateway vRouter, routing goes through it",
            guru.shouldUseFabricSnat(provider));
    }

    @Test
    public void testFabricSnatRequestedWhenProviderIsMissing() {
        assertTrue("an absent provider must not silently lose connectivity",
            guru.shouldUseFabricSnat(null));
    }

    /** Mirrors testImplementGuestNetwork's setup, minus the NAT IP stubbing. */
    private Network guestNetworkReadyToImplement() {
        final Network network = mock(Network.class);
        when(network.getTrafficType()).thenReturn(Networks.TrafficType.Guest);
        when(network.getMode()).thenReturn(Networks.Mode.Dhcp);
        when(network.getGateway()).thenReturn("192.168.1.1");
        when(network.getCidr()).thenReturn("192.168.1.0/24");
        when(network.getBroadcastDomainType()).thenReturn(Networks.BroadcastDomainType.TUNGSTEN);
        when(network.getNetworkOfferingId()).thenReturn(1L);
        when(network.getState()).thenReturn(Network.State.Implementing);
        when(network.getDataCenterId()).thenReturn(2L);
        when(network.getPhysicalNetworkId()).thenReturn(3L);
        when(offering.isRedundantRouter()).thenReturn(false);
        when(offering.getGuestType()).thenReturn(Network.GuestType.Isolated);
        when(tungstenService.getTungstenProjectFqn(network)).thenReturn("default-domain:default-project");
        when(networkModel.getSystemNetworkByZoneAndTrafficType(anyLong(), any())).thenReturn(new NetworkVO());
        when(tungstenFabricUtils.sendTungstenCommand(any(CreateTungstenNetworkCommand.class), anyLong()))
            .thenReturn(new TungstenAnswer(new TungstenCommand(), true, ""));
        when(tungstenFabricUtils.sendTungstenCommand(any(CreateTungstenLogicalRouterCommand.class), anyLong()))
            .thenReturn(new TungstenAnswer(new TungstenCommand(), new LogicalRouter(), true, ""));
        when(tungstenService.allocateDnsIpAddress(any(Network.class), any(), anyString())).thenReturn(true);
        return network;
    }

    @Test
    public void testImplementFailsLoudlyWhenNatIpCannotBeObtained() {
        // apache/cloudstack#7697: the source NAT lookup had no else branch, so
        // a failure left the network created but with no source NAT IP, no
        // guest policy, no public policy and no gateway VMI - and implement()
        // still reported success. The network must not be reported implemented.
        ReflectionTestUtils.setField(guru, "natIpRetryDelayMs", 0L);
        final Network network = guestNetworkReadyToImplement();
        when(tungstenFabricUtils.sendTungstenCommand(any(GetTungstenNatIpCommand.class), anyLong()))
            .thenReturn(new TungstenAnswer(new TungstenCommand(), false, "service instance not ready"));

        try {
            guru.implement(network, offering, mock(DeployDestination.class), mock(ReservationContext.class));
            fail("implement must not report success when the source NAT IP cannot be obtained");
        } catch (CloudRuntimeException expected) {
            assertNotNull(expected.getMessage());
        }

        // And it must not have gone on to build the rest of the network.
        verify(tungstenFabricUtils, times(0)).sendTungstenCommand(any(SetTungstenNetworkGatewayCommand.class),
            anyLong());
    }

    @Test
    public void testNatIpIsRetriedWhileTheServiceInstanceIsBeingCreated() {
        // The fabric creates the service instance asynchronously after the
        // logical router, so the first read can legitimately come back empty.
        ReflectionTestUtils.setField(guru, "natIpRetryDelayMs", 0L);
        final Network network = guestNetworkReadyToImplement();
        when(tungstenFabricUtils.sendTungstenCommand(any(GetTungstenNatIpCommand.class), anyLong()))
            .thenReturn(new TungstenAnswer(new TungstenCommand(), false, ""))
            .thenReturn(new TungstenAnswer(new TungstenCommand(), false, ""))
            .thenReturn(new TungstenAnswer(new TungstenCommand(), true, "192.168.1.100"));
        when(ipAddressDao.findByIpAndDcId(anyLong(), anyString())).thenReturn(mock(IPAddressVO.class));

        final Network implemented =
            guru.implement(network, offering, mock(DeployDestination.class), mock(ReservationContext.class));

        assertEquals(Network.State.Implemented, implemented.getState());
        verify(tungstenFabricUtils, times(3)).sendTungstenCommand(any(GetTungstenNatIpCommand.class), anyLong());
        verify(tungstenFabricUtils, times(1)).sendTungstenCommand(any(SetTungstenNetworkGatewayCommand.class),
            anyLong());
    }
}
