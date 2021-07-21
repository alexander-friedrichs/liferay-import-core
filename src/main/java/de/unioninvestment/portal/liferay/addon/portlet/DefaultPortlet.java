package de.unioninvestment.portal.liferay.addon.portlet;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.service.LayoutLocalServiceUtil;

import de.unioninvestment.portal.liferay.addon.content.jaxb.Portlet;

public class DefaultPortlet extends AbstractPortlet implements de.unioninvestment.portal.liferay.addon.portlet.IPortlet{
	
	private static Log logger = LogFactoryUtil.getLog(DefaultPortlet.class);
	
	public DefaultPortlet(Portlet pP){
		portlet = pP;
	}

	public void addPortlet(Layout layout, int orderid, long communityId,
			String columnId) {
		try {
			logger.debug("add DefaultPortlet");
			layoutTypePortlet = (LayoutTypePortlet) layout.getLayoutType();
			portletIdNew = layoutTypePortlet.addPortletId(0, portlet.getType(),
					columnId, orderid, false);
			setPortletpreferences(layout, null);
			LayoutLocalServiceUtil.updateLayout(layout.getGroupId(), layout.isPrivateLayout(), layout.getLayoutId(), layout.getTypeSettings());
			
			if(portlet.getPermissions() != null)
				logger.debug("Permission settings ignored for Portlet != crud2go");

		} catch (Exception e) {
			logger.error("Cannot add Portlet " + e.toString());
		} 

	}

}
