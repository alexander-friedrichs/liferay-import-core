package de.unioninvestment.portal.liferay.addon.portlet;

import java.io.ByteArrayInputStream;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.jdbc.core.JdbcTemplate;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.model.ResourcePermission;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;

import de.unioninvestment.portal.liferay.addon.content.jaxb.Actionid;
import de.unioninvestment.portal.liferay.addon.content.jaxb.MappedRole;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Mappings;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Parameter;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Permission;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Permissions;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Portlet;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Replacement;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Replacements;

public class Crud2GoPortlet extends AbstractPortlet implements
		de.unioninvestment.portal.liferay.addon.portlet.IPortlet {

	private static Log logger = LogFactoryUtil.getLog(Crud2GoPortlet.class.getName());

	private DataSource liferayDs = null;
	private JdbcTemplate jdbcTemplate = null;

	HashMap<String, ResourcePermission> permissionMap = new HashMap<String, ResourcePermission>();

	public Crud2GoPortlet(Portlet pP) {
		portlet = pP;

	}

	@Override
    public void addPortlet(Layout layout, int orderid, long communityId,
			String columnId) {
		try {

			Context initContext = new InitialContext();
			liferayDs = (DataSource) initContext.lookup("java:/jdbc/liferayDs");
			jdbcTemplate = new JdbcTemplate(liferayDs);

			logger.debug("add Crud2GoPortlet");
			layoutTypePortlet = (LayoutTypePortlet) layout.getLayoutType();
			portletIdNew = layoutTypePortlet.addPortletId(0,
					"crudportlet_WAR_eaiadministration", columnId, orderid,
					false);

			String portletTitle = null;
			List<Parameter> params = portlet.getParameters().getParameter();
			for (Parameter param : params) {
				if (param.getName().equalsIgnoreCase("crud2goURL")) {
					String c2gURL = param.getValue();
					byte[] configXML = SvnExporter.getFileAsByteArrayFromURL(param.getValue());
					portletTitle = parseConfigForPortletTitle(configXML);
                    if (portlet.getReplacements() != null && portlet.getReplacements().getReplacement() != null
                            && portlet.getReplacements().getReplacement().size() > 0) {
                        configXML = replaceAttributes(configXML, portlet.getReplacements());
                    }
					Crud2GoUpload.storeConfiguration(portletIdNew,layout.getGroupId(), c2gURL, configXML,"deployment");
				}
			}
			setPortletpreferences(layout, portletTitle);

			if (portlet.getPermissions() != null) {
				logger.debug("Permission settings for crud2go " + portletIdNew
						+ " with communityId " + communityId);
				Permissions perms = portlet.getPermissions();
				setPermissions(layout, portletIdNew, orderid, perms, communityId);
			}

			LayoutLocalServiceUtil.updateLayout(layout.getGroupId(),
					layout.isPrivateLayout(), layout.getLayoutId(),
					layout.getTypeSettings());

		} catch (Exception e) {
			logger.error("Error adding Portlet " + e.toString());
		}

	}

    private byte[] replaceAttributes(byte[] configXML, Replacements replacements) {
        String config = new String(configXML);
        for (Replacement rep : replacements.getReplacement()) {
            config = config.replaceAll(rep.getRegex(), rep.getValue());
            logger.info("Replacing " + rep.getRegex() + " with " + rep.getValue());
        }
        return config.getBytes();
    }

    static String parseConfigForPortletTitle(byte[] configXML) {
		XMLInputFactory inputFactory = XMLInputFactory.newInstance();
		XMLStreamReader reader = null;
		try {
			reader = inputFactory
					.createXMLStreamReader(new ByteArrayInputStream(configXML));

			while (reader.hasNext()) {
				reader.next();
				if (reader.getEventType() == XMLStreamReader.START_ELEMENT
						&& reader.getLocalName().equals("portlet")) {
					return reader.getAttributeValue(null, "title");
				}
			}
			return null;

		} catch (XMLStreamException e) {
			logger.warn("Failed to parse config for title: " + e.getMessage());
			return null;
			
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (XMLStreamException e) {
					// ignore error
				}
			}
		}
	}

	private void setPermissions(Layout layout, String portletIdNew,
			int orderid, Permissions perms, long communityId) {
		List<Permission> lPerms = perms.getPermission();
		for (Permission perm : lPerms) {
			setPermission(layout, portletIdNew, orderid, perm, communityId);
		}
	}

	private void setPermission(Layout layout, String portletIdNew, int orderid,
			Permission perm, long communityId) {
		long primKey = jdbcTemplate.queryForObject(
				"select RESOURCEID_SEQ.nextval from dual", Long.class);
		String resourceId = portletIdNew + "_" + communityId + "_"
				+ perm.getName();
		jdbcTemplate
				.update("INSERT INTO RESOURCEID_PRIMKEY (PRIMKEY, RESOURCEID) values (?,?)",
						new Object[] { primKey, resourceId }, new int[] {
								Types.NUMERIC, Types.VARCHAR });

		if (perm.getActions() != null) {
			List<Actionid> actList = perm.getActions().getActionid();
			permissionMap.clear();
			permissionMap = new HashMap<String, ResourcePermission>();
			for (Actionid aid : actList) {
				setResourcePermissions(layout, aid, orderid, primKey);
			}
			permissionMap.clear();
		}
	}

	private void setResourcePermissions(Layout layout, Actionid aid, int orderid, long primKey) {
		try {
			String sqlAid = "select bitwisevalue from resourceaction where name ='de.unioninvestment.eai.portal.portlet.crud.domain.model.Role' and actionid='"
					+ aid.getName() + "'";
			long actionId = jdbcTemplate.queryForObject(sqlAid, Long.class);
			if (aid.getMappings() != null) {
				Mappings maps = aid.getMappings();
				List<MappedRole> mappedRoles = maps.getMappedRole();
				for (MappedRole role : mappedRoles) {
					com.liferay.portal.model.Role liferayRole = RoleLocalServiceUtil
							.getRole(layout.getCompanyId(), role.getName());
					String key = layout.getCompanyId() + role.getName() + 4 + primKey + liferayRole.getRoleId();
					if (permissionMap.containsKey(key)) {
						ResourcePermission rp = permissionMap.get(key);
						long aide = rp.getActionIds();
						rp.setActionIds(aide + actionId);
						ResourcePermissionLocalServiceUtil.updateResourcePermission(rp);
					} else {
						logger.debug("no resource permission - create ");
						long resourcePermissionId = CounterLocalServiceUtil.increment();
						ResourcePermission resourcePermission = ResourcePermissionLocalServiceUtil.createResourcePermission(resourcePermissionId);
						resourcePermission.setResourcePermissionId(resourcePermissionId);
						resourcePermission.setCompanyId(layout.getCompanyId());
						resourcePermission.setName("de.unioninvestment.eai.portal.portlet.crud.domain.model.Role");
						resourcePermission.setScope(4);
						resourcePermission.setPrimKey(String.valueOf(primKey));
						resourcePermission.setRoleId(liferayRole.getRoleId());
						resourcePermission.setActionIds(actionId);
						ResourcePermissionLocalServiceUtil.addResourcePermission(resourcePermission);
						permissionMap.put(key, resourcePermission);
					}
				}
			}
		} catch (PortalException e) {
			logger.error(e.toString());
		} catch (SystemException e) {
			logger.error(e.toString());
		}
	}

}
