package de.unioninvestment.portal.liferay.addon;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Role;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.RoleServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;

import de.unioninvestment.portal.liferay.addon.userrole.jaxb.LiferayUserRoleDefinition;
import de.unioninvestment.portal.liferay.addon.userrole.jaxb.Roles;
import de.unioninvestment.portal.liferay.addon.userrole.jaxb.User;
import de.unioninvestment.portal.liferay.addon.userrole.jaxb.Users;
import de.unioninvestment.portal.tools.deploy.LiferayContentDeployListener;

public class LiferayUserRoleImporter {

	private static Log _log = LogFactoryUtil.getLog(LiferayUserRoleImporter.class.getName());

    public void importUserRole(final LiferayUserRoleDefinition lurd, final String filename) {
        String uamid = extractUamId(filename);
        File retryFile = new File(LiferayContentDeployListener.VAR_TMP_UAM + filename);

		try {
			Company company = CompanyLocalServiceUtil.getCompanyByWebId(lurd.getWebId());
			long companyId = company.getCompanyId();
			Users users = lurd.getUsers();
			List<User> usersList = users.getUser();
			for (User user : usersList) {
				com.liferay.portal.model.User u = UserLocalServiceUtil.fetchUserByScreenName(companyId,	user.getScreenName());
				if (u != null) {
					
					Roles roles = user.getRoles();
					List<de.unioninvestment.portal.liferay.addon.userrole.jaxb.Role> strRoles = roles.getRole();
					ArrayList<Long> listToAdd = new ArrayList<Long>();
					ArrayList<Long> listToRemove = new ArrayList<Long>();
					for (de.unioninvestment.portal.liferay.addon.userrole.jaxb.Role strRole : strRoles) {
						String roleName = strRole.getValue();
                        Role r = RoleLocalServiceUtil.fetchRole(company.getCompanyId(), roleName);
						
						if (r == null) {
                            r = RoleServiceUtil.addRole(Role.class.getName(), 0, roleName, null, null, 1, null, null);
                            _log.info(roleName + " created");
						}

						if (r != null) {
							boolean insertRoleLog = false;
							Context initContext = new InitialContext();
							DataSource liferayDs = (DataSource) initContext.lookup("java:/jdbc/liferayDs");
							JdbcTemplate jdbcTemplate = new JdbcTemplate(liferayDs);	
							long cnt = jdbcTemplate.queryForLong("select count(1) from users_roles where userid = ? and roleid = ? ", u.getUserId(), r.getRoleId());
							if (strRole.getAction().equalsIgnoreCase("ADD")) {
								if(cnt < 1){
									insertRoleLog = true;			
									listToAdd.add(r.getRoleId());
		                            _log.info("add role " + r.getName() + " to user " + u.getScreenName());
								}
								
							} else if (strRole.getAction().equalsIgnoreCase("REMOVE")) {
								if(cnt == 1){
									insertRoleLog = true;
									listToRemove.add(r.getRoleId());
                                    _log.info("remove role " + r.getName() + " from user " + u.getScreenName());
								}								 
							} else {
								_log.info("Invalid Action - allowed ADD/REMOVE - provided " + strRole.getAction());
							}
							if (insertRoleLog) {
                                String sql = "INSERT INTO USERS_ROLES_LOG (USERID, USERNAME, ROLEID, ROLENAME, INSERT_USER, INSERT_USERID, INSERT_DATE, ACTION, INFO)"
                                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                                jdbcTemplate.update(sql, u.getUserId(), u.getScreenName(), r.getRoleId(), r.getName(),
                                        "SYNC", 0, new Date(), strRole.getAction(), uamid);
							}
							retryFile.delete();
							
						} else {
							_log.error("Role does not exist and could not be created - " + strRole);
						}
					}

					long roleidsToAdd[] = new long[listToAdd.size()];
					long roleidsToRemove[] = new long[listToRemove.size()];

					int a = 0;
					for (long val : listToAdd) {
						roleidsToAdd[a++] = val;
					}

					int b = 0;
					for (long val : listToRemove) {
						roleidsToRemove[b++] = val;
					}

					RoleLocalServiceUtil.addUserRoles(u.getUserId(), roleidsToAdd);
					RoleLocalServiceUtil.unsetUserRoles(u.getUserId(), roleidsToRemove);
                } else {
                    _log.info("User " + user.getScreenName() + " does not exist, wait for the LDAP Import of all users and try again in 30 minutes");
                    new Timer("UserRoleImport-next-try-thread", true).schedule(new TimerTask() {
                        @Override
                        public void run() {
                            importUserRole(lurd, filename);
                        }
                    }, 30 * 60 * 1000);
                }
			}
		} catch (Exception e1) {
			_log.error(e1.toString());
		}

	}
    


    protected String extractUamId(String fileName) {
        if (fileName != null && fileName.startsWith("UAM_")) {
            String[] parts = fileName.split("_");
            if(parts.length > 1) {
                return parts[1];
            }
        }
        return null;
    }

}
