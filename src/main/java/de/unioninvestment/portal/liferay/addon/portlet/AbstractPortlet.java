package de.unioninvestment.portal.liferay.addon.portlet;

import java.util.List;

import javax.portlet.PortletPreferences;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.service.PortletPreferencesLocalServiceUtil;
import com.liferay.portal.util.PortletKeys;

import de.unioninvestment.portal.liferay.addon.content.jaxb.Portlet;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Preference;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Preferences;

public abstract class AbstractPortlet implements
		de.unioninvestment.portal.liferay.addon.portlet.IPortlet {

	private static final String PORTLET_TITLE_PREF_KEY = "ui-portlet-title";
	LayoutTypePortlet layoutTypePortlet;
	Portlet portlet;

	String portletIdNew;
	private static Log _log = LogFactoryUtil.getLog(AbstractPortlet.class);

	public void addPortlet(Layout layout) {

	}

	public void setPortletpreferences(Layout pLayout, String portletTitle) {
		try {
			PortletPreferences prefs = PortletPreferencesLocalServiceUtil
					.getPreferences(pLayout.getCompanyId(),
							PortletKeys.PREFS_OWNER_ID_DEFAULT,
							PortletKeys.PREFS_OWNER_TYPE_LAYOUT,
							pLayout.getPlid(), portletIdNew);

			if (portletTitle != null) {
				prefs.setValue(PORTLET_TITLE_PREF_KEY, portletTitle);
			}

			Preferences preferences = portlet.getPreferences();
			if (preferences != null) {

				List<Preference> prefList = preferences.getPreference();
				for (Preference p : prefList) {
					String value = p.getValue();
					if (value == null) {
						if (p.getUrl() != null && p.getEncoding() != null) {
							value = SvnExporter.getTextFileFromURL(p.getUrl(),
									p.getEncoding());
						} else {
							_log.error("Either the preference value or url and encoding must be set for preference '"
									+ p.getName() + "'!");
							continue;
						}
					}
					if (value == null) {
						_log.error("Missing value for preference '"
								+ p.getName() + "'!");
					}
					prefs.setValue(p.getName(), value);
					_log.debug("add portlet preference " + p.getName() + " - "
							+ p.getValue());
				}
			}
			prefs.store();
		} catch (Exception e) {
			_log.error("Error setting portlet preferences " + e.toString());
		}
	}

}
