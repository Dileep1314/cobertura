package groovy.CORE

import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.framework.constants.RequestSegment
import com.metlife.service.entity.GSSPEntityService

import groovy.US.POST_BILLING_ACTUAL_DATA
import net.minidev.json.parser.JSONParser
import spock.lang.Specification

/**
 *
 * @author Chiranjeevi
 */

class POST_BILLING_ACTUAL_DATA_spec extends Specification {
	
	def "postBillingActualdataSuccess"(){

		given:
			def domain = new WorkflowDomain()
			
			def responseEntity = new ResponseEntity(getTest1Data("selfAdminBillData.json"), HttpStatus.OK)
			
			def entityResult = new EntityResult(responseEntity.getBody())
			
			
			def entityService = [updateByQuery: {String arg0, Map<String, Object> arg1, Map<String, Object> arg2 -> entityResult}
				] as GSSPEntityService
			
			def config = [get: {String configurationId, String tenantId, Map criteria  -> entityResult}] as GSSPConfiguration
			
			
			def context = [getBean: {String beanName, Class responseBodyType -> beanName=='GSSPConfiguration'? config : entityService},
					getEnvironment:{[getProperty:{String value -> "string"}] as Environment}] as ApplicationContext
			
				
			domain.applicationContext = context
			
			domain.addFacts("request", [(RequestSegment.PathParams.name()):[version:'v1', contract:'tenants', tenantId:'SMD',
									   number:"123456789-12345",accountNumber:"12345",viewed:'false'], \
										(RequestSegment.Body.name()):getTest1Data("selfAdminBillData.json"), tenantId:'SMD'])
		when:
		
			POST_BILLING_ACTUAL_DATA.newInstance().execute(domain)
			
		then:
			def response = domain.getServiceResponse()
			assert response != null
			assert response.getStatus() == HttpStatus.OK
			
		
	}
	
	def "postBillingActualdataException"(){
		
				given:
					def domain = new WorkflowDomain()
					
					def responseEntity = new ResponseEntity(getTest1Data("selfAdminBillData.json"), HttpStatus.OK)
					
					def entityResult = new EntityResult(responseEntity.getBody())
					
					
					def entityService = [create: {String collectionName, Map m1 -> entityResult},
						updateByQuery: {String arg0, Map<String, Object> arg1, Map<String, Object> arg2 -> void}
						] as GSSPEntityService
					
					def registerdServiceInvoker = [postViaSPI: {String serviceUri, Class clazz, Map<String, Object> params-> void}] as RegisteredServiceInvoker
					
					def context = [getBean: {String beanName, Class responseBodyType -> beanName=='registerdServiceInvoker'? registerdServiceInvoker : entityService},
							getEnvironment:{[getProperty:{String value -> "string"}] as Environment}] as ApplicationContext
					
						
					domain.applicationContext = context
					
					domain.addFacts("request", [(RequestSegment.PathParams.name()):[version:'v1', contract:'tenants', tenantId:'SMD',
											   number:"123456789-12345",accountNumber:"12345",viewed:'false'], \
												(RequestSegment.Body.name()):[:], tenantId:'SMD'])
				when:
					POST_BILLING_ACTUAL_DATA.newInstance().execute(domain)
					
				then:
				
				Exception e = thrown()
			}
			
			def "postBillingActualdataFailure"(){
				
						given:
							def domain = new WorkflowDomain()
							
							def responseEntity = new ResponseEntity(getTest1Data("selfAdminBillData.json"), HttpStatus.OK)
							
							def entityResult = new EntityResult(responseEntity.getBody())
							
							
							def entityService = [create: {String collectionName, Map m1 -> entityResult},
								updateByQuery: {String arg0, Map<String, Object> arg1, Map<String, Object> arg2 -> void}
								] as GSSPEntityService
							
							def registerdServiceInvoker = [postViaSPI: {String serviceUri, Class clazz, Map<String, Object> params->responseEntity}] as RegisteredServiceInvoker
							
							def context = [getBean: {String beanName, Class responseBodyType -> beanName=='registerdServiceInvoker'? registerdServiceInvoker : entityService},
									getEnvironment:{[getProperty:{String value -> "string"}] as Environment}] as ApplicationContext
							
								
							domain.applicationContext = context
							
							domain.addFacts("request", [(RequestSegment.PathParams.name()):[version:'v1', contract:'tenants', tenantId:'SMD',
													   number:"123456789-12345",accountNumber:"12345",viewed:'false'], \
														(RequestSegment.Body.name()):getTest1Data("selfAdminBillData.json"), tenantId:'SMD'])
						when:
							POST_BILLING_ACTUAL_DATA.newInstance().execute(domain)
							
						then:
						
						Exception e = thrown()
			}
	
	private Map<String, Object> getTest1Data(String fileName) {
		JSONParser parser = new JSONParser();
		Map<String, Object> jsonObj = null;
		try {
			String workingDir = System.getProperty("user.dir");
			Object obj = parser.parse(new FileReader(workingDir +
					"/src/test/data/"+fileName));
			jsonObj = (HashMap<String, Object>) obj;
			return jsonObj;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
