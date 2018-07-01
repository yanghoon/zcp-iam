package com.skcc.cloudz.zcp.namespace.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.skcc.cloudz.zcp.common.exception.ZcpException;
import com.skcc.cloudz.zcp.common.model.ClusterRole;
import com.skcc.cloudz.zcp.common.model.UserList;
import com.skcc.cloudz.zcp.common.model.ZcpUser;
import com.skcc.cloudz.zcp.common.util.Util;
import com.skcc.cloudz.zcp.manager.KeyCloakManager;
import com.skcc.cloudz.zcp.manager.KubeCoreManager;
import com.skcc.cloudz.zcp.manager.KubeRbacAuthzManager;
import com.skcc.cloudz.zcp.manager.ResourcesLabelManager;
import com.skcc.cloudz.zcp.manager.ResourcesNameManager;
import com.skcc.cloudz.zcp.namespace.vo.InquiryNamespaceVO;
import com.skcc.cloudz.zcp.namespace.vo.ItemList;
import com.skcc.cloudz.zcp.namespace.vo.KubeDeleteOptionsVO;
import com.skcc.cloudz.zcp.namespace.vo.NamespaceVO;
import com.skcc.cloudz.zcp.namespace.vo.QuotaVO;
import com.skcc.cloudz.zcp.namespace.vo.RoleBindingVO;
import com.skcc.cloudz.zcp.namespace.vo.ServiceAccountVO;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1LimitRange;
import io.kubernetes.client.models.V1LimitRangeList;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1NamespaceList;
import io.kubernetes.client.models.V1NamespaceSpec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ResourceQuota;
import io.kubernetes.client.models.V1ResourceQuotaList;
import io.kubernetes.client.models.V1RoleBinding;
import io.kubernetes.client.models.V1RoleBindingList;
import io.kubernetes.client.models.V1RoleRef;
import io.kubernetes.client.models.V1Subject;

@Service
public class NamespaceService {

	private final Logger log = LoggerFactory.getLogger(NamespaceService.class);

	@Autowired
	private KeyCloakManager keyCloakManager;

	@Autowired
	private KubeCoreManager kubeCoreManager;

	@Autowired
	private KubeRbacAuthzManager kubeRbacAuthzManager;

	@Value("${zcp.kube.namespace}")
	private String systemNamespace;

	public V1Namespace getNamespace(String namespace) throws ZcpException {
		try {
			return kubeCoreManager.getNamespace(namespace);
		} catch (ApiException e) {
			throw new ZcpException("N0005", e.getMessage());
		}
	}

	public V1NamespaceList getNamespaceList() throws ZcpException {
		try {
			return kubeCoreManager.getNamespaceList();
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("N001", e.getMessage());
		}
	}

	public NamespaceVO getNamespaceResource(String namespace) throws ZcpException {
		NamespaceVO vo = new NamespaceVO();
		vo.setNamespace(namespace);

		V1ResourceQuotaList quotaList = null;
		try {
			quotaList = kubeCoreManager.getResourceQuotaList(namespace);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("N003", e.getMessage());
		}

		if (quotaList != null) {
			if (quotaList.getItems() != null && !quotaList.getItems().isEmpty()) {
				if (quotaList.getItems().size() > 1) {
					throw new ZcpException("N003", "The resource quotas is more than 1");
				}
				vo.setResourceQuota(quotaList.getItems().get(0));
			}
		}

		V1LimitRangeList limitRangeList = null;
		try {
			limitRangeList = kubeCoreManager.getLimitRanges(namespace);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("N003", e.getMessage());
		}

		if (limitRangeList != null) {
			if (limitRangeList.getItems() != null && !limitRangeList.getItems().isEmpty()) {
				if (limitRangeList.getItems().size() > 1) {
					throw new ZcpException("N003", "The limit range is more than 1");
				}
				vo.setLimitRange(limitRangeList.getItems().get(0));
			}
		}

		return vo;

	}

	@SuppressWarnings("unchecked")
	private List<QuotaVO> getResourceQuotaList() throws ZcpException {
		V1ResourceQuotaList quota = null;
		try {
			quota = kubeCoreManager.getAllResourceQuotaList();
		} catch (ApiException e) {
			throw new ZcpException("N0004", e.getMessage());
		}

		List<QuotaVO> listQuota = new ArrayList<>();
		for (V1ResourceQuota q : quota.getItems()) {
			QuotaVO vo = new QuotaVO();
			Object[] obj = getInfoOfNamespace(q.getMetadata().getNamespace());
			vo.setName(q.getMetadata().getName());
			vo.setNamespace(q.getMetadata().getNamespace());
			vo.setUserCount(getNamespaceUserCount(q.getMetadata().getNamespace()));
			// vo.setSpec(q.getSpec());
			vo.setActive((String) obj[0]);
			vo.setLabels((List<String>) obj[1]);
			vo.setStatus(q.getStatus());
			if (q.getStatus().getUsed() != null) {
				vo.setUsedCpuRate(getUsedCpuRate(
						q.getStatus().getUsed().get("requests.cpu") == null ? "0"
								: q.getStatus().getUsed().get("requests.cpu"),
						q.getStatus().getHard().get("limits.cpu") == null ? "0"
								: q.getStatus().getHard().get("limits.cpu")));

				vo.setUsedMemoryRate(getUsedMemoryRate(
						q.getStatus().getUsed().get("requests.memory") == null ? "0"
								: q.getStatus().getUsed().get("requests.memory"),
						q.getStatus().getHard().get("limits.memory") == null ? "0"
								: q.getStatus().getHard().get("limits.memory")));
			}
			vo.setCreationTimestamp(new DateTime(q.getMetadata().getCreationTimestamp()));
			listQuota.add(vo);
		}
		return listQuota;
	}

	public ItemList<QuotaVO> getResourceQuotaList(InquiryNamespaceVO vo) throws ZcpException {
		// sortOrder = true asc;
		// sortOrder = false desc;
		List<QuotaVO> listQuotas = getResourceQuotaList();
		ItemList<QuotaVO> list = new ItemList<>();

		Stream<QuotaVO> stream = listQuotas.stream();
		if (!StringUtils.isEmpty(vo.getSortItem()))
			switch (vo.getSortItem()) {
			case "namespace":
				if (vo.isSortOrder())
					stream = stream.sorted((a, b) -> a.getNamespace().compareTo(b.getNamespace()));// asc
				else
					stream = stream.sorted((a, b) -> b.getNamespace().compareTo(a.getNamespace()));
				break;
			case "cpu":
				if (vo.isSortOrder())
					stream = stream.sorted((a, b) -> Util.compare(a.getUsedCpuRate(), b.getUsedCpuRate()));
				else
					stream = stream.sorted((a, b) -> Util.compare(b.getUsedCpuRate(), a.getUsedCpuRate()));
				break;
			case "memory":
				if (vo.isSortOrder())
					stream = stream.sorted((a, b) -> Util.compare(a.getUsedMemoryRate(), b.getUsedMemoryRate()));
				else
					stream = stream.sorted((a, b) -> Util.compare(b.getUsedMemoryRate(), a.getUsedMemoryRate()));
				break;
			case "user":
				if (vo.isSortOrder())
					stream = stream.sorted((a, b) -> Util.compare(a.getUserCount(), b.getUserCount()));
				else
					stream = stream.sorted((a, b) -> Util.compare(b.getUserCount(), a.getUserCount()));
				break;
			case "status":
				if (vo.isSortOrder())
					stream = stream.sorted((a, b) -> a.getActive().compareTo(b.getActive()));
				else
					stream = stream.sorted((a, b) -> b.getActive().compareTo(a.getActive()));
				break;
			case "createTime":
				if (vo.isSortOrder())
					stream = stream.sorted((a, b) -> a.getCreationTimestamp().compareTo(b.getCreationTimestamp()));
				else
					stream = stream.sorted((a, b) -> b.getCreationTimestamp().compareTo(a.getCreationTimestamp()));
				break;
			}
		if (!StringUtils.isEmpty(vo.getNamespace())) {
			stream = stream.filter(namespace -> namespace.getNamespace().indexOf(vo.getNamespace()) > -1);
		}

		if (!StringUtils.isEmpty(vo.getLabel())) {
			stream = stream.filter((namespace) -> {
				Stream<String> s = namespace.getLabels().stream().filter(label -> label.indexOf(vo.getLabel()) > -1);
				return s.count() > 0;
			});
		}

		if (stream != null)
			listQuotas = stream.collect(Collectors.toList());

		list.setItems(listQuotas);
		return list;
	}

	public Object[] getInfoOfNamespace(String namespaceName) throws ZcpException {
		V1Namespace namespace = getNamespace(namespaceName);
		String active = namespace.getStatus().getPhase().equals("Active") ? "active" : "inactive";
		List<String> labels = Util.MapToList(namespace.getMetadata().getLabels());
		Object[] obj = { active, labels };

		return obj;
	}

	public ItemList<String> getAllLabelList() throws ZcpException {
		List<String> listLabel = new ArrayList<>();
		for (V1Namespace namespace : this.getNamespaceList().getItems()) {
			List<String> labels = Util.MapToList(namespace.getMetadata().getLabels());
			listLabel.addAll(labels);
		}

		List<String> items = listLabel.stream().distinct().collect(Collectors.toList());
		ItemList<String> item = new ItemList<>();
		item.setItems(items);
		return item;
	}

	public ItemList<String> getLabelsByNamespace(String namespaceName) throws ZcpException {
		V1Namespace namespace = getNamespace(namespaceName);
		Map<String, String> labels = namespace.getMetadata().getLabels();
		if (labels != null && !labels.isEmpty()) {
			ItemList<String> labelList = new ItemList<>();
			labelList.setItems(Util.MapToList(namespace.getMetadata().getLabels()));
			return labelList;
		}

		return null;
	}

	private double getUsedMemoryRate(String used, String hard) {
		int iUsed = 0;
		int iHard = 0;
		if (used != null)
			if (used.indexOf("Gi") > -1) {
				iUsed = Integer.parseInt(used.replace("Gi", ""));
				iUsed *= 1000;
			} else if (used.indexOf("Mi") > -1) {
				iUsed = Integer.parseInt(used.replace("Mi", ""));
			} else
				iUsed = Integer.parseInt(used);

		if (hard != null)
			if (hard.indexOf("Gi") > -1) {
				iHard = Integer.parseInt(hard.replace("Gi", ""));
				iHard *= 1000;
			} else if (hard.indexOf("Mi") > -1) {
				iHard = Integer.parseInt(hard.replace("Mi", ""));
			} else {
				iHard = Integer.parseInt(hard);
			}

		return iHard == 0 ? 0 : Math.round((double) iUsed / (double) iHard * 100);
	}

	private double getUsedCpuRate(String used, String hard) {
		int iUsed = 0;
		int iHard = 0;
		if (used != null)
			if (used.indexOf("m") > -1) {
				iUsed = Integer.parseInt(used.replace("m", ""));
			} else {
				iUsed = Integer.parseInt(used.replace("m", ""));
				iUsed *= 1000;
			}
		if (hard != null)
			if (hard.indexOf("m") > -1) {
				iHard = Integer.parseInt(hard.replace("m", ""));
			} else {
				iHard = Integer.parseInt(hard.replace("m", ""));
				iHard *= 1000;
			}
		return iHard == 0 ? 0 : Math.round((double) iUsed / (double) iHard * 100.0);
	}

	private int getNamespaceUserCount(String namespaceName) throws ZcpException {
		V1RoleBindingList list = null;
		try {
			list = kubeRbacAuthzManager.getRoleBindingListByNamespace(namespaceName);
		} catch (ApiException e) {
			throw new ZcpException("N0005", e.getMessage());
		}
		return list.getItems().size();
	}

	public void saveNamespace(NamespaceVO vo) throws ZcpException {

		String namespaceName = vo.getNamespace();

		V1Namespace namespace = new V1Namespace();
		namespace.setApiVersion("v1");
		namespace.setKind("Namespace");
		namespace.setSpec(new V1NamespaceSpec().addFinalizersItem("kubernetes"));
		V1ObjectMeta namespaceMetadata = new V1ObjectMeta();
		namespaceMetadata.setName(vo.getNamespace());
		namespace.setMetadata(namespaceMetadata);
		namespace.getMetadata().setLabels(ResourcesLabelManager.getSystemLabels());

		V1Namespace v1Namespace = null;
		try {
			v1Namespace = kubeCoreManager.getNamespace(namespaceName);
		} catch (ApiException e) {
			// this case is create case
		}

		if (v1Namespace == null) {
			try {
				kubeCoreManager.createNamespace(namespaceName, namespace);
			} catch (ApiException e) {
				e.printStackTrace();
				throw new ZcpException("N002", e.getMessage());
			}
		} else {
			try {
				kubeCoreManager.editNamespace(namespaceName, namespace);
			} catch (ApiException e) {
				e.printStackTrace();
				throw new ZcpException("N002", e.getMessage());
			}
		}

		saveNamespaceResoruceQuota(vo.getResourceQuota(), namespaceName);

		saveNamespaceLimitRange(vo.getLimitRange(), namespaceName);
	}

	private void saveNamespaceLimitRange(V1LimitRange limitRange, String namespaceName) throws ZcpException {
		limitRange.setApiVersion("v1");
		limitRange.setKind("LimitRange");
		V1ObjectMeta limitRangeMetadata = new V1ObjectMeta();
		limitRangeMetadata.setName(ResourcesNameManager.getLimtRangeName(namespaceName));
		limitRangeMetadata.setLabels(ResourcesLabelManager.getSystemNamespaceLabels(namespaceName));
		limitRange.setMetadata(limitRangeMetadata);

		V1LimitRange v1LimitRange = null;
		try {
			v1LimitRange = kubeCoreManager.getLimitRange(namespaceName, limitRangeMetadata.getName());
		} catch (ApiException e1) {
			//
		}

		if (v1LimitRange == null) {
			try {
				kubeCoreManager.createLimitRange(namespaceName, limitRange);
			} catch (ApiException e) {
				e.printStackTrace();
				throw new ZcpException("N002", e.getMessage());
			}
		} else {
			try {
				kubeCoreManager.editLimitRange(namespaceName, limitRangeMetadata.getName(), limitRange);
			} catch (ApiException e) {
				e.printStackTrace();
				throw new ZcpException("N002", e.getMessage());
			}
		}
	}

	private void saveNamespaceResoruceQuota(V1ResourceQuota resourceQuota, String namespaceName) throws ZcpException {
		resourceQuota.setApiVersion("v1");
		resourceQuota.setKind("ResourceQuota");
		V1ObjectMeta resourceQuotaMetadata = new V1ObjectMeta();
		resourceQuotaMetadata.setName(ResourcesNameManager.getResouceQuotaName(namespaceName));
		resourceQuotaMetadata.setLabels(ResourcesLabelManager.getSystemNamespaceLabels(namespaceName));
		resourceQuota.setMetadata(resourceQuotaMetadata);

		V1ResourceQuota v1ResourceQuota = null;
		try {
			v1ResourceQuota = kubeCoreManager.getResourceQuota(namespaceName, resourceQuotaMetadata.getName());
		} catch (ApiException e1) {
			//
		}

		if (v1ResourceQuota == null) {
			try {
				kubeCoreManager.createResourceQuota(namespaceName, resourceQuota);
			} catch (ApiException e) {
				e.printStackTrace();
				throw new ZcpException("N002", e.getMessage());
			}
		} else {
			try {
				kubeCoreManager.editResourceQuota(namespaceName, resourceQuotaMetadata.getName(), resourceQuota);
			} catch (ApiException e) {
				e.printStackTrace();
				throw new ZcpException("N002", e.getMessage());
			}
		}
	}

	public void deleteClusterRoleBinding(KubeDeleteOptionsVO data) throws IOException, ApiException {
		kubeRbacAuthzManager.deleteClusterRoleBinding(data.getName(), data);
	}

	public void createRoleBinding(String namespace, RoleBindingVO vo) throws ZcpException {
		V1RoleBinding roleBinding = makeRoleBinding(namespace, vo);

		try {
			roleBinding = kubeRbacAuthzManager.createRoleBinding(namespace, roleBinding);
		} catch (ApiException e) {
			throw new ZcpException("N0002", e.getMessage());
		}
	}

	public void editRoleBinding(String namespace, RoleBindingVO vo) throws ZcpException {
		V1RoleBinding roleBinding = makeRoleBinding(namespace, vo);

		// 1.delete RoleBinding
		deleteRoleBinding(namespace, vo);

		// 2.create RoleBinding
		try {
			kubeRbacAuthzManager.createRoleBinding(namespace, roleBinding);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("N0003", e.getMessage());
		}
	}

	public void deleteRoleBinding(String namespace, RoleBindingVO data) throws ZcpException {
		V1DeleteOptions deleteOptions = new V1DeleteOptions();
		deleteOptions.setGracePeriodSeconds(0L);
		try {
			kubeRbacAuthzManager.deleteRoleBinding(namespace,
					ResourcesNameManager.getRoleBindingName(data.getUsername()), deleteOptions);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("N0002", e.getMessage());
		}

	}

	private V1RoleBinding makeRoleBinding(String namespace, RoleBindingVO vo) {
		String username = vo.getUsername();
		String serviceAccountName = ResourcesNameManager.getServiceAccountName(username);
		String roleBindingName = ResourcesNameManager.getRoleBindingName(username);
		Map<String, String> labels = ResourcesLabelManager.getSystemUsernameLabels(username);

		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(roleBindingName);
		metadata.setLabels(labels);
		metadata.setNamespace(namespace);

		V1RoleRef roleRef = new V1RoleRef();
		roleRef.setApiGroup("rbac.authorization.k8s.io");
		roleRef.setKind("ClusterRole");
		roleRef.setName(vo.getClusterRole().getRole());

		V1Subject subject = new V1Subject();
		subject.setKind("ServiceAccount");
		subject.setName(serviceAccountName);
		subject.setNamespace(systemNamespace);

		List<V1Subject> subjects = new ArrayList<V1Subject>();
		subjects.add(subject);

		V1RoleBinding roleBinding = new V1RoleBinding();
		// roleBinding.setApiVersion("rbac.authorization.k8s.io/v1");
		roleBinding.setMetadata(metadata);
		roleBinding.setRoleRef(roleRef);
		roleBinding.setSubjects(subjects);

		return roleBinding;
	}

	public void deleteNamespace(String namespace) throws IOException, ApiException {
		try {
			kubeCoreManager.deleteNamespace(namespace, new V1DeleteOptions());
		} catch (ApiException e) {
			throw e;
		}
	}

	public void createAndEditServiceAccount(String name, String namespace, ServiceAccountVO vo) throws ApiException {
		try {
			kubeCoreManager.createServiceAccount(vo.getNamespace(), vo);
		} catch (ApiException e) {
			if (e.getMessage().equals("Conflict")) {
				kubeCoreManager.editServiceAccount(name, vo.getNamespace(), vo);
			} else {
				throw e;
			}
		}
	}

	public void createAndEditClusterRoleBinding(String username, V1ClusterRoleBinding clusterRoleBinding)
			throws ApiException {
		try {
			kubeRbacAuthzManager.createClusterRoleBinding(clusterRoleBinding);
		} catch (ApiException e) {
			if (e.getMessage().equals("Conflict")) {
				kubeRbacAuthzManager.editClusterRoleBinding(clusterRoleBinding.getMetadata().getName(),
						clusterRoleBinding);
			} else {
				throw e;
			}
		}
	}

	public UserList getUserListByNamespace(String namespace) throws ZcpException {
		V1RoleBindingList rolebindingList = null;
		try {
			rolebindingList = kubeRbacAuthzManager.getRoleBindingListByNamespace(namespace);
		} catch (ApiException e) {
			throw new ZcpException("N0005", e.getMessage());
		}
		List<V1RoleBinding> rolebindings = rolebindingList.getItems();
		List<UserRepresentation> keycloakUsers = keyCloakManager.getUserList();
		List<ZcpUser> zcpUsers = new ArrayList<ZcpUser>();

		for (V1RoleBinding rolebinding : rolebindings) {
			String rolebindingName = rolebinding.getMetadata().getName();
			for (UserRepresentation keycloakUser : keycloakUsers) {
				if (rolebindingName.equals(ResourcesNameManager.getRoleBindingName(keycloakUser.getUsername()))) {
					ZcpUser user = new ZcpUser();
					user.setId(keycloakUser.getId());
					user.setUsername(keycloakUser.getUsername());
					user.setEmail(keycloakUser.getEmail());
					user.setLastName(keycloakUser.getLastName());
					user.setFirstName(keycloakUser.getFirstName());
					user.setCreatedDate(new Date(keycloakUser.getCreatedTimestamp()));
					user.setEnabled(keycloakUser.isEnabled());
					user.setNamespacedRole(ClusterRole.getClusterRole(rolebinding.getRoleRef().getName()));

					zcpUsers.add(user);
				}
			}
		}

		UserList userlist = new UserList();
		userlist.setItems(zcpUsers);

		return userlist;

	}

	public void deleteNamespaceLabel(String namespaceName, String label) throws ZcpException {
		V1Namespace namespace = getNamespace(namespaceName);
		Map<String, String> labels = namespace.getMetadata().getLabels();
		namespace.getMetadata().setLabels(removeLabel(labels, label));

		try {
			kubeCoreManager.replaceNamespace(namespaceName, namespace);
		} catch (ApiException e) {
			throw new ZcpException("N0009", e.getMessage());
		}

	}

	private Map<String, String> removeLabel(Map<String, String> labels, String label) {
		if (labels == null || labels.isEmpty()) {
			return labels;
		}

		if (StringUtils.isEmpty(label)) {
			return labels;
		}

		String[] map = label.split("=");
		if (map == null || map.length != 2) {
			log.debug("label is invalid - {}", label);
			return labels;
		}

		String key = map[0];
		String value = map[1];

		if (StringUtils.equals(value, labels.get(key))) {
			labels.remove(key);
		}

		return labels;
	}

	public void createNamespaceLabel(String namespaceName, String newLabel) throws ZcpException {
		V1Namespace namespace = getNamespace(namespaceName);
		Map<String, String> labels = namespace.getMetadata().getLabels();
		namespace.getMetadata().setLabels(addLabel(labels, newLabel));

		try {
			kubeCoreManager.replaceNamespace(namespaceName, namespace);
		} catch (ApiException e) {
			throw new ZcpException("N0009", e.getMessage());
		}

	}

	private Map<String, String> addLabel(Map<String, String> labels, String newLabel) {
		if (StringUtils.isEmpty(newLabel)) {
			return labels;
		}

		String[] map = newLabel.split("=");
		if (map == null || map.length != 2) {
			log.debug("label is invalid - {}", newLabel);
			return labels;
		}

		String key = map[0];
		String value = map[1];

		if (labels == null) {
			labels = new HashMap<>();
		}

		labels.put(key, value);

		return labels;
	}

	public Object deserialize(String jsonStr, Class<?> targetClass) {
		Object obj = (new Gson()).fromJson(jsonStr, targetClass);
		return obj;
	}
	//// 테스트
}
