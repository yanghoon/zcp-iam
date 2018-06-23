package com.skcc.cloudz.zcp.user.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.skcc.cloudz.zcp.common.exception.KeyCloakException;
import com.skcc.cloudz.zcp.common.exception.ZcpException;
import com.skcc.cloudz.zcp.common.model.ClusterRole;
import com.skcc.cloudz.zcp.common.model.UserList;
import com.skcc.cloudz.zcp.common.model.ZcpKubeConfig;
import com.skcc.cloudz.zcp.common.model.ZcpKubeConfig.ClusterInfo;
import com.skcc.cloudz.zcp.common.model.ZcpKubeConfig.ClusterInfo.Cluster;
import com.skcc.cloudz.zcp.common.model.ZcpKubeConfig.ContextInfo;
import com.skcc.cloudz.zcp.common.model.ZcpKubeConfig.ContextInfo.Context;
import com.skcc.cloudz.zcp.common.model.ZcpKubeConfig.UserInfo;
import com.skcc.cloudz.zcp.common.model.ZcpKubeConfig.UserInfo.User;
import com.skcc.cloudz.zcp.common.model.ZcpUser;
import com.skcc.cloudz.zcp.manager.KeyCloakManager;
import com.skcc.cloudz.zcp.manager.KubeCoreManager;
import com.skcc.cloudz.zcp.manager.KubeRbacAuthzManager;
import com.skcc.cloudz.zcp.manager.ResourcesLabelManager;
import com.skcc.cloudz.zcp.manager.ResourcesNameManager;
import com.skcc.cloudz.zcp.user.vo.MemberVO;
import com.skcc.cloudz.zcp.user.vo.ResetCredentialVO;
import com.skcc.cloudz.zcp.user.vo.ResetPasswordVO;
import com.skcc.cloudz.zcp.user.vo.UpdateClusterRoleVO;
import com.skcc.cloudz.zcp.user.vo.UpdatePasswordVO;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import io.kubernetes.client.models.V1ClusterRoleBindingList;
import io.kubernetes.client.models.V1ClusterRoleList;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ObjectReference;
import io.kubernetes.client.models.V1RoleBinding;
import io.kubernetes.client.models.V1RoleBindingList;
import io.kubernetes.client.models.V1RoleRef;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1ServiceAccountList;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Subject;

@Service
public class UserService {

	private final Logger logger = LoggerFactory.getLogger(UserService.class);

	@Autowired
	private KeyCloakManager keyCloakManager;

	@Autowired
	private KubeCoreManager kubeCoreManager;

	@Autowired
	private KubeRbacAuthzManager kubeRbacAuthzManager;

	@Value("${zcp.kube.namespace}")
	private String zcpSystemNamespace;

	@Value("${kube.server.apiserver.endpoint}")
	private String kubeApiServerEndpoint;

	public UserList getUserList(String keyword) throws ZcpException {
		List<UserRepresentation> keyCloakUsers = keyCloakManager.getUserList(keyword);

		List<ZcpUser> users = new ArrayList<ZcpUser>();
		for (UserRepresentation cloakUser : keyCloakUsers) {
			ZcpUser user = new ZcpUser();
			user.setId(cloakUser.getId());
			user.setUsername(cloakUser.getUsername());
			user.setEmail(cloakUser.getEmail());
			user.setLastName(cloakUser.getLastName());
			user.setFirstName(cloakUser.getFirstName());
			user.setCreatedDate(new Date(cloakUser.getCreatedTimestamp()));
			user.setEnabled(cloakUser.isEnabled());

			users.add(user);
		}

		Map<String, V1ClusterRoleBinding> mappedClusterRoleBindings = null;
		try {
			mappedClusterRoleBindings = getMappedClusterRoleBindings();
		} catch (ApiException e) {
			throw new ZcpException("ZCP-0001");
		}

		Map<String, List<V1RoleBinding>> mappedRoleBindings = null;
		try {
			mappedRoleBindings = getMappedRoleBindings();
		} catch (ApiException e) {
			throw new ZcpException("ZCP-0001");
		}

		for (ZcpUser user : users) {
			List<V1RoleBinding> userRoleBindins = mappedRoleBindings.get(user.getUsername());
			if (userRoleBindins != null) {
				user.setUsedNamespace(userRoleBindins.size());
			}
			V1ClusterRoleBinding userClusterRoleBinding = mappedClusterRoleBindings.get(user.getUsername());
			if (userClusterRoleBinding != null) {
				user.setClusterRole(ClusterRole.getClusterRole(userClusterRoleBinding.getRoleRef().getName()));
			}
		}

		return new UserList(users);
	}

	private Map<String, List<V1RoleBinding>> getMappedRoleBindings() throws ApiException {
		List<V1RoleBinding> allRoleBindings = kubeRbacAuthzManager.getRoleBindingListAllNamespaces().getItems();
		Map<String, List<V1RoleBinding>> map = new HashMap<>();
		for (V1RoleBinding roleBinding : allRoleBindings) {
			String username = roleBinding.getMetadata().getLabels()
					.get(ResourcesLabelManager.SYSTEM_USERNAME_LABEL_NAME);
			List<V1RoleBinding> userRoleBindings = map.get(username);
			if (userRoleBindings == null) {
				userRoleBindings = new ArrayList<>();
				userRoleBindings.add(roleBinding);
				map.put(username, userRoleBindings);
			} else {
				map.get(username).add(roleBinding);
			}
		}

		return map;
	}

	private Map<String, V1ClusterRoleBinding> getMappedClusterRoleBindings() throws ApiException {
		List<V1ClusterRoleBinding> clusterRoleBindings = kubeRbacAuthzManager.getClusterRoleBindingList().getItems();

		Map<String, V1ClusterRoleBinding> map = new HashMap<>();
		for (V1ClusterRoleBinding clusterRoleBinding : clusterRoleBindings) {
			String username = clusterRoleBinding.getMetadata().getLabels()
					.get(ResourcesLabelManager.SYSTEM_USERNAME_LABEL_NAME);
			map.put(username, clusterRoleBinding);
		}

		return map;
	}

	@Deprecated
	public UserList getUserListByNamespace(String namespace) throws ApiException {

		List<ZcpUser> users = new ArrayList<ZcpUser>();
		V1RoleBindingList rolebindingList = kubeRbacAuthzManager.getRoleBindingListByNamespace(namespace);
		List<V1RoleBinding> rolebindings = rolebindingList.getItems();
		List<UserRepresentation> keyCloakUsers = keyCloakManager.getUserList();

		for (V1RoleBinding binding : rolebindings) {
			String name = binding.getMetadata().getName();
			for (UserRepresentation cloakUser : keyCloakUsers) {
				if (name.equals(ResourcesNameManager.getRoleBindingName(cloakUser.getUsername()))) {
					ZcpUser user = new ZcpUser();
					user.setUsername(cloakUser.getUsername());
					user.setEmail(cloakUser.getEmail());
					user.setLastName(cloakUser.getLastName());
					user.setFirstName(cloakUser.getFirstName());
					user.setCreatedDate(new Date(cloakUser.getCreatedTimestamp()));
					user.setEnabled(cloakUser.isEnabled());

					users.add(user);
				}
			}
		}
		// TODO
		// for (ZcpUser user : users) {
		// V1RoleBindingList mapUser =
		// kubeRbacAuthzManager.getRoleBindingList(user.getUsername());
		// int count = mapUser.getItems().size();
		// user.setUsedNamespace(count);
		// }

		UserList userlist = new UserList();
		userlist.setItems(users);

		return userlist;

	}

	public void createUser(MemberVO user) throws ZcpException {
		// 1. create service account
		V1ServiceAccountList serviceAccountList = null;
		try {
			serviceAccountList = kubeCoreManager.getServiceAccountListByUsername(zcpSystemNamespace,
					user.getUsername());
		} catch (ApiException e) {
			// ignore
		}

		if (serviceAccountList != null) {
			V1Status status = null;
			try {
				status = kubeCoreManager.deleteServiceAccountListByUsername(zcpSystemNamespace, user.getUsername());
			} catch (ApiException e) {
				throw new ZcpException("ZCP-008", e.getMessage());
			}

			logger.debug("The serviceaccounts of user({}) have been removed. {}", user.getUsername(),
					status.getMessage());
		}

		V1ServiceAccount serviceAccount = getServiceAccount(user.getUsername());

		try {
			serviceAccount = kubeCoreManager.createServiceAccount(zcpSystemNamespace, serviceAccount);
		} catch (ApiException e) {
			throw new ZcpException("ZCP-009", e.getMessage());
		}

		// 2. create clusterRolebindinding
		V1ClusterRoleBindingList clusterRoleBindingList = null;
		try {
			clusterRoleBindingList = kubeRbacAuthzManager.getClusterRoleBindingListByUsername(user.getUsername());
		} catch (ApiException e) {
			throw new ZcpException("ZCP-009", e.getMessage());
		}

		if (clusterRoleBindingList != null) {
			V1Status status = null;
			try {
				status = kubeRbacAuthzManager.deleteClusterRoleBindingByUsername(user.getUsername());
			} catch (ApiException e) {
				throw new ZcpException("ZCP-008", e.getMessage());
			}

			logger.debug("The clusterRoleBindings of user({}) have been removed. {}", user.getUsername(),
					status.getMessage());
		}

		V1ClusterRoleBinding clusterRoleBinding = getClusterRoleBinding(user.getUsername(), user.getClusterRole());
		try {
			clusterRoleBinding = kubeRbacAuthzManager.createClusterRoleBinding(clusterRoleBinding);
		} catch (ApiException e) {
			throw new ZcpException("ZCP-009", e.getMessage());
		}

		// 3. create keycloak user
		user.setEnabled(Boolean.TRUE);
		UserRepresentation userRepresentation = getKeyCloakUser(null, user);

		keyCloakManager.createUser(userRepresentation);
	}

	public ZcpUser getUser(String id) throws ZcpException {
		UserRepresentation userRepresentation = null;
		ZcpUser zcpUser = null;
		try {
			userRepresentation = keyCloakManager.getUser(id);
		} catch (KeyCloakException e) {
			throw new ZcpException("ZCP-0001", e.getMessage());
		}

		logger.debug("keyclock user info - {}", userRepresentation);

		zcpUser = convertUser(userRepresentation);
		String username = zcpUser.getUsername();

		V1ClusterRoleBinding userClusterRoleBinding = getClusterRoleBindingByUsername(username);
		if (userClusterRoleBinding == null) {
			// If user registered by himself, the clusterrolebindings may not exist before
			// cluster-admin confirms the user.
			logger.debug("The clusterrolebinding of user({}) does not exist yet", username);
		} else {
			zcpUser.setClusterRole(ClusterRole.getClusterRole(userClusterRoleBinding.getRoleRef().getName()));
		}

		List<V1RoleBinding> userRoleBindings = null;
		try {
			userRoleBindings = kubeRbacAuthzManager.getRoleBindingListByUsername(username).getItems();
		} catch (ApiException e1) {
			throw new ZcpException("ZCP-0001");
		}

		if (userRoleBindings != null && !userRoleBindings.isEmpty()) {
			List<String> userNamespaces = new ArrayList<>();
			for (V1RoleBinding roleBinding : userRoleBindings) {
				userNamespaces.add(roleBinding.getMetadata().getNamespace());
			}

			zcpUser.setNamespaces(userNamespaces);
			zcpUser.setUsedNamespace(userNamespaces.size());
		}

		return zcpUser;

	}

	@SuppressWarnings("deprecation")
	private ZcpUser convertUser(UserRepresentation userRepresentation) {
		ZcpUser user = new ZcpUser();
		user.setId(userRepresentation.getId());
		user.setFirstName(userRepresentation.getFirstName());
		user.setLastName(userRepresentation.getLastName());
		user.setEmail(userRepresentation.getEmail());
		user.setEnabled(userRepresentation.isEnabled());
		user.setUsername(userRepresentation.getUsername());
		user.setCreatedDate(new Date(userRepresentation.getCreatedTimestamp()));
		user.setEmailVerified(userRepresentation.isEmailVerified());
		user.setTotp(userRepresentation.isTotp());
		Map<String, List<String>> attributes = userRepresentation.getAttributes();
		if (attributes != null) {
			List<String> defaultNamespaces = attributes.get(KeyCloakManager.DEFAULT_NAMESPACE_ATTRIBUTE_KEY);
			if (defaultNamespaces != null && !defaultNamespaces.isEmpty()) {
				user.setDefaultNamespace(defaultNamespaces.get(0));
			}
		}

		return user;
	}

	public void updateUser(String id, MemberVO user) throws ZcpException {

		try {
			keyCloakManager.editUser(getKeyCloakUser(id, user));
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	private UserRepresentation getKeyCloakUser(String id, MemberVO user) {
		UserRepresentation userRepresentation = new UserRepresentation();
		userRepresentation.setId(id);
		userRepresentation.setFirstName(user.getFirstName());
		userRepresentation.setLastName(user.getLastName());
		userRepresentation.setEmail(user.getEmail());
		userRepresentation.setUsername(user.getUsername());
		userRepresentation.setEnabled(user.getEnabled());
		if (StringUtils.isNoneEmpty(user.getDefaultNamespace())) {
			List<String> defaultNamespaces = new ArrayList<>();
			defaultNamespaces.add(user.getDefaultNamespace());

			Map<String, List<String>> attributes = new HashMap<>();
			attributes.put(KeyCloakManager.DEFAULT_NAMESPACE_ATTRIBUTE_KEY, defaultNamespaces);

			userRepresentation.setAttributes(attributes);
		}

		return userRepresentation;
	}

	private V1ServiceAccount getServiceAccount(String username) {
		String serviceAccountName = ResourcesNameManager.getServiceAccountName(username);
		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(serviceAccountName);
		metadata.setNamespace(zcpSystemNamespace);
		metadata.setLabels(ResourcesLabelManager.getSystemUsernameLabels(username));

		V1ServiceAccount serviceAccount = new V1ServiceAccount();
		serviceAccount.setMetadata(metadata);

		return serviceAccount;
	}

	private V1ClusterRoleBinding getClusterRoleBinding(String username, ClusterRole clusterRole) {
		String serviceAccountName = ResourcesNameManager.getServiceAccountName(username);
		String clusterRoleBindingName = ResourcesNameManager.getClusterRoleBindingName(username);

		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(clusterRoleBindingName);
		metadata.setNamespace(zcpSystemNamespace);
		metadata.setLabels(ResourcesLabelManager.getSystemUsernameLabels(username));

		V1Subject subject = new V1Subject();
		subject.setKind("ServiceAccount");
		subject.setName(serviceAccountName);
		subject.setNamespace(zcpSystemNamespace);

		List<V1Subject> subjects = new ArrayList<V1Subject>();
		subjects.add(subject);

		V1RoleRef roleRef = new V1RoleRef();
		roleRef.setApiGroup("rbac.authorization.k8s.io");
		roleRef.setKind("ClusterRole");
		roleRef.setName(clusterRole.getRole());

		V1ClusterRoleBinding clusterRoleBinding = new V1ClusterRoleBinding();
		clusterRoleBinding.setMetadata(metadata);
		clusterRoleBinding.setRoleRef(roleRef);
		clusterRoleBinding.setSubjects(subjects);

		return clusterRoleBinding;
	}

	public void updateUserClusterRole(String id, UpdateClusterRoleVO vo) throws ZcpException {
		UserRepresentation userRepresentation = null;
		try {
			userRepresentation = keyCloakManager.getUser(id);
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		String username = userRepresentation.getUsername();

		// should check the service account exist or not
		// if user created by himself, the service account may not exist
		// so should create the service account
		V1ServiceAccountList serviceAccounts = null;
		try {
			serviceAccounts = kubeCoreManager.getServiceAccountListByUsername(zcpSystemNamespace, username);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		if (serviceAccounts == null || serviceAccounts.getItems() == null || serviceAccounts.getItems().isEmpty()) {
			V1ServiceAccount serviceAccount = getServiceAccount(username);
			try {
				kubeCoreManager.createServiceAccount(zcpSystemNamespace, serviceAccount);
			} catch (ApiException e) {
				e.printStackTrace();
				throw new ZcpException("ZCP-000", e.getMessage());
			}
		}

		// delete clusterRolebindinding
		V1ClusterRoleBindingList clusterRoleBindingList = null;
		try {
			clusterRoleBindingList = kubeRbacAuthzManager.getClusterRoleBindingListByUsername(username);
		} catch (ApiException e) {
			throw new ZcpException("ZCP-009", e.getMessage());
		}

		if (clusterRoleBindingList != null) {
			V1Status status = null;
			try {
				status = kubeRbacAuthzManager.deleteClusterRoleBindingByUsername(username);
			} catch (ApiException e) {
				throw new ZcpException("ZCP-008", e.getMessage());
			}

			logger.debug("The clusterRoleBindings of user({}) have been removed. {}", username, status.getMessage());
		}

		// create new clusterRolebindinding
		V1ClusterRoleBinding clusterRoleBinding = getClusterRoleBinding(username, vo.getClusterRole());
		try {
			clusterRoleBinding = kubeRbacAuthzManager.createClusterRoleBinding(clusterRoleBinding);
		} catch (ApiException e) {
			throw new ZcpException("ZCP-009", e.getMessage());
		}
	}

	public void updateUserPassword(String id, UpdatePasswordVO vo) throws ZcpException {
		// TODO check current password
		try {
			keyCloakManager.editUserPassword(id, getCredentialRepresentation(vo.getNewPassword(), Boolean.FALSE));
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	public void resetUserPassword(String id, ResetPasswordVO vo) throws ZcpException {
		try {
			keyCloakManager.editUserPassword(id, getCredentialRepresentation(vo.getPassword(), vo.getTemporary()));
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	private CredentialRepresentation getCredentialRepresentation(String password, Boolean temporary) {
		CredentialRepresentation credentail = new CredentialRepresentation();
		credentail.setType(CredentialRepresentation.PASSWORD);
		credentail.setValue(password);
		credentail.setTemporary(temporary);
		return credentail;
	}

	public void deleteUser(String id) throws ZcpException {
		ZcpUser zcpUser = getUser(id);

		String username = zcpUser.getUsername();

		// delete service account
		try {
			kubeCoreManager.deleteServiceAccountListByUsername(zcpSystemNamespace, username);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		// delete clusterrolebinding
		try {
			kubeRbacAuthzManager.deleteClusterRoleBindingByUsername(username);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		// delete rolebindings
		List<String> userNamespaces = zcpUser.getNamespaces();
		if (userNamespaces != null && !userNamespaces.isEmpty()) {
			for (String namespace : userNamespaces) {
				try {
					kubeRbacAuthzManager.deleteRoleBindingListByUsername(namespace, username);
				} catch (ApiException e) {
					e.printStackTrace();
					throw new ZcpException("ZCP-000", e.getMessage());
				}
			}
		}

		// delete keycloak user
		try {
			keyCloakManager.deleteUser(id);
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	public V1ClusterRoleBinding getClusterRoleBindingByUsername(String username) throws ZcpException {

		List<V1ClusterRoleBinding> userClusterRoleBindings = null;
		try {
			userClusterRoleBindings = kubeRbacAuthzManager.getClusterRoleBindingListByUsername(username).getItems();
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-0004", e.getMessage());
		}

		if (userClusterRoleBindings == null || userClusterRoleBindings.isEmpty()) {
			// If user registered by himself, user's clusterrolebinding does not exist
			// before cluster-admin confirms user.
			return null;
		}

		if (userClusterRoleBindings.size() > 1) {
			throw new ZcpException("ZCP-0002", "The clusterrolebindings of user(" + username + ") should be only one");
		}

		return userClusterRoleBindings.get(0);

	}

	public void resetUserCredentials(String id, ResetCredentialVO resetCredentialVO) throws ZcpException {
		try {
			keyCloakManager.resetUserCredentials(id, resetCredentialVO.getActions());
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	public void deleteOtpPassword(String id) throws ZcpException {
		try {
			keyCloakManager.deleteUserOtpPassword(id);
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	public V1ClusterRoleList clusterRoleList() throws ZcpException {
		try {
			return kubeRbacAuthzManager.getClusterRoleList();
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	public void logout(String id) throws ZcpException {
		try {
			keyCloakManager.logout(id);
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
	}

	public ZcpKubeConfig getKubeConfig(String id, String namespace) throws ZcpException {
		UserRepresentation userRepresentation = null;
		try {
			userRepresentation = keyCloakManager.getUser(id);
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		String username = userRepresentation.getUsername();
		String serviceAccountName = ResourcesNameManager.getServiceAccountName(username);
		V1ServiceAccount serviceAccount = null;
		try {
			serviceAccount = kubeCoreManager.getServiceAccount(zcpSystemNamespace, serviceAccountName);
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		List<V1ObjectReference> secrets = serviceAccount.getSecrets();
		V1ObjectReference objectReference = secrets.get(0);
		V1Secret secret = null;
		try {
			// objectReference.getNamespace() is null, so shoud use zcpSystemNamespace
			secret = kubeCoreManager.getSecret(zcpSystemNamespace, objectReference.getName());
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		String caCrt = new String(Base64.getEncoder().encode(secret.getData().get("ca.crt")));
		String token = new String(secret.getData().get("token"));

		ZcpKubeConfig config = generateZcpKubeConfig(kubeApiServerEndpoint, namespace, userRepresentation.getEmail(),
				caCrt, token);

		return config;
	}

	private ZcpKubeConfig generateZcpKubeConfig(String apiServerEndpoint, String namespace, String email, String caCrt,
			String token) {
		ZcpKubeConfig config = new ZcpKubeConfig();

		ClusterInfo clusterInfo = config.new ClusterInfo();
		Cluster cluster = clusterInfo.new Cluster();
		cluster.setCertificateAuthorityData(caCrt);
		cluster.setServer(apiServerEndpoint);
		clusterInfo.setName("zcp-cluster");
		clusterInfo.setCluster(cluster);

		UserInfo userInfo = config.new UserInfo();
		User user = userInfo.new User();
		user.setToken(token);
		userInfo.setName(email);
		userInfo.setUser(user);

		ContextInfo contextInfo = config.new ContextInfo();
		Context context = contextInfo.new Context();
		context.setCluster(clusterInfo.getName());
		context.setUser(userInfo.getName());
		context.setNamespace(namespace);
		contextInfo.setContext(context);
		contextInfo.setName("zcp-context");

		config.setApiVersion("v1");
		config.setKind("Config");
		config.setCurrentContext(contextInfo.getName());

		config.getClusters().add(clusterInfo);
		config.getUsers().add(userInfo);
		config.getContexts().add(contextInfo);

		return config;
	}

	public void resetUserServiceAccount(String id) throws ZcpException {
		// 1. create service account
		UserRepresentation userRepresentation = null;
		try {
			userRepresentation = keyCloakManager.getUser(id);
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}

		String username = userRepresentation.getUsername();

		V1ServiceAccountList serviceAccountList = null;
		try {
			serviceAccountList = kubeCoreManager.getServiceAccountListByUsername(zcpSystemNamespace, username);
		} catch (ApiException e) {
			// ignore
		}

		if (serviceAccountList != null) {
			V1Status status = null;
			try {
				status = kubeCoreManager.deleteServiceAccountListByUsername(zcpSystemNamespace, username);
			} catch (ApiException e) {
				throw new ZcpException("ZCP-008", e.getMessage());
			}

			logger.debug("The serviceaccounts of user({}) have been removed. {}", username, status.getMessage());
		}

		V1ServiceAccount serviceAccount = getServiceAccount(username);

		try {
			serviceAccount = kubeCoreManager.createServiceAccount(zcpSystemNamespace, serviceAccount);
		} catch (ApiException e) {
			throw new ZcpException("ZCP-009", e.getMessage());
		}

	}

	public void enableOtpPassword(String id) throws ZcpException {
		try {
			keyCloakManager.enableUserOtpPassword(id);
		} catch (KeyCloakException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-008", e.getMessage());
		}

	}

	public V1RoleBindingList getUserRoleBindings(String id) throws ZcpException {
		ZcpUser zcpUser = getUser(id);
		V1RoleBindingList roleBindingList = null;
		try {
			roleBindingList = kubeRbacAuthzManager.getRoleBindingListByUsername(zcpUser.getUsername());
		} catch (ApiException e) {
			e.printStackTrace();
			throw new ZcpException("ZCP-000", e.getMessage());
		}
		
		return roleBindingList;
	}

}
