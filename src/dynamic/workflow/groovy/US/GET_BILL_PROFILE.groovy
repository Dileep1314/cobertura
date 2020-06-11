package groovy.US

import com.metlife.domain.model.WorkflowDomain
import com.metlife.gssp.logging.Logger
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder

import com.metlife.domain.model.EntityResult
import com.metlife.gssp.common.controller.RegisteredServiceInvoker
import com.metlife.gssp.exception.GSSPException
import com.metlife.gssp.logging.LoggerFactory
import com.metlife.gssp.taskflow.Task

import groovy.UTILS.BillingConstants
import groovy.UTILS.ValidationUtil

import static java.util.UUID.randomUUID

/**
 * This groovy is used to retrieve the bill
 * profile details containing current bill amount
 *  and outstanding amount
 *
 * Call : SPI
 *
 * @author Vakul
 */

class GET_BILL_PROFILE implements Task {
	private static final Logger logger = LoggerFactory.getLogger(GET_BILL_PROFILE)

	
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
		def spiPrefix = workFlow.getEnvPropertyFromContext(BillingConstants.SPI_PREFIX)
		def spiMockURI = workFlow.getEnvPropertyFromContext(BillingConstants.LIST_OF_SERVERS)
		def tenantId = requestPathParamsMap[BillingConstants.TENANT_ID]
		def groupNumber = requestPathParamsMap[BillingConstants.GROUP_NUMBER]
		
		ValidationUtil validation = new ValidationUtil()
		def validationList = [] as List
		validationList.add(groupNumber)
		validation.validateUser(workFlow, validationList)
		def billProfileResponse = retrieveBillSummaryFromSPI(registeredServiceInvoker, spiPrefix, groupNumber, spiMockURI, spiHeadersMap)
		logger.info "Calling client detail for each clientId.."+billProfileResponse
		workFlow.addResponseBody(new EntityResult([bills:billProfileResponse], true))
		workFlow.addResponseStatus(HttpStatus.OK)
	}


	/*
	 * Used to get bill profile details from SPI
	 */
	def retrieveBillSummaryFromSPI(registeredServiceInvoker, spiPrefix, groupNumber, spiMockURI, spiHeadersMap) {
		def uri

		if(spiMockURI !=null && (spiMockURI.contains ('localhost') || spiMockURI.contains ('gsspspiservice'))){
			uri= "${spiPrefix}/billProfiles?q=accountNumber==$groupNumber&billFromDate==2010-01-01&billToDate==2018-01-01&view==current"
		} else {
			uri = "${spiPrefix}/groups/$groupNumber/billProfiles?q=view==current"
		}
		def uriBuilder = UriComponentsBuilder.fromPath(uri)
		def serviceUri = uriBuilder.build(false).toString()
		def response
		try {
			response = registeredServiceInvoker.getViaSPI(serviceUri, Map.class, [:], spiHeadersMap)
		} catch (Exception e) {
			logger.info "ERROR while retrieving bill profile details from SPI....."+e.toString()
			throw new GSSPException("20014")
		}
		response.getBody().items.item[0]
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

