package de.unioninvestment.portal.liferay.addon.portlet;

import com.liferay.portal.model.Layout;

public interface IPortlet {
	
	public void addPortlet(Layout layout, int orderid, long communityId,
			String columnId);

}
