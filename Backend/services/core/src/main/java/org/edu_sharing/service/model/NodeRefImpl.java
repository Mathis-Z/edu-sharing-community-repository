package org.edu_sharing.service.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.edu_sharing.repository.client.tools.CCConstants;

public class NodeRefImpl implements NodeRef {

	String repositoryId;

	List<NodeRef> usedInCollections = new ArrayList<>();

	public static class PreviewImpl implements Preview {
		String mimetype;
		byte[] data;

		public PreviewImpl(String mimetype, byte[] data) {
			this.mimetype = mimetype;
			this.data = data;
		}

		@Override
		public String getMimetype() {
			return mimetype;
		}

		public void setMimetype(String mimetype) {
			this.mimetype = mimetype;
		}

		@Override
		public byte[] getData() {
			return data;
		}

		public void setData(byte[] data) {
			this.data = data;
		}
	}

	String storeProtocol;
	
	String storeId;
	
	String nodeId;

	Preview preview;
	HashMap<String, Object> properties;
	Map<String, Boolean> permissions;
	private List<String> aspects;

	String owner;

	public NodeRefImpl(){
		
	}
	public NodeRefImpl(String repositoryId, String storeProtocol, String storeId, String nodeId){
		this.repositoryId = repositoryId;
		this.storeId = storeId;
		this.storeProtocol = storeProtocol;
		this.nodeId = nodeId;
	}
	public NodeRefImpl(String repositoryId, String storeProtocol, String storeId, HashMap<String, Object> properties ){
		this.repositoryId = repositoryId;
		this.storeId = storeId;
		this.storeProtocol = storeProtocol;
		this.nodeId = (String)properties.get(CCConstants.SYS_PROP_NODE_UID);
		this.properties = properties;
	}

	@Override
	public String getRepositoryId() {
		return repositoryId;
	}

	public void setRepositoryId(String repositoryId) {
		this.repositoryId = repositoryId;
	}

	@Override
	public String getStoreId() {
		return storeId;
	}

	public void setStoreId(String storeId) {
		this.storeId = storeId;
	}

	@Override
	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	@Override
	public HashMap<String, Object> getProperties() {
		return properties;
	}

	public void setProperties(HashMap<String, Object> properties) {
		this.properties = properties;
	}
	
	@Override
	public String getStoreProtocol() {
		return this.storeProtocol;
	}
	
	public void setStoreProtocol(String storeProtocol) {
		this.storeProtocol = storeProtocol;
	}

	@Override
	public Map<String, Boolean> getPermissions() {
		return permissions;
	}

	@Override
	public void setPermissions(Map<String, Boolean> permissions) {
		this.permissions = permissions;
	}

	@Override
	public void setAspects(List<String> aspects) {
		this.aspects = aspects;
	}

	@Override
	public List<String> getAspects() {
		return aspects;
	}

	@Override
	public void setUsedInCollections(List<NodeRef> usedInCollections) {
		this.usedInCollections = usedInCollections;
	}

	@Override
	public List<NodeRef> getUsedInCollections() {
		return usedInCollections;
	}

	@Override
	public void setPreview(Preview preview) {
		this.preview = preview;
	}

	@Override
	public Preview getPreview() {
		return preview;
	}

	@Override
	public String getOwner() { return owner;}

	@Override
	public void setOwner(String owner) { this.owner = owner; }
}
