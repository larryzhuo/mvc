# mvc
A simple java web mvc framework

like spring webmvc, here is a demo:

```
package com.mvc.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mvc.core.anno.Controller;
import com.mvc.core.anno.RequestMapping;
import com.mvc.core.anno.RequestParam;

@Controller
@RequestMapping(value="/demo")
public class DemoController {
	private static final Logger log = LoggerFactory.getLogger(DemoController.class);
	
	@RequestMapping(value="/doreq1")
	public String doReq1(@RequestParam(value="uid")String uid) {
		log.debug("doReq1: {}", uid);
		return uid;
	}
	
	
	@RequestMapping(value="/doreq2")
	public String doReq2() {
		return "default";
	}
	
	public String test() {
		return "this is test";
	}
}

```
