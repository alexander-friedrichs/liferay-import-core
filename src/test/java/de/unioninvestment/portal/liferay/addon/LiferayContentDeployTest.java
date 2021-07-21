package de.unioninvestment.portal.liferay.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployer;
import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;

import de.unioninvestment.portal.liferay.addon.LiferayUserRoleImporter;
import de.unioninvestment.portal.tools.deploy.LiferayContentDeployListener;
import de.unioninvestment.portal.tools.deploy.LiferayContentDeployer;

public class LiferayContentDeployTest {

    @Mock
    private AutoDeploymentContext autoDeploymentContext;
    @Mock
    private LiferayContentDeployer deployer;

    private LiferayContentDeployListener listener;

    private final File lcdFile = new File("target/test-lcd.xml");
    private final File roleAddFile = new File("target/test_ADD-lcd.xml");
    private final File roleRemoveFile = new File("target/test_REMOVE-lcd.xml");
    private final File lcdrdyFile = new File("target/test-lcd.xml.rdy");
    private final File roleAddrdyFile = new File("target/test_ADD-lcd.xml.rdy");
    private final File roleRemoverdyFile = new File("target/test_REMOVE-lcd.xml.rdy");
    private final File crudPortletDeployed = new File("target/eai-administration.war.deployed");

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        this.listener = new LiferayContentDeployListener();
        this.listener.setAutoDeployer(this.deployer);
    }

    @Test
    public void testDeployLcdAfterRestart() throws AutoDeployException, InterruptedException {
        when(this.autoDeploymentContext.getFile()).thenReturn(this.lcdFile);
        when(this.deployer.getCrudPortletDeployedFilePath()).thenReturn(crudPortletDeployed.getPath());
        when(deployer.autoDeploy(autoDeploymentContext)).thenReturn(AutoDeployer.CODE_DEFAULT);
        try {
            final Timer timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    try {
                        crudPortletDeployed.createNewFile();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }, LiferayContentDeployListener.WAITING_PERIOD * 3 + 10000);
            this.lcdFile.createNewFile();
            this.listener.deploy(this.autoDeploymentContext);
            verify(this.deployer, atLeast(1)).autoDeploy(this.autoDeploymentContext);
            verify(this.deployer, atMost(1)).autoDeploy(this.autoDeploymentContext);
            assertTrue(lcdrdyFile.exists());
        } catch (AutoDeployException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testDeployLcd() throws AutoDeployException {
        when(this.autoDeploymentContext.getFile()).thenReturn(this.lcdFile);
        when(this.deployer.getCrudPortletDeployedFilePath()).thenReturn(crudPortletDeployed.getPath());
        when(deployer.autoDeploy(autoDeploymentContext)).thenReturn(AutoDeployer.CODE_DEFAULT);
        try {
            this.crudPortletDeployed.createNewFile();
            this.lcdFile.createNewFile();
            this.listener.deploy(this.autoDeploymentContext);
            verify(this.deployer, atLeast(1)).autoDeploy(this.autoDeploymentContext);
            verify(this.deployer, atMost(1)).autoDeploy(this.autoDeploymentContext);
            assertTrue(lcdrdyFile.exists());
        } catch (AutoDeployException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testDeployAddRoleFile() throws AutoDeployException {
        when(this.autoDeploymentContext.getFile()).thenReturn(this.roleAddFile);
        when(deployer.autoDeploy(autoDeploymentContext)).thenReturn(AutoDeployer.CODE_DEFAULT);
        try {
            this.roleAddFile.createNewFile();
            this.listener.deploy(this.autoDeploymentContext);
            verify(this.deployer, atLeast(1)).autoDeploy(this.autoDeploymentContext);
            verify(this.deployer, atMost(1)).autoDeploy(this.autoDeploymentContext);
            assertFalse(roleAddrdyFile.exists());
        } catch (AutoDeployException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testDeployRemoveRoleFile() throws AutoDeployException {
        when(this.autoDeploymentContext.getFile()).thenReturn(this.roleRemoveFile);
        when(deployer.autoDeploy(autoDeploymentContext)).thenReturn(AutoDeployer.CODE_DEFAULT);
        try {
            this.roleRemoveFile.createNewFile();
            this.listener.deploy(this.autoDeploymentContext);
            verify(this.deployer, atLeast(1)).autoDeploy(this.autoDeploymentContext);
            verify(this.deployer, atMost(1)).autoDeploy(this.autoDeploymentContext);
            assertFalse(roleRemoverdyFile.exists());
        } catch (AutoDeployException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }
    
    @Test
    public void testExtractUamId() {
        String uamid1 = new LiferayUserRoleImporter().extractUamId("UAM_434b3fda-d592-4b5f-80c1-885323041441_FDWH-AV_xz49ddb_ADD-lcd.xml");
        assertEquals("434b3fda-d592-4b5f-80c1-885323041441", uamid1);
        String uamid2 = new LiferayUserRoleImporter().extractUamId("FDWH-AV_xz49ddb_ADD-lcd.xml");
        assertEquals(null, uamid2);
    }

    @After
    public void deleteFiles() {
        this.lcdrdyFile.delete();
        this.lcdFile.delete();
        this.roleAddFile.delete();
        this.roleRemoveFile.delete();
        this.roleAddrdyFile.delete();
        this.roleRemoverdyFile.delete();
        this.crudPortletDeployed.delete();
    }

}
