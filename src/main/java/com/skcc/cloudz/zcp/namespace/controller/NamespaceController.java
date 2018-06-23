package com.skcc.cloudz.zcp.namespace.controller;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.skcc.cloudz.zcp.common.annotation.NullProperty;
import com.skcc.cloudz.zcp.common.exception.ZcpException;
import com.skcc.cloudz.zcp.common.model.UserList;
import com.skcc.cloudz.zcp.common.util.ValidUtil;
import com.skcc.cloudz.zcp.common.vo.Response;
import com.skcc.cloudz.zcp.namespace.service.NamespaceService;
import com.skcc.cloudz.zcp.namespace.vo.EnquryNamespaceVO;
import com.skcc.cloudz.zcp.namespace.vo.KubeDeleteOptionsVO;
import com.skcc.cloudz.zcp.namespace.vo.NamespaceVO;
import com.skcc.cloudz.zcp.namespace.vo.QuotaVO;
import com.skcc.cloudz.zcp.namespace.vo.ItemList;
import com.skcc.cloudz.zcp.namespace.vo.RoleBindingVO;
import com.skcc.cloudz.zcp.user.service.UserService;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1NamespaceList;
@Configuration
@RestController
@RequestMapping("/iam")
public class NamespaceController {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(NamespaceController.class);    
	
	@Autowired
	NamespaceService namespaceSvc;
	
	@Autowired
	UserService userSvc;
	
	
	@RequestMapping(value="/namespaces", method=RequestMethod.GET)
	@NullProperty(field= {"items.metadata.creationTimestamp", "items.spec"})
	Response<V1NamespaceList> getNamespaces(HttpServletRequest httpServletRequest) throws  ApiException, ParseException{
		Response<V1NamespaceList> vo = new Response<V1NamespaceList>();
		vo.setData(namespaceSvc.getNamespaceList());	
		
		return vo;
	}
	
	
	/**
	 * namespace info
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace/{namespace}", method=RequestMethod.GET)
	@NullProperty(field= {"metadata.creationTimestamp", "spec"})
	Response<V1Namespace> getNamespace(HttpServletRequest httpServletRequest, @PathVariable("namespace") String namespace) throws  ApiException, ParseException{
		Response<V1Namespace> vo = new Response<V1Namespace>();
		vo.setData(namespaceSvc.getNamespace(namespace));	
		
		return vo;
	}
	
	/**
	 * all user list by namespace
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace/{namespace}/users", method=RequestMethod.GET)
	Response<UserList> userListOfNamespace(HttpServletRequest httpServletRequest
			, @PathVariable("namespace") String namespace) throws ApiException{
		Response<UserList> vo = new Response<UserList>();
		vo.setData(userSvc.getUserListByNamespace(namespace));
		return vo;
	}
	
	/**
	 * resource info by namespace
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace/{namespace}/resource", method=RequestMethod.GET)
	Response<NamespaceVO> getNamespaceResource(HttpServletRequest httpServletRequest
			, @PathVariable("namespace") String namespace) throws  ApiException, ParseException{
		Response<NamespaceVO> vo = new Response<NamespaceVO>();
		vo.setData(namespaceSvc.getNamespaceResource(namespace));	
		return vo;
	}
	
	
	/**
	 * resource quota info
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/resourceQuota", method=RequestMethod.GET)
	Response<ItemList<QuotaVO>> getResourceQuota(HttpServletRequest httpServletRequest, @ModelAttribute EnquryNamespaceVO enquery) throws  ApiException, ParseException{
		Response<ItemList<QuotaVO>> vo = new Response<>();
		vo.setData(namespaceSvc.getResourceQuota(enquery));	
		return vo;
	}
	
	
	/**
	 * create Namespace Label
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 * @throws ZcpException 
	 */
	@RequestMapping(value="/namespace/{namespace}/label", method=RequestMethod.POST)
	Response<Object> createNamespaceLabel(HttpServletRequest httpServletRequest
			, @PathVariable("namespace") String namespace
			, HashMap<String, String> label) throws  ApiException, ParseException, ZcpException{
		namespaceSvc.createNamespaceLabel(namespace, label);	
		return new Response<Object>();
	}
	
	
	/**
	 * all Namespace Label
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 * @throws ZcpException 
	 */
	@RequestMapping(value="/namespace/labels", method=RequestMethod.GET)
	Response<ItemList<String>> getNamespaceLabel(HttpServletRequest httpServletRequest) throws  ApiException, ParseException, ZcpException{
		Response<ItemList<String>> vo = new Response<ItemList<String>>();
		vo.setData(namespaceSvc.getLabelsOfNamespaces());
		return vo;
	}
	
	/**
	 * all namespace resource info
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/resource", method=RequestMethod.GET)
	Response<NamespaceVO> getAllOfNamespaceResource(HttpServletRequest httpServletRequest) throws  ApiException, ParseException{
		Response<NamespaceVO> vo = new Response<NamespaceVO>();
		vo.setData(namespaceSvc.getNamespaceResource(""));
		return vo;
	}
	
	/**
	 * namespace name only
	 * @param httpServletRequest
	 * @param map
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
//	@RequestMapping(value="/namespace/onlyNames", method=RequestMethod.GET)
//	@Deprecated
//	Response<List<Map>> getAllOfNamespace(HttpServletRequest httpServletRequest) throws  ApiException, ParseException{
//		Response<List<Map>> vo = new Response();
//		vo.setData(namespaceSvc.getAllOfNamespace());	
//		return vo;
//	}
	
	
	/**
	 * create namespace
	 * 
	 * @param httpServletRequest
	 * @param data
	 * @return
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace", method=RequestMethod.POST)
	Response<Object> addNamespace(HttpServletRequest httpServletRequest, @RequestBody NamespaceVO data) throws ApiException {
		Response<Object> vo = new Response<Object>();
		namespaceSvc.createAndEditNamespace(data);
		return vo;
	}
	
	
	
	
	/**
	 * each user namespace and rolebinding
	 * @param httpServletRequest
	 * @param data
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace/{namespace}/roleBinding", method=RequestMethod.POST)
	Response<Object> createRoleBinding(HttpServletRequest httpServletRequest 
			, @PathVariable("namespace") String namespace
			, @RequestBody RoleBindingVO data) throws IOException, ApiException{
		Response<Object> vo = new Response<Object>();
		String msg = ValidUtil.required(data,  "userName", "clusterRole");
		if(msg != null) {
			vo.setMsg(msg);
			vo.setCode("500");
		}
		else {
			data.setNamespace(namespace);
			namespaceSvc.createRoleBinding(data);	
		}
		return vo;
		
	}
	
	
	/**
	 * each user namespace and rolebinding
	 * @param httpServletRequest
	 * @param data
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace/{namespace}/roleBinding", method=RequestMethod.PUT)
	Response<Object> editRoleBinding(HttpServletRequest httpServletRequest 
			, @PathVariable("namespace") String namespace
			, @RequestBody RoleBindingVO data) throws IOException, ApiException{
		Response<Object> vo = new Response<Object>();
		String msg = ValidUtil.required(data,  "userName", "clusterRole");
		if(msg != null) {
			vo.setMsg(msg);
			vo.setCode("500");
		}
		else {
			data.setNamespace(namespace);
			namespaceSvc.editRoleBinding(data);	
		}
		return vo;
		
	}
	


	/**
	 * delete rolebinding by namespace
	 * 
	 * @param httpServletRequest
	 * @param data
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace/{namespace}/roleBinding", method=RequestMethod.DELETE)
	Response<Object> deleteRoleBinding(HttpServletRequest httpServletRequest
			, @PathVariable("namespace") String namespace
			, @RequestBody KubeDeleteOptionsVO data) throws IOException, ApiException{
		Response<Object> vo = new Response<Object>();
		String msg = ValidUtil.required(data,  "userName");
		if(msg != null) {
			vo.setMsg(msg);
			vo.setCode("500");
		}
		else {
			data.setNamespace(namespace);
			namespaceSvc.deleteRoleBinding(data);	
		}
		return vo;
	}
	
	
	/**
	 * delete rolebinding by namespace
	 * 
	 * @param httpServletRequest
	 * @param data
	 * @return
	 * @throws IOException
	 * @throws ApiException
	 */
	@RequestMapping(value="/namespace/{namespace}", method=RequestMethod.DELETE)
	Response<Object> deleteNamespace(HttpServletRequest httpServletRequest
			, @PathVariable("namespace") String namespace) throws IOException, ApiException{
		Response<Object> vo = new Response<Object>();
		namespaceSvc.deleteNamespace(namespace);	
		return vo;
	}
	
	
}
