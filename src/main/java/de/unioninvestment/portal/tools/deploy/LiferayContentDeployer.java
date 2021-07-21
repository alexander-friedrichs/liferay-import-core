package de.unioninvestment.portal.tools.deploy;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;

import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployer;
import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.PrincipalThreadLocal;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.security.permission.PermissionThreadLocal;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;

import de.unioninvestment.portal.liferay.addon.LiferayBaselineImporter;
import de.unioninvestment.portal.liferay.addon.LiferayContentImporter;
import de.unioninvestment.portal.liferay.addon.LiferayUserRoleImporter;
import de.unioninvestment.portal.liferay.addon.base.jaxb.LiferayBaselineDefinition;
import de.unioninvestment.portal.liferay.addon.content.jaxb.LiferayContentDescriptor;
import de.unioninvestment.portal.liferay.addon.userrole.jaxb.LiferayUserRoleDefinition;

public class LiferayContentDeployer implements AutoDeployer {

	private static Log logger = LogFactoryUtil.getLog(LiferayContentDeployer.class);

	public static final  String USERROLEROOT = "liferayUserRoleDefinition";
	public static final String LIFERAYCONTENTROOT = "LiferayContentDescriptor";
	public static final String LIFERAYBASEDEFINITIONROOOT = "liferayBaselineDefinition";

	@Override
	public int autoDeploy(AutoDeploymentContext adCtx)
			throws AutoDeployException {
		registerPermissionChecker();

		LiferayContentDescriptor contentDescriptor = null;
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(adCtx.getFile());
			byte[] fin = IOUtils.toByteArray(fis);
			ByteArrayInputStream input = new ByteArrayInputStream(fin);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(input);
			doc.getDocumentElement().normalize();
			String rootName = doc.getDocumentElement().getNodeName();

			ByteArrayInputStream inputSource = new ByteArrayInputStream(fin);
			Source source = new StreamSource(inputSource);
			if (rootName.equalsIgnoreCase(LIFERAYCONTENTROOT)) {
				logger.info("Import LiferayContentDescriptor");
				JAXBContext jaxbContext = JAXBContext.newInstance(LiferayContentDescriptor.class);
				Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
				contentDescriptor = (LiferayContentDescriptor) jaxbUnmarshaller.unmarshal(source);
				LiferayContentImporter imp = new LiferayContentImporter();
				imp.importLiferayContent(contentDescriptor);
			} else if (rootName.equalsIgnoreCase(USERROLEROOT)) {
				logger.info("Import liferayUserRoleDefinition");
				JAXBContext jaxbContext = JAXBContext.newInstance(LiferayUserRoleDefinition.class);
				Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
				LiferayUserRoleDefinition lurd = (LiferayUserRoleDefinition) jaxbUnmarshaller.unmarshal(source);
				LiferayUserRoleImporter imp = new LiferayUserRoleImporter();
				imp.importUserRole(lurd, adCtx.getFile().getName());

			} else if (rootName.equalsIgnoreCase(LIFERAYBASEDEFINITIONROOOT)) {
				logger.info("Import liferayBaselineDefinition");
				JAXBContext jaxbContext = JAXBContext.newInstance(LiferayBaselineDefinition.class);
				Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
				LiferayBaselineDefinition lbDef = (LiferayBaselineDefinition) jaxbUnmarshaller.unmarshal(source);
				LiferayBaselineImporter imp = new LiferayBaselineImporter();
				imp.importBaseline(lbDef);
			} else{
				logger.error("Invalid lcd Root");
			}
		} catch (Exception e) {
			logger.error(e.toString());
			return 0;
		}
		finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					logger.error(e.toString());
				}
			}
		}

		return 1;
	}

    public void registerPermissionChecker() {
		try {
			Company companyqq = CompanyLocalServiceUtil.getCompanyByWebId("union-investment.de");
			Role adminRole = RoleLocalServiceUtil.getRole(companyqq.getCompanyId(), "Administrator");
			List<User> adminUsers = UserLocalServiceUtil.getRoleUsers(adminRole.getRoleId());
			PrincipalThreadLocal.setName(adminUsers.get(0).getUserId());
			@SuppressWarnings("deprecation")
			PermissionChecker permissionChecker = PermissionCheckerFactoryUtil.create(adminUsers.get(0), true);
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		} catch (PortalException e) {
			logger.error(e.toString());
		} catch (SystemException e) {
			logger.error(e.toString());
		} catch (Exception e) {
			logger.error(e.toString());
		}
	}

	@Override
	public AutoDeployer cloneAutoDeployer() throws AutoDeployException {
		return new LiferayContentDeployer();
	}

    public String getCrudPortletDeployedFilePath() {
        return "/opt/osiris/sofia/nodes/liferay-portal-6.2-ce-ga2-node0/jboss-7.1.1/standalone/deployments/eai-administration.war.deployed";
    }

}
