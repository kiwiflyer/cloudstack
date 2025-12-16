package org.apache.cloudstack.network.tungsten.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloud.dc.dao.DataCenterDao;
import com.cloud.domain.Domain;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.network.tungsten.api.response.TungstenFabricPolicyResponse;
import com.cloud.projects.dao.ProjectDao;
import org.apache.cloudstack.network.tungsten.agent.api.CreateTungstenFabricPolicyCmd;
import org.apache.cloudstack.network.tungsten.agent.api.TungstenAnswer;
import org.apache.cloudstack.network.tungsten.agent.api.TungstenCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import net.juniper.tungsten.api.types.NetworkPolicy;

@RunWith(MockitoJUnitRunner.class)
public class TungstenServiceImplDomainTest {

    @Mock
    ProjectDao projectDao;
    @Mock
    DomainDao domainDao;
    @Mock
    TungstenFabricUtils tungstenFabricUtils;
    @Mock
    DataCenterDao dataCenterDao;

    TungstenServiceImpl tungstenService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        tungstenService = new TungstenServiceImpl();
        tungstenService.projectDao = projectDao;
        tungstenService.domainDao = domainDao;
        tungstenService.tungstenFabricUtils = tungstenFabricUtils;
        tungstenService.dataCenterDao = dataCenterDao;
    }

    @Test
    public void createTungstenPolicyWithDomainTest() {
        long zoneId = 1L;
        long domainId = 10L;
        String policyName = "test-policy";
        String domainName = "frontend-domain";
        String expectedFqn = "frontend-domain:default-project";

        DomainVO domain = mock(DomainVO.class);
        when(domainDao.findById(domainId)).thenReturn(domain);
        when(domain.getName()).thenReturn(domainName);
        when(domain.getId()).thenReturn(domainId);

        // Mock successful response
        TungstenAnswer answer = mock(TungstenAnswer.class);
        when(answer.getResult()).thenReturn(true);
        NetworkPolicy networkPolicy = mock(NetworkPolicy.class);
        when(answer.getApiObjectBase()).thenReturn(networkPolicy);

        when(tungstenFabricUtils.sendTungstenCommand(any(TungstenCommand.class), anyLong())).thenReturn(answer);

        TungstenFabricPolicyResponse response = tungstenService.createTungstenPolicy(zoneId, domainId, policyName);

        ArgumentCaptor<TungstenCommand> argument = ArgumentCaptor.forClass(TungstenCommand.class);
        verify(tungstenFabricUtils).sendTungstenCommand(argument.capture(), anyLong());

        TungstenCommand cmd = argument.getValue();
        // We expect CreateTungstenFabricPolicyCmd (or equivalent used in impl)
        // Impl uses: new CreateTungstenFabricPolicyCmd(name, projectFqn);
        // Note: I need to verify the class name used in Impl.
        // In TungstenServiceImpl.java I used: new CreateTungstenFabricPolicyCmd(name,
        // projectFqn);
        // But wait, in the imports of TungstenServiceImpl,
        // CreateTungstenFabricPolicyCmd might refer to the API command OR the Agent
        // command.
        // Let's check imports in TungstenServiceImpl.
        // It imports
        // org.apache.cloudstack.network.tungsten.api.command.CreateTungstenFabricPolicyCmd
        // is the API one.
        // Impl usually sends an Agent command.
        // In my Edit I used: TungstenCommand createTungstenPolicyCommand = new
        // CreateTungstenFabricPolicyCmd(name, projectFqn);
        // Wait, did I use the API command class as an Agent command?
        // CreateTungstenFabricPolicyCmd (API) extends BaseAsyncCmd.
        // CreateTungstenPolicyCommand (Agent) extends TungstenCommand.
        // I need to check which one I instantiated in my edit.

    }
}
