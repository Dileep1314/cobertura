package groovy.CORE

import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.framework.constants.RequestSegment
import com.metlife.service.entity.GSSPEntityService

import groovy.US.GET_BILLING_DETAILS_BY_YEAR
import net.minidev.json.parser.JSONParser
import spock.lang.Specification

/**
 * 
 * @author panchanan
 *
 */
class GET_BILLING_DETAILS_BY_YEAR_Spec extends Specification
{
    def "getBillingDetailsSuccess"()
    {
        given:
		def domain = new WorkflowDomain()
		
		def responseEntityFromSPI = new ResponseEntity(getTestData("actualDetailsFromSPI"),HttpStatus.OK)
		def registeredServiceInvoker =[getViaSPI: {String serviceVip, Class clazz,Map<String,Object> requestParams-> responseEntityFromSPI}]as RegisteredServiceInvoker
		
		def selfAdminMappings = ["data" : getTestData("selfAdminMappings")]
		def config = [get: {String configurationId, String tenantId, Map criteria  -> selfAdminMappings}] as GSSPConfiguration
		 
		def context = [getBean: {String beanName, Class responseBodyType -> beanName=='GSSPConfiguration'? config : responseEntityFromSPI}, getEnvironment:{[getProperty:{String anyString -> "string"}] as Environment }] as ApplicationContext
		domain.applicationContext = context
		domain.addFacts("request", [(RequestSegment.PathParams.name()):[version:'v1', contract:'tenants', tenantId:'SMD', groupNumber:'12345', viewed:'false'],
		 (RequestSegment.Body.name()):[:],(RequestSegment.RequestParams.name()):['q':'billNumber==123456789-12345;versionNumber==0'], tenantId:'SMD'])
		
                                        
        when:
        GET_BILLING_DETAILS_BY_YEAR.newInstance().execute(domain)

		
        then:
        def response = domain.getServiceResponse()
        assert response != null
        assert response.getStatus() == HttpStatus.OK    
    }
    
	def "getBillingDetailsFailure"()
	{
		given:
		def domain = new WorkflowDomain()
		
		def responseEntityFromSPI = new ResponseEntity(getTestData("actualDetailsFromSPI"),HttpStatus.OK)
		def registeredServiceInvoker =[getViaSPI: {String serviceVip, Class clazz,Map<String,Object> requestParams-> responseEntityFromSPI}]as RegisteredServiceInvoker
		
		def selfAdminMappings = ["data" : getTestData("selfAdminMappings")]
		def config = [get: {String configurationId, String tenantId, Map criteria  -> selfAdminMappings}] as GSSPConfiguration
		 
		def context = [getBean: {String beanName, Class responseBodyType -> beanName=='GSSPConfiguration'? config : responseEntityFromSPI}, getEnvironment:{[getProperty:{String anyString -> "string"}] as Environment }] as ApplicationContext
		domain.applicationContext = context
		domain.addFacts("request", [(RequestSegment.PathParams.name()):[version:'v1', contract:'tenants', tenantId:'SMD', groupNumber:'12345', viewed:'false'],
		 (RequestSegment.Body.name()):[:],(RequestSegment.RequestParams.name()):['q':'billNumber==123456789-12345;versionNumber=='], tenantId:'SMD'])
        
		                                
        when:
        GET_BILLING_DETAILS_BY_YEAR.newInstance().execute(domain)

		
		then:
		 def response = domain.getServiceResponse()
		 assert response.getProperties().getAt('responseEntity') == null
	}
    
	
	def "getBillingDetailsException"()
	{
		given:
		def domain = new WorkflowDomain()
		
		def responseEntityFromSPI = new ResponseEntity(getTestData("actualDetailsFromSPI"),HttpStatus.OK)
		def registeredServiceInvoker =[getViaSPI: {String serviceVip, Class clazz,Map<String,Object> requestParams-> responseEntityFromSPI}]as RegisteredServiceInvoker
		
		def selfAdminMappings = ["data" : getTestData("selfAdminMappings")]
		def config = [get: {String configurationId, String tenantId, Map criteria  -> selfAdminMappings}] as GSSPConfiguration
		 
		def context = [getBean: {String beanName, Class responseBodyType -> beanName=='GSSPConfiguration'? config : responseEntityFromSPI}, getEnvironment:{[getProperty:{String anyString -> "string"}] as Environment }] as ApplicationContext
		domain.applicationContext = context
		domain.addFacts("request", [(RequestSegment.PathParams.name()):[version:'v1', contract:'tenants', tenantId:'SMD', groupNumber:'12345', viewed:'false'],
		 (RequestSegment.Body.name()):[:],(RequestSegment.RequestParams.name()):['q':'billNumber==12345'], tenantId:'SMD'])

		        
        when:
        GET_BILLING_DETAILS_BY_YEAR.newInstance().execute(domain)
		
		
		then:
		   Exception e = thrown()
	}
    private Map<String, Object> getTestData(String fileName) {
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