package de.unioninvestment.portal.liferay.addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.portlet.PortletPreferences;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import com.liferay.counter.service.CounterLocalServiceUtil;
import com.liferay.portal.kernel.cache.CacheRegistryUtil;
import com.liferay.portal.kernel.cache.MultiVMPoolUtil;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.webcache.WebCachePoolUtil;
import com.liferay.portal.model.Company;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutConstants;
import com.liferay.portal.model.LayoutTemplate;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.model.ResourcePermission;
import com.liferay.portal.model.User;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.service.CompanyLocalServiceUtil;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.service.LayoutTemplateLocalServiceUtil;
import com.liferay.portal.service.PortletPreferencesLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortletKeys;

import de.unioninvestment.portal.liferay.addon.content.jaxb.LiferayContentDescriptor;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Page;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Pages;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Portlet;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Role;
import de.unioninvestment.portal.liferay.addon.content.jaxb.Roles;
import de.unioninvestment.portal.liferay.addon.portlet.Crud2GoPortlet;
import de.unioninvestment.portal.liferay.addon.portlet.DefaultPortlet;
import de.unioninvestment.portal.liferay.addon.portlet.IPortlet;

public class LiferayContentImporter {

    private static Log _log = LogFactoryUtil.getLog(LiferayContentImporter.class.getName());
    
    private static final String LAYOUT_3_PORTLETS = "sofia_6_6";

    boolean bErr = false;
    ArrayList<String> importWarnings = new ArrayList<String>();

    public long communityId;
    public long defaultUserId;
    public String defaultLayout = "sofia_3_9";
    public String menuDisplayStyle = "from-level-1-with-title";

    public LiferayContentImporter() {

    }

    public void importLiferayContent(LiferayContentDescriptor descriptor) throws SystemException {
        this.bErr = false;

        if (descriptor.getCommunityId() != null) {
            this.communityId = descriptor.getCommunityId();
            _log.info("communityId from descriptor " + this.communityId);
        } else {
            this.communityId = getDefaultCompanyId();
            this.communityId = getGroupFromDefaultCompanyId(this.communityId);
            _log.info("default communityId " + this.communityId);
        }

        if (descriptor.getDefaultUserId() != null) {
            this.defaultUserId = descriptor.getDefaultUserId();
        } else {
            this.defaultUserId = getDefaultUserId();
        }

        if (descriptor.getDefaultLayout() != null) {
            this.defaultLayout = descriptor.getDefaultLayout();
        }
        if (descriptor.getMenuDisplayStyle() != null) {
            this.menuDisplayStyle = descriptor.getMenuDisplayStyle();
        }

        UserLocalServiceUtil.fetchUserById(this.defaultUserId);

        if (descriptor.getPages().getPage().isEmpty()) {
            throw new IllegalStateException("no pages defined");
        }

        Page rootPage = descriptor.getPages().getPage().get(0);
        _log.info("Import LCD: " + rootPage.getName());

        removeLayout(this.communityId, rootPage.getFriendlyUrl());
        importPages(descriptor.getPages(), 0, new ArrayList<Role>());
        sortRootPages();

        if (!this.bErr && (this.importWarnings.size() == 0)) {
            _log.info("Import LCD succesful: " + rootPage.getName());
        }
        if (this.importWarnings.size() > 0) {
            for (String warn : this.importWarnings) {
                _log.warn(warn);
            }
            _log.info("Import LCD ends with warnings - manual cleanup required, all pages should be available");

        }
    }

    private void sortRootPages() {
        List<Layout> rootLayouts = getLayoutsByParentId(this.communityId, 0);
        _log.info("Import : Sort all " + rootLayouts.size() + " root layouts alphabetically");

        SortedMap<String, Layout> layoutMap = new TreeMap<String, Layout>();
        for (Layout layout : rootLayouts) {
            String key = extractLayoutName(layout);
            if (layoutMap.containsKey(key)) {
                // dummy key to place duplicate names in order
                key = key + "_" + layoutMap.size();
            }
            layoutMap.put(key, layout);
        }

        int priority = 1;
        for (Layout layout : layoutMap.values()) {
            String name = extractLayoutName(layout);
            try {
                if ("Welcome".equalsIgnoreCase(name)) {
                    LayoutLocalServiceUtil.updatePriority(layout, 0);
                } else {
                    LayoutLocalServiceUtil.updatePriority(layout, priority++);
                }
            } catch (SystemException e) {
                _log.error("Cannot set priority for root-layout " + name + ": " + e.toString(), e);
            } catch (PortalException e) {
                _log.error("Cannot set priority for root-layout " + name + ": " + e.toString(), e);
            }
        }
    }

    private String extractLayoutName(Layout layout) {
        if ((layout.getName() == null) || !layout.getName().contains("</Name>")) {
            return layout.getName();
        }

        String name = layout.getName();
        name = name.substring(0, name.indexOf("</Name>"));
        name = name.substring(name.lastIndexOf('>') + 1);
        return name;
    }

    public void importPages(Pages pages, long parentLayoutId, List<Role> inheritedRoles) {
        List<Page> pageList = pages.getPage();
        for (Page page : pageList) {
            cleanupCache();
            importPage(page, parentLayoutId, new ArrayList<Role>(inheritedRoles));
        }
    }

    private void cleanupCache() {
        _log.debug("Import : Cache cleanup -> start");
        CacheRegistryUtil.clear(); // to clear all the Database caches
        MultiVMPoolUtil.clear();// clearing cache across JVM clusters
        WebCachePoolUtil.clear();// clearing cache in Single VM
        _log.debug("Import : Cache cleanup -> end");
    }

    public void importPage(Page page, long parentLayoutId, List<Role> inheritedRoles) {
        _log.debug("Import : add Page: " + page.getName());

        String layoutTemplate = this.defaultLayout;
        if (!StringUtils.isEmpty(page.getLayout())) {
            layoutTemplate = page.getLayout();
        }

        Layout layout = addLayout(page.getName(), page.getFriendlyUrl(), parentLayoutId, page.isHidden());
        if (layout != null) {
            inheritedRoles = setPermission(page, layout, inheritedRoles);
            setLayoutTemplate(layout, layoutTemplate);
            
            if (LAYOUT_3_PORTLETS.equals(layoutTemplate)) {
                if (page.getPortletsLeft() != null) {
                    addPortlets(layout, "column-1", page.getPortletsLeft().getPortlet());
                }
                if (page.getPortlets() != null) {
                    addPortlets(layout, "column-2", page.getPortlets().getPortlet());
                }
                if (page.getPortletsBottom() != null) {
                    addPortlets(layout, "column-3", page.getPortletsBottom().getPortlet());
                }                
            } else {
                if (page.isShowMenuPortlet()) {
                    addMenuPortlet(layout);
                }
                if ((page.getPortletsLeft() != null) && !isSingleColumnLayout(layoutTemplate)) {
                    addPortlets(layout, "column-1", page.getPortletsLeft().getPortlet());
                }
                if (page.getPortlets() != null) {
                    String column = isSingleColumnLayout(layoutTemplate) ? "column-1" : "column-2";
                    addPortlets(layout, column, page.getPortlets().getPortlet());
                }
            }

            // add subpage -->
            long nroot = layout.getLayoutId();
            if (page.getPages() != null) {
                _log.info("Import : add SubPage " + page.getName());
                importPages(page.getPages(), nroot, inheritedRoles);
            }

            // <-- add subpage
        } else {
            _log.error("importPage " + page.getName() + " -> layout is null");
        }
    }

    private boolean isSingleColumnLayout(String layoutTemplate) {
        return "1_column".equalsIgnoreCase(layoutTemplate);
    }

    private void addPortlets(Layout layout, String columnId, List<Portlet> portletList) {
        int orderId = 1;
        for (Portlet portlet : portletList) {
            IPortlet po = null;

            if (portlet.getType().equalsIgnoreCase("crud2go")) {
                po = new Crud2GoPortlet(portlet);
            } else {
                po = new DefaultPortlet(portlet);
            }
            po.addPortlet(layout, orderId++, this.communityId, columnId);
        }
    }

    private List<Role> setPermission(Page page, Layout layout, List<Role> inheritedRoles) {
        try {
            // rechte -->
            cleanupPermission(layout);
            for (Role role : inheritedRoles) {
                addRoleToLayout(layout, role.getName());
                _log.info("add inherited role " + role.getName() + " to page " + page.getName());
            }
            if (page.getRoles() != null) {
                Roles rs = page.getRoles();
                List<Role> roles = rs.getRole();
                for (Role role : roles) {
                    if (role != null) {
                        if (role.isInherit() != null) {
                            if (role.isInherit()) {
                                inheritedRoles.add(role);
                                _log.info("add role " + role.getName() + " to inheritatedRoles for subpages of " + page.getName());
                            }
                        }
                        addRoleToLayout(layout, role.getName());
                        _log.info("add role " + role.getName() + " to page " + page.getName());
                    }
                }
            }
        } catch (Exception e) {
            _log.error("Import LCD error : setPermission " + e.toString());
            this.bErr = true;
        }
        return inheritedRoles;
    }

    private void setLayoutTemplate(Layout layout, String templateId) {
        LayoutTypePortlet layoutTypePortlet = (LayoutTypePortlet) layout.getLayoutType();
        List<LayoutTemplate> templates = LayoutTemplateLocalServiceUtil.getLayoutTemplates();

        boolean templateAvailable = false;
        for (LayoutTemplate tmplt : templates) {
            if (tmplt.getLayoutTemplateId().equalsIgnoreCase(templateId)) {
                templateAvailable = true;
            }
        }

        if (templateAvailable) {
            layoutTypePortlet.setLayoutTemplateId(0, templateId, false);
        } else {
            _log.error("Import LCD error : could not find layout " + templateId);
            layoutTypePortlet.setLayoutTemplateId(0, this.defaultLayout, false);
        }

    }

    private void addMenuPortlet(Layout layout) {
        try {
            LayoutTypePortlet layoutTypePortlet = (LayoutTypePortlet) layout.getLayoutType();
            String portletIdInc = layoutTypePortlet.addPortletId(0, "71", "column-1", 0, false);
            PortletPreferences prefs = PortletPreferencesLocalServiceUtil.getPreferences(layout.getCompanyId(), PortletKeys.PREFS_OWNER_ID_DEFAULT,
                    PortletKeys.PREFS_OWNER_TYPE_LAYOUT, layout.getPlid(), portletIdInc);
            prefs.setValue("nestedChildren", "1");
            prefs.setValue("includedLayouts", "auto");
            prefs.setValue("rootLayoutLevel", "1");
            prefs.setValue("bulletStyle", "Punkte");
            prefs.setValue("headerType", "root-layout");
            prefs.setValue("rootLayoutType", "absolute");
            prefs.setValue("displayStyle", this.menuDisplayStyle);
            prefs.store();
            LayoutLocalServiceUtil.updateLayout(layout.getGroupId(), layout.isPrivateLayout(), layout.getLayoutId(), layout.getTypeSettings());
        } catch (Exception e) {
            _log.error("Import LCD error : addMenu " + e.toString());
            this.bErr = true;
        }
    }

    public Layout addLayout(String name, String friendlyUrl, long parentLayoutId, boolean pHidden) {
        Layout layout = null;
        try {
            boolean privateLayout = false;
            String childpageName = name;
            String titleChild = name;
            String descriptionChild = name;
            String type = LayoutConstants.TYPE_PORTLET;
            boolean hidden = pHidden;
            String friendlyURL = friendlyUrl;
            ServiceContext serviceContext = new ServiceContext();
            serviceContext.setScopeGroupId(this.communityId);
            _log.info("Import : Add Layout " + childpageName + " with URL " + friendlyUrl);
            if (getLayoutsByFriendlyUrl(this.communityId, friendlyUrl) != null) {
                String tempFriendlyUrl = friendlyURL + RandomStringUtils.random(5);
                _log.warn("LCD import warning : layout already exists " + friendlyUrl + " use tempFriendlyUrl " + tempFriendlyUrl);
                this.importWarnings.add("LCD import warning : layout already exists " + friendlyUrl + " use tempFriendlyUrl " + tempFriendlyUrl);
                layout = LayoutLocalServiceUtil.addLayout(this.defaultUserId, this.communityId, privateLayout, parentLayoutId, childpageName,
                        titleChild, descriptionChild, type, hidden, tempFriendlyUrl, serviceContext);
            } else {
                layout = LayoutLocalServiceUtil.addLayout(this.defaultUserId, this.communityId, privateLayout, parentLayoutId, childpageName,
                        titleChild, descriptionChild, type, hidden, friendlyURL, serviceContext);
            }

        } catch (Exception e) {
            _log.error("Import LCD error : addLayout " + friendlyUrl + " " + e.toString());
            this.bErr = true;

        }

        return layout;
    }

    @SuppressWarnings("unchecked")
    public void removeLayout(long pGroupId, String friendlyURL) {
        try {
            DynamicQuery query = DynamicQueryFactoryUtil.forClass(Layout.class).add(PropertyFactoryUtil.forName("friendlyURL").eq(friendlyURL))
                    .add(PropertyFactoryUtil.forName("groupId").eq(new Long(pGroupId)));
            List<Layout> lay = LayoutLocalServiceUtil.dynamicQuery(query);
            if (lay.size() == 1) {
                Layout l = lay.get(0);
                _log.info("Import : Delete Layout " + l.getFriendlyURL());
                ServiceContext serviceContext = new ServiceContext();
                serviceContext.setScopeGroupId(this.communityId);
                LayoutLocalServiceUtil.deleteLayout(l.getPlid(), serviceContext);
            } else if (lay.size() == 0) {
                _log.info("Import : Delete Layout : no layout found");
            } else {
                _log.info("Import : Delete Layout : ambiguous layout found - please cleanup first");
            }
        } catch (Exception e) {
            _log.error("Import LCD error : removeLayout " + friendlyURL + " " + e.toString());
            this.bErr = true;
        }
    }

    public List<Layout> getLayoutsByParentId(long pGroupId, long parentLayoutId) {
        try {
            DynamicQuery query = DynamicQueryFactoryUtil.forClass(Layout.class).add(PropertyFactoryUtil.forName("parentLayoutId").eq(parentLayoutId))
                    .add(PropertyFactoryUtil.forName("groupId").eq(new Long(pGroupId)));
            @SuppressWarnings("unchecked")
            List<Layout> layouts = LayoutLocalServiceUtil.dynamicQuery(query);
            return layouts;
        } catch (SystemException e) {
            _log.error("Cannot get Layouts by parent-id " + parentLayoutId + ": " + e.toString(), e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Layout> getLayoutsByFriendlyUrl(long pGroupId, String friendlyURL) {
        try {
            DynamicQuery query = DynamicQueryFactoryUtil.forClass(Layout.class).add(PropertyFactoryUtil.forName("friendlyURL").eq(friendlyURL))
                    .add(PropertyFactoryUtil.forName("groupId").eq(new Long(pGroupId)));
            List<Layout> lay = LayoutLocalServiceUtil.dynamicQuery(query);
            if (lay.size() > 0) {
                return lay;
            }
        } catch (SystemException e) {
            _log.error("Cannot get Layout by FriendlyUrl " + e.toString());
        }
        return null;
    }

    public void cleanupPermission(Layout layout) {
        try {
            String primKey = String.valueOf(layout.getPrimaryKey());
            List<String> tbr = new ArrayList<String>();
            tbr.add(ActionKeys.VIEW);
            tbr.add(ActionKeys.ADD_DISCUSSION);
            tbr.add(ActionKeys.CUSTOMIZE);

            for (String act : tbr) {
                com.liferay.portal.model.Role role = RoleLocalServiceUtil.getRole(layout.getCompanyId(), "Guest");
                com.liferay.portal.model.Role roleSM = RoleLocalServiceUtil.getRole(layout.getCompanyId(), "Site Member");
                if (ResourcePermissionLocalServiceUtil.hasResourcePermission(layout.getCompanyId(), "com.liferay.portal.model.Layout", 4, primKey,
                        role.getRoleId(), act)) {
                    ResourcePermissionLocalServiceUtil.removeResourcePermission(layout.getCompanyId(), "com.liferay.portal.model.Layout", 4, primKey,
                            role.getRoleId(), act);
                }
                if (ResourcePermissionLocalServiceUtil.hasResourcePermission(layout.getCompanyId(), "com.liferay.portal.model.Layout", 4, primKey,
                        roleSM.getRoleId(), act)) {
                    ResourcePermissionLocalServiceUtil.removeResourcePermission(layout.getCompanyId(), "com.liferay.portal.model.Layout", 4, primKey,
                            roleSM.getRoleId(), act);
                }
            }
        } catch (Exception e) {
            _log.error("Import LCD error : cleanupPermission " + e.toString());
            this.bErr = true;
        }

    }

    public void addRoleToLayout(Layout layout, String strRole) {
        try {
            long companyId = layout.getCompanyId();
            com.liferay.portal.model.Role role = RoleLocalServiceUtil.getRole(companyId, strRole);
            String primKey = String.valueOf(layout.getPrimaryKey());
            long resourcePermissionId = CounterLocalServiceUtil.increment(ResourcePermission.class.getName());
            ResourcePermission resourcePermission = ResourcePermissionLocalServiceUtil.createResourcePermission(resourcePermissionId);
            resourcePermission.setResourcePermissionId(resourcePermissionId);
            resourcePermission.setCompanyId(companyId);
            resourcePermission.setName("com.liferay.portal.model.Layout");
            resourcePermission.setScope(4);
            resourcePermission.setPrimKey(primKey);
            resourcePermission.setRoleId(role.getRoleId());
            resourcePermission.setActionIds(1);
            ResourcePermissionLocalServiceUtil.addResourcePermission(resourcePermission);
        } catch (Exception e) {
            _log.error("Import LCD error : addRoleToLayout " + e.toString());
            this.bErr = true;
        }
    }

    public long getDefaultCompanyId() {
        try {
            DynamicQuery query = DynamicQueryFactoryUtil.forClass(Company.class).add(PropertyFactoryUtil.forName("active").eq(Boolean.TRUE));
            List<Company> users = CompanyLocalServiceUtil.dynamicQuery(query);
            Company c = users.get(0);
            return c.getCompanyId();
        } catch (SystemException e) {
            _log.error("Cannot get default company " + e.toString());
            return 0;
        }
    }

    public long getDefaultUserId() {

        try {
            DynamicQuery query = DynamicQueryFactoryUtil.forClass(User.class).add(PropertyFactoryUtil.forName("defaultUser").eq(Boolean.TRUE));
            List<User> users = UserLocalServiceUtil.dynamicQuery(query);
            User u = users.get(0);
            return u.getUserId();
        } catch (SystemException e) {
            _log.error("Cannot get default user " + e.toString());
            return 0;
        }

    }

    public long getGroupFromDefaultCompanyId(long companyId) {
        try {
            DynamicQuery query = DynamicQueryFactoryUtil.forClass(Group.class);
            query.add(PropertyFactoryUtil.forName("site").eq(Boolean.TRUE));
            query.add(PropertyFactoryUtil.forName("type").eq(1));

            List<Group> groups = GroupLocalServiceUtil.dynamicQuery(query);
            Group g = groups.get(0);
            return g.getGroupId();
        } catch (SystemException e) {
            _log.error("Cannot get group from default company " + e.toString());
            return 0;
        }
    }

}
