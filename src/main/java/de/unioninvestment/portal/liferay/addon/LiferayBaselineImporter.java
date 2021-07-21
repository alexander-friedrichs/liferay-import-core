package de.unioninvestment.portal.liferay.addon;

import com.liferay.portal.NoSuchRoleException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Role;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.RoleServiceUtil;

import de.unioninvestment.portal.liferay.addon.base.jaxb.LiferayBaselineDefinition;
import de.unioninvestment.portal.liferay.addon.base.jaxb.Roles;




public class LiferayBaselineImporter {
	
	private static Log _log = LogFactoryUtil.getLog(LiferayBaselineImporter.class);

	public void importBaseline(LiferayBaselineDefinition lbDef) {
		
		try {
			if (lbDef.getRoles() != null){
				Roles roles = lbDef.getRoles();
				Company company = CompanyLocalServiceUtil.getCompanyByWebId(lbDef.getWebId());
				for (String role : roles.getRole()){
					try {
						RoleLocalServiceUtil.getRole(company.getCompanyId(), role);
						_log.info(role + " exist");
					} catch (NoSuchRoleException e) {
                        RoleServiceUtil.addRole(Role.class.getName(), 0, role, null, null, 1, null, null);
				        _log.info(role + " created");
						
					}
				}
			}
		} catch (PortalException e) {
			_log.error("LiferayBaselineImporter error : " + e.toString());
		} catch (SystemException e) {
			_log.error("LiferayBaselineImporter error : " + e.toString());
		}

	}

}
