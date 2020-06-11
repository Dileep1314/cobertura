package groovy.CORE

import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.http.HttpStatus

import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.excel.impl.ExcelGenerationImpl
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.framework.constants.RequestSegment
import com.metlife.service.entity.GSSPEntityService

import groovy.US.DOWNLOAD_SELF_ADMIN_VOIDED_ACTUALS
import net.minidev.json.parser.JSONParser
import spock.lang.Specification

class DOWNLOAD_SELF_ADMIN_VOIDED_ACTUALS_Spec extends Specification{
	def "getSelfAdminVoidedActualsExcel"() {
		given:
		def domain = new WorkflowDomain()
		def data=getTestData("smdBillingSelfadminBillProfile")
		def groupData=getTestData("groupDetails")
		List selfAdminlist = Arrays.asList(data)
		def groupDataObj=Arrays.asList(groupData)
		GSSPEntityService entityService= Mock()
		entityService.listByCriteria("selfAdminBillProfile", _ as Criteria ) >> selfAdminlist
		entityService.listByCriteria("billProfile", _ as Criteria) >> groupDataObj
		byte[] contents = new File ("src/test/data/","excelbytecode").bytes
		def excelGeneration = [generateExcel:{excelOutputFileName, xlsBytes -> contents}] as ExcelGenerationImpl
		def productData=getTestData("productDetails")
		def config = [get:{Object obj,Object obj1,Object obj2 -> ["data":productData]}]  as GSSPConfiguration
		def context = [getBean: { String beanName, Class responseBodyType ->
				if(beanName == 'GSSPEntityService')
					entityService
				else if(beanName == 'excelGenerationImpl')
					excelGeneration
				else if(beanName == 'GSSPConfiguration')
					config
			},getEnvironment:{[getProperty:{String anyString -> "src/template/excel/"}] as Environment }] as ApplicationContext
		domain.applicationContext = context

		domain.addFacts("request", [(RequestSegment.PathParams.name()):[tenantId:'SMD',groupNumber:'12345'], \
			(RequestSegment.Header.name()):["content-language":""], \
			tenantId:'SMD',(RequestSegment.Body.name()): ["billNumber":"123456789-12345",groupNumber:'12345']])
		
		
		
		when:
		DOWNLOAD_SELF_ADMIN_VOIDED_ACTUALS.newInstance().execute(domain)

		then:
		def response = domain.getServiceResponse()
		assert response != null
		assert response.getStatus() == HttpStatus.OK
	}

	def "getSelfAdminVoidedActualsExcel Exception"() {
		given:
		def domain = new WorkflowDomain()
		def data=getTestData("smdBillingSelfadminBillProfile")
		def groupData=getTestData("groupDetails")
		List selfAdminlist = Arrays.asList(data)
		def groupDataObj=Arrays.asList(groupData)
		GSSPEntityService entityService= Mock()
		entityService.listByCriteria("selfAdminBillProfile", _ as Criteria ) >> selfAdminlist
		entityService.listByCriteria("billProfile", _ as Criteria) >> groupDataObj
		byte[] contents = new File ("src/test/data/","excelbytecode").bytes
		def excelGeneration = [generateExcel:{excelOutputFileName, xlsBytes -> contents}] as ExcelGenerationImpl
		def productData=getTestData("productDetails")
		def config = [get:{Object obj,Object obj1,Object obj2 -> ["data":productData]}]  as GSSPConfiguration
		def context = [getBean: { String beanName, Class responseBodyType ->
				if(beanName == 'GSSPEntityService')
					entityService
				else if(beanName == 'excelGenerationImpl')
					excelGeneration
				else if(beanName == 'GSSPConfiguration')
					config
			},getEnvironment:{[getProperty:{String anyString -> "src/template/excel/"}] as Environment }] as ApplicationContext
		domain.applicationContext = context

		when:
		DOWNLOAD_SELF_ADMIN_VOIDED_ACTUALS.newInstance().execute(domain)

		then:
		Exception e = thrown()
	}

	def "getSelfAdminVoidedActualsExcel Data Not Found"() {
		given:
		def domain = new WorkflowDomain()
		def data=getTestData("smdBillingSelfadminBillProfile")
		def groupData=getTestData("groupDetails")
		List selfAdminlist = Arrays.asList('')
		def groupDataObj=Arrays.asList('')
		GSSPEntityService entityService= Mock()
		entityService.listByCriteria("selfAdminBillProfile", _ as Criteria ) >> selfAdminlist
		entityService.listByCriteria("billProfile", _ as Criteria) >> groupDataObj
		byte[] contents = new File ("src/test/data/","excelbytecode").bytes
		def excelGeneration = [generateExcel:{excelOutputFileName, xlsBytes -> contents}] as ExcelGenerationImpl
		def productData=getTestData("productDetails")
		def config = [get:{Object obj,Object obj1,Object obj2 -> ["data":productData]}]  as GSSPConfiguration
		def context = [getBean: { String beanName, Class responseBodyType ->
				if(beanName == 'GSSPEntityService')
					entityService
				else if(beanName == 'excelGenerationImpl')
					excelGeneration
				else if(beanName == 'GSSPConfiguration')
					config
			},getEnvironment:{[getProperty:{String anyString -> "src/template/excel/"}] as Environment }] as ApplicationContext
		domain.applicationContext = context

		when:
		DOWNLOAD_SELF_ADMIN_VOIDED_ACTUALS.newInstance().execute(domain)

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
