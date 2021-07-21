package de.unioninvestment.portal.liferay.addon.portlet;


import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

public class Crud2GoUpload {
	
	
	
	private static Log logger = LogFactoryUtil.getLog(Crud2GoUpload.class.getName());	
	
	private Crud2GoUpload(){
	
	}
	
	public static void storeConfiguration(String portletId,long communityId, String configName,byte[] configXML, String user){
		Connection con = null;
		PreparedStatement ps = null;
		try {
			Context initContext = new InitialContext();
			DataSource liferayDs = (DataSource) initContext.lookup("java:/jdbc/liferayDs");
			con = liferayDs.getConnection();		
			ps = con.prepareStatement("INSERT INTO ADM_CONFIG (PORTLET_ID, COMMUNITY_ID, CONFIG_NAME, CONFIG_XML,USER_CREATED,DATE_CREATED) VALUES (?,?,?,?,?,?)");
			Blob configBlob = con.createBlob();
			configBlob.setBytes(1, configXML);
			ps.setString(1, portletId);
			ps.setLong(2, communityId);
			ps.setString(3, configName);
			ps.setBlob(4, configBlob);
			ps.setString(5, user);
			ps.setTimestamp(6,new Timestamp(System.currentTimeMillis()));
			ps.execute();
		} catch (Exception e) {
			logger.error(e.toString());
		}
		finally{
			if (ps != null) {
		        try {
		            ps.close();
		        } catch (SQLException e) { /* ignored */}
		    }
		    if (con != null) {
		        try {
		            con.close();
		        } catch (SQLException e) { /* ignored */}
		    }
		}
	}
	

}
