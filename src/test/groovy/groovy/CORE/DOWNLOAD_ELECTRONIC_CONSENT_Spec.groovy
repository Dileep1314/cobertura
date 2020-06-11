package groovy.CORE

import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus

import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.pdf.PdfGeneration
import com.metlife.gssp.common.pdf.impl.PdfGenerationFactory
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.framework.constants.RequestSegment

import groovy.US.DOWNLOAD_ELECTRONIC_CONSENT

import com.metlife.gssp.common.pdf.PdfGeneration.PdfImplementationTypes

import net.minidev.json.parser.JSONParser
import spock.lang.Specification

class DOWNLOAD_ELECTRONIC_CONSENT_Spec extends Specification{

	def "getDownloadEconsentCalifornia"() {
		given:
		def domain = new WorkflowDomain()
		def data=getTestData("econsentCalifornia")
		byte[] contents = new File ("src/test/data/","econsentCaliforniaByteCodes").bytes
		def pdfGeneration = [generatePdf:{excelOutputFileName, xlsBytes -> contents}] as PdfGeneration
		def pdfGenerationFactory = [getPdfGenerationImplementation:{Object obj -> pdfGeneration}] as PdfGenerationFactory
		def config = [get:{Object obj,Object obj1,Object obj2 -> [data:data]}]  as GSSPConfiguration
		def context = [getBean: { String beanName, Class responseBodyType ->
				if(beanName == 'GSSPConfiguration')
					config
				else if (beanName=='pdfGenerationFactory')
					pdfGenerationFactory
			},getEnvironment:{[getProperty:{String anyString -> "src/template/pdf/"}] as Environment }] as ApplicationContext
		domain.applicationContext = context

		domain.addFacts("request", [(RequestSegment.PathParams.name()):[tenantId:'SMD',groupNumber:'12345'], \
			tenantId:'SMD',(RequestSegment.Body.name()): ["state":"california"]])

		when:
		DOWNLOAD_ELECTRONIC_CONSENT.newInstance().execute(domain)

		then:
		def response = domain.getServiceResponse()
		assert response != null
		assert response.getStatus() == HttpStatus.OK
	}

	def "getDownloadEconsentOtherState"() {
		given:
		def domain = new WorkflowDomain()
		def data=getTestData("econsentOtherState")
		byte[] contents = new File ("src/test/data/","econsentOtherStateByteCodes").bytes
		def pdfGeneration = [generatePdf:{excelOutputFileName, xlsBytes -> contents}] as PdfGeneration
		def pdfGenerationFactory = [getPdfGenerationImplementation:{Object obj -> pdfGeneration}] as PdfGenerationFactory
		def config = [get:{Object obj,Object obj1,Object obj2 -> [data:data]}]  as GSSPConfiguration
		def context = [getBean: { String beanName, Class responseBodyType ->
				if(beanName == 'GSSPConfiguration')
					config
				else if (beanName=='pdfGenerationFactory')
					pdfGenerationFactory
			},getEnvironment:{[getProperty:{String anyString -> "src/template/pdf/"}] as Environment }] as ApplicationContext
		domain.applicationContext = context

		domain.addFacts("request", [(RequestSegment.PathParams.name()):[tenantId:'SMD',groupNumber:'12345'], \
			tenantId:'SMD',(RequestSegment.Body.name()): ["state":"otherState"]])

		when:
		DOWNLOAD_ELECTRONIC_CONSENT.newInstance().execute(domain)

		then:
		def response = domain.getServiceResponse()
		assert response != null
		assert response.getStatus() == HttpStatus.OK
	}

	def "getDownloadEconsentException"() {
		given:
		def domain = new WorkflowDomain()
		def data=[]
		byte[] contents = new File ("src/test/data/","econsentOtherStateByteCodes").bytes
		def pdfGeneration = [generatePdf:{excelOutputFileName, xlsBytes -> contents}] as PdfGeneration
		def pdfGenerationFactory = [getPdfGenerationImplementation:{Object obj -> pdfGeneration}] as PdfGenerationFactory
		def config = [get:{Object obj,Object obj1,Object obj2 -> [data:data]}]  as GSSPConfiguration
		def context = [getBean: { String beanName, Class responseBodyType ->
				if(beanName == 'GSSPConfiguration')
					config
				else if (beanName=='pdfGenerationFactory')
					pdfGenerationFactory
			},getEnvironment:{[getProperty:{String anyString -> "src/template/pdf/"}] as Environment }] as ApplicationContext
		domain.applicationContext = context

		domain.addFacts("request", [(RequestSegment.PathParams.name()):[tenantId:'SMD',groupNumber:'12345'], \
			tenantId:'SMD',(RequestSegment.Body.name()): ["state":"otherState"]])

		when:
		DOWNLOAD_ELECTRONIC_CONSENT.newInstance().execute(domain)

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
