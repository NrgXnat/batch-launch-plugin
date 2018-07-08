package com.radiologics.bulk.launch;


import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.nrg.framework.orm.hibernate.AbstractHibernateEntity;
import org.nrg.xft.security.UserI;

/**
 * @author Mohana Ramaratnam
 *
 */
@Entity
@Table( uniqueConstraints = {@UniqueConstraint(columnNames = {"bundleId", "launchDate","launchedBy"})}	)
public class BulkLaunchRecorder extends AbstractHibernateEntity {
	
	private String bundleId;
	private Date launchDate;
	private String searchXml;
	private UserI launchedBy;
	
	/**
	 * @return the bundleId
	 */
	public String getBundleId() {
		return bundleId;
	}
	/**
	 * @param bundleId the bundleId to set
	 */
	public void setBundleId(String bundleId) {
		this.bundleId = bundleId;
	}
	/**
	 * @return the launchDate
	 */
	public Date getLaunchDate() {
		return launchDate;
	}
	/**
	 * @param launchDate the launchDate to set
	 */
	public void setLaunchDate(Date launchDate) {
		this.launchDate = launchDate;
	}
	/**
	 * @return the searchXml
	 */
	
	@Column(columnDefinition="TEXT")
	public String getSearchXml() {
		return searchXml;
	}
	/**
	 * @param searchXml the searchXml to set
	 */
	public void setSearchXml(String searchXml) {
		this.searchXml = searchXml;
	}
	
	
}
