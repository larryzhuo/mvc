package com.mvc.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mvc.core.anno.Controller;
import com.mvc.core.anno.RequestMapping;
import com.mvc.core.anno.RequestParam;

public class DispatchServlet extends HttpServlet {
	private static final long serialVersionUID = -7036071090053544081L;
	private static final Logger log = LoggerFactory.getLogger("core");
	private Properties prop;
	private Map<String, Object> controllerInsMap = new ConcurrentHashMap<>();	//key:全类名  	   val:controller实例
	private Map<String, String> handle2InsMap = new ConcurrentHashMap<>();		//key:请求uri   val:全类名
	private Map<String, Method> handleMap = new ConcurrentHashMap<>();			//key:请求uri   val:对应方法
	private static String projUrl;
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		boolean loadConfigRet = this.loadConfig(config);
		if(!loadConfigRet) {
			log.warn("loadConfigRet false");
			return;
		}
		
		String controllDir = prop.getProperty("controllDir");
		if(StringUtils.isEmpty(controllDir)) {
			log.warn("controllDir null");
			return;
		}
		projUrl = prop.getProperty("projUrl");
		
		this.scanControllers(controllDir);
		if(controllerInsMap.size() <= 0) {
			log.warn("controllerMap size = 0");
			return;
		}
		
		this.initHandleMap();
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String requestURI = req.getRequestURI();
		log.debug("service, {}", requestURI);
		if(!handleMap.containsKey(requestURI)) {
			log.debug("404");
			resp.setStatus(404);
			return;
		}

		Method method = handleMap.get(requestURI);
		Map<String, String[]> parameterMap = req.getParameterMap();
		
		Object[] args = new Object[method.getParameterCount()];
		Parameter[] parameters = method.getParameters();
		Class<?>[] parameterTypes = method.getParameterTypes();
		for(int i=0,ilen=parameterTypes.length; i<ilen; i++) {
			Class<?> parameterType = parameterTypes[i];
			String typeName = parameterType.getSimpleName();
			log.debug("{},{}", typeName, parameters[i].getName());
			
			if("HttpServletRequest".equals(typeName)) {
				args[i] = req;
				continue;
			}
			else if("HttpServletResponse".equals(typeName)) {
				args[i] = resp;
			}
			else {
//				String pname = parameters[i].getName(); //如果没有开启带参数名编译，名称会被混淆拿不到
				Parameter parameter = parameters[i];
				if(parameter.isAnnotationPresent(RequestParam.class)) {
					RequestParam annotation = parameter.getAnnotation(RequestParam.class);
					String pname = annotation.value();
					log.debug("pname={}, require={}", pname, annotation.require());
					
					String[] strings = parameterMap.get(pname);
					log.debug("key={}, value={}", pname, strings);
					args[i] = StringUtils.join(strings, ",");
				}
			}
		}
		
		try {
			String clsName = this.handle2InsMap.get(requestURI);
			if(StringUtils.isNotEmpty(clsName)) {
				Object objIns = controllerInsMap.get(clsName);
				if(objIns != null) {
					Object ret = method.invoke(objIns, args);
					if(ret != null) {				
						resp.getWriter().write(ret.toString());
						return;
					}				
				}
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			log.error("method invoke:", e);
		}
		resp.getWriter().write("null");
	}
	
	/**
	 * 读取 application.properties文件
	 * @param config
	 * @return
	 */
	private boolean loadConfig(ServletConfig config) {
		String contextConfigLocation = config.getInitParameter("contextConfigLocation");
		if(StringUtils.isEmpty(contextConfigLocation)) {
			contextConfigLocation = "application.properties";
		}
		
		InputStream resourceAsStream = this.getClass().getResourceAsStream("/" + contextConfigLocation);
		if(resourceAsStream == null) {
			log.warn("resourceAsStream == null");
			return false;
		}
		
		prop = new Properties();
		try {
			prop.load(resourceAsStream);
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * 根据配置路径扫描出所有controller
	 * @param controllerDir
	 */
	private void scanControllers(String controllerDir) {
		String formatDir = "/" + controllerDir.replaceAll("\\.", "/");
		log.debug("formatDir:{}", formatDir);
		URL res = this.getClass().getResource(formatDir);
		if(res == null) {
			log.warn("scanControllers getResource null, {}", formatDir);
			return;
		}
		
		File dir = new File(res.getFile());
		File[] listFiles = dir.listFiles();
		for(File file : listFiles) {
			if(file.isDirectory()) {
				this.scanControllers(file.getPath());
			} else {		//文件，直接放进
				loadControllerByName(file.getName(), formatDir);
			}	
		}
	}
	
	private void loadControllerByName(String name, String dir) {
		String pointName = (dir +"/"+ name).substring(1).replaceAll("/", ".").replace(".class", "");
		log.debug("loadControllerByName,{},{},{}", dir, name, pointName);
		try {
			Class<?> cls = Class.forName(pointName);
			if(cls == null) {
				log.warn("loadControllerByName cls null, {}", name);
				return;
			}
			
			if(cls.isAnnotationPresent(Controller.class)) {
				log.debug("name: {},{}", cls.getName(), cls.getSimpleName());
				String clsName = cls.getName();
				controllerInsMap.put(clsName, cls.newInstance());
			}
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		log.debug("controllerMap:{}", controllerInsMap);
	}
	
	/**
	 * 根据request mapping注解解析出 请求url和方法
	 */
	private void initHandleMap() {
		if(this.controllerInsMap.size() <= 0) {
			return;
		}
		
		for(Entry<String, Object> controllerObj : this.controllerInsMap.entrySet()) {
			String clsName = controllerObj.getKey();
			Class<? extends Object> controllerCls = controllerObj.getValue().getClass();
			
			String baseurl = "";
			if(controllerCls.isAnnotationPresent(RequestMapping.class)) {		//controller类上的request maping
				RequestMapping annotation = controllerCls.getAnnotation(RequestMapping.class);
				baseurl += annotation.value();
			}

			Method[] methods = controllerCls.getDeclaredMethods();
			for(Method method : methods) {
				if(method.isAnnotationPresent(RequestMapping.class)) {
					RequestMapping annotation = method.getAnnotation(RequestMapping.class);
					String turl = annotation.value();
					if(!turl.startsWith("/")) {
						turl = "/" + turl;
					}
					String url = projUrl + baseurl + turl;
					
					handle2InsMap.put(url, clsName);
					handleMap.put(url, method);
				}
			}
		}
		log.debug("handleMap: {}", handleMap);
	}
}
