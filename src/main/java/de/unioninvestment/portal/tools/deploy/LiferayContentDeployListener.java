package de.unioninvestment.portal.tools.deploy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployer;
import com.liferay.portal.kernel.deploy.auto.BaseAutoDeployListener;
import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

public class LiferayContentDeployListener extends BaseAutoDeployListener {

    public static final String VAR_TMP_UAM = "/var/tmp/uam/";

    public static final int WAITING_PERIOD = 20 * 1000;

    private static Log logger = LogFactoryUtil.getLog(LiferayContentDeployListener.class);

    private AutoDeployer autoDeployer;

    public LiferayContentDeployListener() {
        this.autoDeployer = new LiferayContentDeployer();
    }

    @Override
    public int deploy(AutoDeploymentContext autoDeploymentContext) throws AutoDeployException {

        File file = autoDeploymentContext.getFile();

        if (logger.isDebugEnabled()) {
            logger.debug("Invoking deploy for " + file.getPath());
        }

        if (!isLiferayContentPlugin(file)) {
            return 0;
        }

        if (!isLiferayContentPluginRoleSyncFile(file)) {

            if (logger.isInfoEnabled()) {
                logger.info("Copying LiferayLayoutDescriptor for " + file.getPath() + ", waiting 1 minute before Deployment starts.");
            }

            try {
                Thread.sleep(WAITING_PERIOD * 3);
            } catch (InterruptedException e) {
                logger.error("lcd deploy wait interrupted", e);
            }

        } else if (file.getName().startsWith("UAM_")) {
            File retryFile = new File(VAR_TMP_UAM + file.getName());
            try {
                retryFile.getParentFile().mkdirs();
                retryFile.createNewFile();
                IOUtils.copy(new FileInputStream(file), new FileOutputStream(retryFile));
            } catch (IOException e) {
                logger.error("Retry File " + retryFile.getAbsolutePath() + " cannot be created.", e);
            }
        }

        String crudPortletDeployed = ((LiferayContentDeployer) autoDeployer).getCrudPortletDeployedFilePath();

        while (!isLiferayContentPluginRoleSyncFile(file) && !new File(crudPortletDeployed).exists()) {
            try {
                logger.warn("Waiting for CRUD Portlet Deployment, " + crudPortletDeployed + " must exist before LCD Deployment continues.");
                Thread.sleep(WAITING_PERIOD);
            } catch (InterruptedException e) {
                logger.error("Waiting for CRUD Portlet Deployment interrupted.");
            }
        }

        int code = this.autoDeployer.autoDeploy(autoDeploymentContext);

        if (code == AutoDeployer.CODE_DEFAULT) {
            if (!isLiferayContentPluginRoleSyncFile(file)) {
                File rdyFile = new File(file.getAbsolutePath() + ".rdy");
                try {
                    rdyFile.createNewFile();
                } catch (IOException e) {
                    logger.error("Ready File for " + file.getPath() + " cannot be created.", e);
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info("LiferayLayoutDescriptor for " + file.getPath() + " copied successfully");
            }
        }
        return code;
    }

    private boolean isLiferayContentPlugin(File file) {
        return file.getName().endsWith("lcd.xml") ? true : false;
    }

    private boolean isLiferayContentPluginRoleSyncFile(File file) {
        return file.getName().endsWith("_REMOVE-lcd.xml") || file.getName().endsWith("_ADD-lcd.xml") ? true : false;
    }

    public void setAutoDeployer(AutoDeployer autoDeployer) {
        this.autoDeployer = autoDeployer;
    }

}