package groovy.US

import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

import com.metlife.domain.model.EntityResult
import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.Logger
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil

import static java.util.UUID.randomUUID
/**
 * This groovy is used to retrieve broker group details
 * 
 *
 * @author Shikhar Arora
 */
class GET_GROUPS_OF_BROKER implements Task {
	Logger logger = LoggerFactory.getLogger(GET_GROUPS_OF_BROKER)

	@Override
	Object execute(WorkflowDomain workFlow) {
		def registeredServiceInvoker = workFlow.getBeanFromContext(BillingConstants.REGISTERED_SERVICE_INVOKER, RegisteredServiceInvoker)
		def requestPathParamsMap = workFlow.getRequestPathParams()
		def requestHeaders =  workFlow.getRequestHeader()
		def headersList = workFlow.getEnvPropertyFromContext(BillingConstants.GSSP_HEADERS)
		requestHeaders << [
			'x-gssp-tenantid': workFlow.getEnvPropertyFromContext(BillingConstants.SMD_GSSP_TENANT_ID),
			'x-spi-service-id': workFlow.getEnvPropertyFromContext(BillingConstants.SERVICE_ID)]
		def spiHeadersMap = getRequiredHeaders(headersList.tokenize(BillingConstants.COMMA) , requestHeaders)
		def enrollmentType = "enrollment"

		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def brokerId = requestPathParamsMap['brokerId']
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(brokerId)
		validation.validateUser(workFlow, validationList)
		
		def clientDetails = retrieveBrokerGroupsFromSPI(registeredServiceInvoker, spiPrefix, brokerId,enrollmentType,spiHeadersMap)
		if(clientDetails){
			workFlow.addResponseBody(new EntityResult([groups : clientDetails], true))
			workFlow.addResponseStatus(HttpStatus.OK)
		}else{
			throw new GSSPException('10005')
		}
	}

	def retrieveBrokerGroupsFromSPI(registeredServiceInvoker, spiPrefix, brokerId,enrollmentType,spiHeadersMap) {
		def queryMap = "enrollmentType==${enrollmentType}"
		def uri = "${spiPrefix}/brokers/${brokerId}/groups?q=${queryMap}"
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:],spiHeadersMap)
			logger.info("response from IIB ::::: ==>" + response)
			response = response?.getBody()
		} catch (e) {
			logger.error "ERROR WHILE retrieving group detail from SPI....."+e.toString()
			throw new GSSPException('10004')
		}
		response?.items.item
	}


	def getRequiredHeaders(List headersList, Map headerMap) {
		headerMap<<[(BillingConstants.X_GSSP_TRACE_ID):randomUUID().toString()]
		def spiHeaders = [:]
		for (header in headersList) {
			if (headerMap[header]) {
				spiHeaders << [(header): [headerMap[header]]]
			}
		}
		spiHeaders
	}
}