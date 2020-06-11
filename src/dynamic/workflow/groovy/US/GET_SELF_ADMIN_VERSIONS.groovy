package groovy.US

import static java.util.UUID.randomUUID

import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder
import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.configuration.GSSPConfiguration
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil
import net.minidev.json.JSONObject
import net.minidev.json.parser.JSONParser

class GET_SELF_ADMIN_VERSIONS implements Task{

	private static final Logger LOGGER = LoggerFactory.getLogger(GET_SELF_ADMIN_VERSIONS)
	def billMethodMap=[:]
	def static final X_GSSP_TRACE_ID = 'x-gssp-trace-id'

	@Override
	Object execute(WorkflowDomain workFlow) {
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
		def requestParamsMap = workFlow.getRequestParams()
		def config = workFlow.getBeanFromContext(BillingConstants.GSSP_CONFIGURATION, GSSPConfiguration)
		def billNumber
		if (requestParamsMap.get('q')!=null) {
			def requestParam = requestParamsMap.get('q')
			def (key, value) = requestParam.tokenize( '==' )
			if (key != null && key == "billNumber") {
				billNumber = value
			}
		}
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID),]
		def spiHeadersMap = getRequiredHeaders(headersList.tokenize(BillingConstants.COMMA) , requestHeaders)

		def response = getSelfAdminPastVersions(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber,  billNumber, spiHeadersMap)
		if (response == null) {
			throw new GSSPException("20012")
		}
		LOGGER.info("Before for loop....")
		for(def item : response?.items){
			def submissionStatusTypeCode = item.item.submissionStatusTypeCode
			item.item << ['submissionStatusTypeCode':getBillingMethod(submissionStatusTypeCode, config, 'ref_billing_form_data')]
		}
		LOGGER.info("After for loop....")
		workFlow.addResponseBody(new EntityResult([submissionhistory:response], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}

	def getSelfAdminPastVersions(registeredServiceInvoker, spiPrefix, spiMockURI, groupNumber,  billNumber, spiHeadersMap) {

		try {
			def response
			def uri
			if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
				uri= "${spiPrefix}/selfAdminBillProfile"
			} else
			{
				uri = "${spiPrefix}/groups/$groupNumber/billProfiles/versions?q=billNumber==$billNumber"
			}
			def uriBuilder = UriComponentsBuilder.fromPath(uri)
			def serviceUri = uriBuilder.build(false).toString()
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:],spiHeadersMap)
			LOGGER.info("Selfadmin SPI response: "+response)
			if (response != null) {
				LOGGER.info("response body: "+response.getBody())
				return response.getBody()
			}
			return response
			
		} catch (Exception e) {
			LOGGER.error 'Exception in getting data from SPI for selfAdminPastVersions ' + e.toString()
			throw new GSSPException("20012")
		}

	}

	def getBillingMethod(code , config, configurationId){
		code = code.toString()
		def statusMapping = config.get(configurationId, 'US', [locale : 'en_US'])
		billMethodMap = statusMapping?.data
		billMethodMap[code]
	}

	def getRequiredHeaders(List headersList, Map headerMap) {
		LOGGER.info "Configuring spi hearder"
		headerMap<<[(X_GSSP_TRACE_ID):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {
				spiHeaders << [(header): [headerMap[header]]]
			}
		}
		spiHeaders
	}
}
